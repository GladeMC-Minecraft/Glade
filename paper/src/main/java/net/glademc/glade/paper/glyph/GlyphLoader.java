package net.glademc.glade.paper.glyph;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.glademc.glade.core.glyph.Glyph;
import net.glademc.glade.core.glyph.GlyphCharMap;
import net.glademc.glade.core.glyph.GlyphDefinition;
import net.glademc.glade.core.glyph.GlyphParser;
import net.glademc.glade.core.glyph.GlyphRegistry;
import net.glademc.glade.core.item.ItemParser;
import net.glademc.glade.paper.file.GladeFolders;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Liest alle {@code .yml}-Dateien aus {@code plugins/Glade/glyphs/} und füllt die {@link GlyphRegistry}.
 *
 * <p>Aufbau wie bei den Items: Der Ordner direkt unter {@code glyphs/} ist der Namespace, Dateien
 * direkt in {@code glyphs/} gehören zum Namespace {@code glade}. Glyph-IDs müssen über alle
 * Namespaces hinweg eindeutig sein (so genügt {@code <glyph:id>}).</p>
 *
 * <p>Welches Zeichen ein Glyph bekommt, steht in {@code data/glyph_chars.json}. Die Datei sorgt
 * dafür, dass sich Zeichen nach einem Neustart oder nach dem Löschen anderer Glyphs nie ändern.</p>
 */
public final class GlyphLoader {

    /** Größere Bilder passen nicht in den Schrift-Atlas von Minecraft. */
    private static final int MAX_TEXTURE_SIZE = 256;

    /** Ergebnis eines Ladevorgangs. */
    public record Result(int loaded, int errors) {
    }

    private final Logger logger;
    private final GladeFolders folders;
    private final GlyphRegistry registry;

    public GlyphLoader(JavaPlugin plugin, GladeFolders folders, GlyphRegistry registry) {
        this.logger = plugin.getLogger();
        this.folders = folders;
        this.registry = registry;
    }

