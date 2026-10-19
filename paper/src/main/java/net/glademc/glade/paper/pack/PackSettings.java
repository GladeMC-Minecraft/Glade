package net.glademc.glade.paper.pack;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Der {@code pack}-Block der config.yml, eingelesen und geprüft.
 *
 * <pre>
 * pack:
 *   mode: external
 *   external:
 *     url: "..."
 *   self_host:
 *     host: "127.0.0.1"
 *     port: 3301
 *   hermes:
 *     key: "..."
 * </pre>
 */
public record PackSettings(PackMode mode, String externalUrl, String host, int port, String hermesKey) {

    public static final int DEFAULT_PORT = 3301;

    /** Liest den pack-Block. Ungültige Werte werden mit einer Warnung durch Standardwerte ersetzt. */
    public static PackSettings load(FileConfiguration config, Logger logger) {
        String rawMode = config.getString("pack.mode", "external");
        PackMode mode = PackMode.parse(rawMode);
        if (mode == null) {
            logger.warning("Unknown pack.mode '" + rawMode
                    + "' (use 'external', 'self_host' or 'hermes'), using 'external'.");
            mode = PackMode.EXTERNAL;
        }

        String url = config.getString("pack.external.url", "").trim();
        String host = config.getString("pack.self_host.host", "127.0.0.1").trim();
        int port = parsePort(config.getString("pack.self_host.port"), logger);
        String hermesKey = config.getString("pack.hermes.key", "").trim();

        return new PackSettings(mode, url, host, port, hermesKey);
    }

    private static int parsePort(String raw, Logger logger) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // wird unten gemeldet
        }
        logger.warning("Invalid pack.self_host.port '" + raw + "' (1-65535), using " + DEFAULT_PORT + ".");
        return DEFAULT_PORT;
    }

    /** @return eine Fehlerbeschreibung, wenn die Einstellungen für den gewählten Modus nicht nutzbar sind */
    public Optional<String> validationError() {
        return switch (mode) {
            case EXTERNAL -> {
                if (externalUrl.isBlank()) {
                    yield Optional.of("pack.external.url is empty");
                }
                try {
                    URI uri = URI.create(externalUrl);
                    String scheme = uri.getScheme();
                    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                        yield Optional.of("pack.external.url must start with http:// or https://");
                    }
                    yield Optional.empty();
                } catch (IllegalArgumentException e) {
                    yield Optional.of("pack.external.url is not a valid URL");
                }
            }
            case SELF_HOST -> host.isBlank()
                    ? Optional.of("pack.self_host.host is empty")
                    : Optional.empty();
            case HERMES -> hermesKey.isBlank()
                    ? Optional.of("pack.hermes.key is empty (request a key on the Glade Discord)")
                    : Optional.empty();
        };
    }
}