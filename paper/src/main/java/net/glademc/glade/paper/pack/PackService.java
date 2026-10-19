package net.glademc.glade.paper.pack;

import com.sun.net.httpserver.HttpServer;
import net.glademc.glade.paper.file.GladeFolders;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Liefert das Resourcepack an die Spieler aus, abhängig vom {@code pack}-Block der config.yml.
 *
 * <ul>
 *   <li>{@link PackMode#EXTERNAL}: Die URL aus der Config wird an die Spieler gesendet.
 *       Der SHA-1-Hash wird beim Laden automatisch aus dem Download berechnet.</li>
 *   <li>{@link PackMode#SELF_HOST}: Ein eingebauter Webserver stellt die Datei
 *       {@code plugins/Glade/pack/glade-pack.zip} bereit. Der Server lauscht auf allen
 *       Netzwerk-Schnittstellen auf dem konfigurierten Port, {@code host} wird für die
 *       URL genutzt, die an die Spieler gesendet wird.</li>
 *   <li>{@link PackMode#HERMES}: Beim Generieren wird die Zip auf den Hermes-Server hochgeladen
 *       ({@link #uploadToHermes}). Die zurückgegebene URL wird in {@code pack/hermes.json}
 *       gespeichert und beim Start/Reload von dort gelesen.</li>
 * </ul>
 */
public final class PackService {

    /** Name der Pack-Datei im Self-Host-Modus (liegt in plugins/Glade/pack/). */
    public static final String ZIP_NAME = "glade-pack.zip";

    /** Speichert URL und Hash des zuletzt hochgeladenen Hermes-Packs (liegt in plugins/Glade/pack/). */
    public static final String HERMES_FILE = "hermes.json";

    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("glade:resourcepack".getBytes(StandardCharsets.UTF_8));

    private final JavaPlugin plugin;
    private final GladeFolders folders;
    private final Logger logger;
    private final AtomicLong generation = new AtomicLong();

    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private volatile ResourcePackInfo packInfo;

    public PackService(JavaPlugin plugin, GladeFolders folders) {
        this.plugin = plugin;
        this.folders = folders;
        this.logger = plugin.getLogger();
    }

    /** Wendet neue Einstellungen an: stoppt den alten Zustand und startet den gewählten Modus neu. */
    public synchronized void reload(PackSettings settings) {
        stop();
        long current = generation.incrementAndGet();

        settings.validationError().ifPresent(error ->
                logger.warning("Resource pack is not active: " + error + "."));
        if (settings.validationError().isPresent()) {
            return;
        }

        switch (settings.mode()) {
            case EXTERNAL -> startExternal(settings, current);
            case SELF_HOST -> startSelfHost(settings);
            case HERMES -> startHermes();
        }
    }

    /** Stoppt den Webserver (falls aktiv) und vergisst das aktuelle Pack. */
    public synchronized void stop() {
        generation.incrementAndGet();
        packInfo = null;
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
    }

    /** Sendet das Pack an einen Spieler (tut nichts, solange kein Pack bereit ist). */
    public void send(Player player) {
        ResourcePackInfo info = packInfo;
        if (info == null) {
            return;
        }
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(false)
                .build());
    }

    public boolean isReady() {
        return packInfo != null;
    }

    // ------------------------------------------------------------------
    // external
    // ------------------------------------------------------------------

    private void startExternal(PackSettings settings, long current) {
        URI uri = URI.create(settings.externalUrl());
        ResourcePackInfo.resourcePackInfo()
                .id(PACK_ID)
                .uri(uri)
                .computeHashAndBuild()
                .whenComplete((info, error) -> {
                    if (error != null) {
                        logger.warning("Could not download the resource pack to compute its hash: " + error.getMessage());
                        return;
                    }
                    if (generation.get() != current) {
                        return; // inzwischen neu geladen
                    }
                    packInfo = info;
                    logger.info("Resource pack ready (external): " + uri);
                });
    }

    // ------------------------------------------------------------------
    // hermes
    // ------------------------------------------------------------------

    private void startHermes() {
        HermesState state = HermesState.load(folders.pack().resolve(HERMES_FILE));
        if (state == null) {
            logger.warning("Resource pack (hermes) is not active yet: run /glade generatepack to upload it.");
            return;
        }
        packInfo = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(state.url()), state.sha1());
        logger.info("Resource pack ready (hermes): " + state.url());
    }

    /**
     * Lädt die Zip auf den Hermes-Server hoch und verwendet danach die zurückgegebene URL.
     * Blockiert während des Uploads, deshalb nicht im Server-Thread aufrufen.
     *
     * @throws IOException wenn der Upload fehlschlägt oder Hermes ihn ablehnt
     */
    public HermesState uploadToHermes(Path zip, String key) throws IOException {
        HermesState state = new HermesClient().upload(zip, key);
        state.save(folders.pack().resolve(HERMES_FILE));
        synchronized (this) {
            packInfo = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(state.url()), state.sha1());
        }
        logger.info("Resource pack ready (hermes): " + state.url());
        return state;
    }

    // ------------------------------------------------------------------
    // self_host
    // ------------------------------------------------------------------

    private void startSelfHost(PackSettings settings) {
        Path zip = folders.pack().resolve(ZIP_NAME);
        if (!Files.isRegularFile(zip)) {
            logger.warning("Resource pack is not active: " + zip + " does not exist yet.");
            return;
        }

        String hash;
        try {
            hash = sha1(zip);
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.warning("Could not read " + zip.getFileName() + ": " + e.getMessage());
            return;
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(settings.port()), 0);
            ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "Glade-PackServer");
                thread.setDaemon(true);
                return thread;
            });

            server.createContext("/" + ZIP_NAME, exchange -> {
                try (exchange) {
                    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    byte[] data = Files.readAllBytes(zip);
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(data);
                    }
                } catch (IOException e) {
                    logger.fine("Pack download failed: " + e.getMessage());
                }
            });
            server.setExecutor(executor);
            server.start();

            httpServer = server;
            httpExecutor = executor;

            URI uri = URI.create("http://" + settings.host() + ":" + settings.port() + "/" + ZIP_NAME);
            packInfo = ResourcePackInfo.resourcePackInfo(PACK_ID, uri, hash);
            logger.info("Resource pack ready (self_host): " + uri);
        } catch (IOException e) {
            logger.warning("Could not start the pack web server on port " + settings.port() + ": " + e.getMessage());
        }
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}