    public Result reload() {
        Path directory = folders.glyphs();
        int errors = 0;

        // 1. Alle Definitionen einlesen
        List<GlyphDefinition> definitions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path file : listYamlFiles(directory)) {
            Path relative = directory.relativize(file);
            String name = relative.toString().replace('\\', '/');

            String namespace = relative.getNameCount() > 1
                    ? relative.getName(0).toString()
                    : ItemParser.DEFAULT_NAMESPACE;
            if (!ItemParser.isValidNamespace(namespace)) {
                logger.warning("glyphs/" + name + ": the folder name '" + namespace
                        + "' is not a valid namespace (allowed: a-z, 0-9, _ . -). Rename the folder.");
                errors++;
                continue;
            }

            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file.toFile());
            } catch (IOException | InvalidConfigurationException e) {
                logger.warning("Could not read glyphs/" + name + ": " + e.getMessage());
                errors++;
                continue;
            }

            GlyphParser.Result parsed = GlyphParser.parse("glyphs/" + name, namespace, toMap(yaml));
            for (String error : parsed.errors()) {
                logger.warning(error);
                errors++;
            }
            for (String warning : parsed.warnings()) {
                logger.warning(warning);
            }

            for (GlyphDefinition definition : parsed.glyphs()) {
                if (!ids.add(definition.id())) {
                    logger.warning("glyphs/" + name + ": the glyph id '" + definition.id()
                            + "' is used more than once (ids must be unique across all namespaces), "
                            + "the first definition is used.");
                    errors++;
                    continue;
                }
                definitions.add(definition);
            }
        }

        // 2. Zeichen zuweisen: erst die fest gewählten, dann die übrigen
        Map<String, Integer> stored = readChars();
        GlyphCharMap chars = new GlyphCharMap(stored);
        Set<String> failed = new HashSet<>();
        for (GlyphDefinition definition : definitions) {
            if (definition.explicitChar() == null) {
                continue;
            }
            try {
                chars.reserve(definition.key(), definition.explicitChar());
            } catch (IllegalArgumentException e) {
                logger.warning("'" + definition.key() + "': " + e.getMessage());
                errors++;
                failed.add(definition.key());
            }
        }

        List<Glyph> glyphs = new ArrayList<>();
        for (GlyphDefinition definition : definitions) {
            if (failed.contains(definition.key())) {
                continue;
            }
            try {
                int codeUnit = chars.charFor(definition.key());
                glyphs.add(new Glyph(definition, String.valueOf((char) codeUnit)));
                checkTexture(definition);
            } catch (IllegalArgumentException e) {
                logger.warning("'" + definition.key() + "': " + e.getMessage());
                errors++;
            }
        }

        registry.replaceAll(glyphs);

        Map<String, Integer> current = chars.snapshot();
        if (!current.equals(stored)) {
            writeChars(current);
        }

        logger.info("Loaded " + glyphs.size() + " glyph(s)" + (errors > 0 ? " with " + errors + " problem(s)" : "") + ".");
        return new Result(glyphs.size(), errors);
    }

    // ------------------------------------------------------------------
    // Texturen prüfen
    // ------------------------------------------------------------------

    /** Meldet eine fehlende oder zu große Textur (nur als Hinweis, denn sie kann auch aus einem externen Pack kommen). */
    private void checkTexture(GlyphDefinition definition) {
        String texture = definition.texture();
        int colon = texture.indexOf(':');
        Path png = folders.packAssets()
                .resolve(texture.substring(0, colon))
                .resolve("textures")
                .resolve(texture.substring(colon + 1) + ".png");

        if (!Files.isRegularFile(png)) {
            logger.warning("'" + definition.key() + "': the texture was not found at "
                    + folders.pack().relativize(png).toString().replace('\\', '/')
                    + " (ignore this if it comes from an external pack)");
            return;
        }
        try {
            BufferedImage image = ImageIO.read(png.toFile());
            if (image != null && (image.getWidth() > MAX_TEXTURE_SIZE || image.getHeight() > MAX_TEXTURE_SIZE)) {
                logger.warning("'" + definition.key() + "': the texture is " + image.getWidth() + "x" + image.getHeight()
                        + " pixels, but glyph textures must not be larger than " + MAX_TEXTURE_SIZE + "x" + MAX_TEXTURE_SIZE);
            }
        } catch (IOException e) {
            logger.warning("'" + definition.key() + "': could not read the texture: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Gespeicherte Zeichen
    // ------------------------------------------------------------------

    private Map<String, Integer> readChars() {
        Path file = folders.glyphChars();
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Integer> result = new TreeMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                result.put(entry.getKey(), Integer.parseInt(entry.getValue().getAsString(), 16));
            }
            return result;
        } catch (IOException | RuntimeException e) {
            // Nicht überschreiben: sonst gingen alle Zuordnungen verloren
            Path broken = file.resolveSibling(file.getFileName() + ".broken");
            logger.severe("data/glyph_chars.json could not be read (" + e.getMessage()
                    + "). It was renamed to " + broken.getFileName() + ", new characters are assigned.");
            try {
                Files.move(file, broken, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // dann bleibt die Datei liegen und wird überschrieben
            }
            return Map.of();
        }
    }

    private void writeChars(Map<String, Integer> chars) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(chars).entrySet()) {
            json.addProperty(entry.getKey(), GlyphCharMap.hex(entry.getValue()));
        }
        try {
            Files.writeString(folders.glyphChars(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(json) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("Could not save data/glyph_chars.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Dateien
    // ------------------------------------------------------------------

    private List<Path> listYamlFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return !fileName.startsWith(".") && (fileName.endsWith(".yml") || fileName.endsWith(".yaml"));
                    })
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .forEach(files::add);
            return files;
        } catch (IOException e) {
            logger.warning("Could not list the glyphs folder: " + e.getMessage());
            return List.of();
        }
    }

    private static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                map.put(key, toMap(nested));
            } else if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }
}