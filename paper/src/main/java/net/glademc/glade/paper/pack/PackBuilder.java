package net.glademc.glade.paper.pack;

import net.glademc.glade.paper.file.GladeFolders;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Baut aus dem Ordner {@code plugins/Glade/pack/} das Resourcepack {@code glade-pack.zip}.
 *
 * <p>Aufbau des Ordners (wie bei Nexo):</p>
 * <pre>
 * pack/
 * |-- pack.mcmeta          (optional hier, sonst wird assets/minecraft/pack.mcmeta genutzt)
 * |-- pack.png             (optional hier, sonst wird assets/minecraft/pack.png genutzt)
 * |-- assets/              (eigene Dateien von Glade)
 * |-- external_packs/      (fremde Packs als .zip oder Ordner, werden eingemischt)
 * |-- glade-pack.zip       (Ergebnis)
 * </pre>
 *
 * <p>Regeln:</p>
 * <ul>
 *   <li>{@code pack.mcmeta} und {@code pack.png} landen im Wurzelordner der Zip, wo Minecraft
 *       sie erwartet.</li>
 *   <li>Aus {@code external_packs/} werden nur die {@code assets/}-Ordner übernommen
 *       (alphabetisch nach Name sortiert).</li>
 *   <li>Von Glade erzeugte Dateien (z. B. Item-Modelle) kommen aus dem Konstruktor und werden
 *       zusätzlich eingefügt.</li>
 *   <li>Bei doppelten Dateien gewinnt: eigene Datei aus {@code assets/} vor erzeugter Datei
 *       vor erstem externen Pack vor den weiteren externen Packs. So kannst du ein erzeugtes
 *       Modell jederzeit durch ein eigenes ersetzen.</li>
 *   <li>Die Zip ist reproduzierbar (feste Zeitstempel, sortierte Einträge): gleicher Inhalt
 *       ergibt gleichen Hash, sodass Clients das Pack zwischenspeichern können.</li>
 * </ul>
 */
public final class PackBuilder {

    private static final FileTime FIXED_TIME = FileTime.from(Instant.parse("2000-01-01T00:00:00Z"));

    /** Ergebnis eines erfolgreichen Builds. */
    public record Result(Path zip, int files, int externalPacks, long bytes, long millis) {
    }

    private final GladeFolders folders;
    private final Logger logger;
    private final Map<String, byte[]> generated;

    public PackBuilder(GladeFolders folders, Logger logger) {
        this(folders, logger, Map.of());
    }

    /**
     * @param generated von Glade erzeugte Dateien: Pfad in der Zip (muss mit {@code assets/} beginnen) zu Inhalt
     */
    public PackBuilder(GladeFolders folders, Logger logger, Map<String, byte[]> generated) {
        this.folders = folders;
        this.logger = logger;
        this.generated = generated;
    }

    public Result build() throws IOException {
        long start = System.nanoTime();

        Path packDir = folders.pack();
        Path target = packDir.resolve(PackService.ZIP_NAME);
        Path temp = packDir.resolve(PackService.ZIP_NAME + ".tmp");

        Path mcmeta = Files.isRegularFile(packDir.resolve("pack.mcmeta"))
                ? packDir.resolve("pack.mcmeta")
                : folders.packMcmeta();
        if (!Files.isRegularFile(mcmeta)) {
            throw new IOException("pack.mcmeta not found (expected pack/pack.mcmeta or pack/assets/minecraft/pack.mcmeta)");
        }

        int externalPacks = 0;
        ZipWriter writer = null;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            writer = new ZipWriter(zip);

            // 1. Metadaten im Wurzelordner
            writer.addFile("pack.mcmeta", mcmeta);
            Path icon = Files.isRegularFile(packDir.resolve("pack.png"))
                    ? packDir.resolve("pack.png")
                    : folders.packIcon();
            if (Files.isRegularFile(icon)) {
                writer.addFile("pack.png", icon);
            }

            // 2. Eigene Dateien von Glade (höchste Priorität)
            Path assets = folders.packAssets();
            if (Files.isDirectory(assets)) {
                for (Path file : listFiles(assets)) {
                    String name = "assets/" + relative(assets, file);
                    if (name.equals("assets/minecraft/pack.mcmeta") || name.equals("assets/minecraft/pack.png")) {
                        continue; // liegen schon im Wurzelordner der Zip
                    }
                    writer.addFile(name, file);
                }
            }

            // 3. Von Glade erzeugte Dateien (Item-Modelle, ...)
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(generated).entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("assets/") && writer.claim(name)) {
                    writer.write(name, new ByteArrayInputStream(entry.getValue()));
                }
            }

            // 4. Externe Packs
            Path external = folders.externalPacks();
            if (Files.isDirectory(external)) {
                List<Path> entries = new ArrayList<>();
                try (Stream<Path> stream = Files.list(external)) {
                    stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                            .forEach(entries::add);
                }
                for (Path entry : entries) {
                    String fileName = entry.getFileName().toString();
                    if (fileName.startsWith(".")) {
                        continue;
                    }
                    if (Files.isDirectory(entry)) {
                        mergeDirectory(writer, entry);
                        externalPacks++;
                    } else if (fileName.toLowerCase().endsWith(".zip")) {
                        mergeZip(writer, entry);
                        externalPacks++;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }

        long millis = (System.nanoTime() - start) / 1_000_000;
        return new Result(target, writer.files, externalPacks, Files.size(target), millis);
    }

    private void mergeDirectory(ZipWriter writer, Path directory) throws IOException {
        Path assets = directory.resolve("assets");
        if (!Files.isDirectory(assets)) {
            logger.warning("External pack '" + directory.getFileName() + "' has no assets folder, skipped.");
            return;
        }
        for (Path file : listFiles(assets)) {
            writer.addFile("assets/" + relative(assets, file), file);
        }
    }

    private void mergeZip(ZipWriter writer, Path zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();

            boolean foundAssets = false;
            for (ZipEntry entry : entries) {
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith("assets/") || name.contains("..") || name.contains("//")) {
                    continue;
                }
                foundAssets = true;
                if (writer.claim(name)) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        writer.write(name, in);
                    }
                }
            }
            if (!foundAssets) {
                logger.warning("External pack '" + zipFile.getFileName() + "' has no assets folder, skipped.");
            }
        }
    }

    /** Alle normalen Dateien unterhalb von {@code root}, sortiert (versteckte Dateien werden ignoriert). */
    private static List<Path> listFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .toList();
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }

    /** Schreibt Einträge in die Zip und merkt sich, welche Namen schon vergeben sind. */
    private static final class ZipWriter {

        private final ZipOutputStream zip;
        private final Set<String> written = new HashSet<>();
        private int files;

        private ZipWriter(ZipOutputStream zip) {
            this.zip = zip;
        }

        /** @return {@code true}, wenn der Name noch frei war (und jetzt reserviert ist) */
        boolean claim(String name) {
            return written.add(name);
        }

        void write(String name, InputStream in) throws IOException {
            ZipEntry entry = new ZipEntry(name);
            entry.setLastModifiedTime(FIXED_TIME);
            zip.putNextEntry(entry);
            in.transferTo(zip);
            zip.closeEntry();
            files++;
        }

        void addFile(String name, Path file) throws IOException {
            if (!claim(name)) {
                return;
            }
            try (InputStream in = Files.newInputStream(file)) {
                write(name, in);
            }
        }
    }
}