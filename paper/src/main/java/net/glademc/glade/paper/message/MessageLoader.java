package net.glademc.glade.paper.message;

import net.glademc.glade.core.util.MessageUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Lädt die Sprachdateien {@code messages/<langid>.yml} in {@link MessageUtils}.
 *
 * <ul>
 *   <li>Mitgelieferte Sprachen werden beim ersten Start in den Plugin-Ordner kopiert.</li>
 *   <li>Zusätzliche {@code .yml}-Dateien in {@code plugins/Glade/messages/} werden automatisch
 *       als weitere Sprachen geladen.</li>
 *   <li>Fehlende Schlüssel in einer (vom Server-Admin bearbeiteten) Datei werden mit den
 *       Werten aus der mitgelieferten Datei im JAR aufgefüllt.</li>
 * </ul>
 */
public final class MessageLoader {

    private static final String DIRECTORY = "messages";
    private static final String EXTENSION = ".yml";
    private static final List<String> BUNDLED_LANGUAGES = List.of("de", "en");

    private final JavaPlugin plugin;
    private final MessageUtils messages;

    public MessageLoader(JavaPlugin plugin, MessageUtils messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /** Lädt alle Sprachen neu. Bestehende Sprachen werden atomar ersetzt. */
    public void reload() {
        File directory = new File(plugin.getDataFolder(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create directory: " + directory.getPath());
        }

        for (String lang : BUNDLED_LANGUAGES) {
            saveBundledIfMissing(lang);
        }

        Set<String> languages = new LinkedHashSet<>(BUNDLED_LANGUAGES);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                languages.add(name.substring(0, name.length() - EXTENSION.length()));
            }
        }

        for (String lang : languages) {
            loadLanguage(lang, new File(directory, lang + EXTENSION));
        }

        // Sprachen entfernen, deren Datei nicht mehr existiert
        for (String loaded : messages.loadedLanguages()) {
            boolean stillExists = languages.stream().anyMatch(l -> l.equalsIgnoreCase(loaded));
            if (!stillExists) {
                messages.unload(loaded);
            }
        }

        plugin.getLogger().info("Loaded languages: " + String.join(", ", messages.loadedLanguages()));
    }

    private void saveBundledIfMissing(String lang) {
        String resource = DIRECTORY + "/" + lang + EXTENSION;
        File target = new File(plugin.getDataFolder(), resource);
        if (!target.exists() && plugin.getResource(resource) != null) {
            plugin.saveResource(resource, false);
        }
    }

    private void loadLanguage(String lang, File file) {
        try {
            Map<String, Object> merged = new LinkedHashMap<>();

            // 1. Standardwerte aus dem JAR (falls mitgeliefert)
            String resource = DIRECTORY + "/" + lang + EXTENSION;
            try (InputStream stream = plugin.getResource(resource)) {
                if (stream != null) {
                    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        merged.putAll(toMap(YamlConfiguration.loadConfiguration(reader)));
                    }
                }
            }

            // 2. Datei im Plugin-Ordner überschreibt die Standardwerte
            if (file.isFile()) {
                deepMerge(merged, toMap(YamlConfiguration.loadConfiguration(file)));
            }

            if (merged.isEmpty()) {
                plugin.getLogger().warning("Language file is empty: " + file.getName());
                return;
            }
            messages.load(lang, merged);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load language file: " + file.getName(), e);
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

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            Object existing = base.get(entry.getKey());
            if (existing instanceof Map<?, ?> && entry.getValue() instanceof Map<?, ?> nested) {
                deepMerge((Map<String, Object>) existing, (Map<String, Object>) nested);
            } else {
                base.put(entry.getKey(), entry.getValue());
            }
        }
    }
}