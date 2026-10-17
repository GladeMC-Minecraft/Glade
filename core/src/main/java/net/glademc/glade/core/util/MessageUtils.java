package net.glademc.glade.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet alle Nachrichten von Glade (MiniMessage) pro Sprache.
 *
 * <p>Sprachdateien liegen in den Plattform-Modulen unter {@code resources/messages/<langid>.yml}
 * und werden dort eingelesen. Diese Klasse bekommt nur die geladene YAML-Struktur als
 * {@link Map} und ist damit unabhängig von Bukkit/Paper.</p>
 *
 * <p>Nachrichten werden über den Schlüssel {@code kategorie.schluessel} angesprochen,
 * z. B. {@code general.no_permission}. Der Schlüssel {@code prefix} steht auf oberster Ebene.
 * Listen in der YAML werden zu einer mehrzeiligen Nachricht (Zeilen mit {@code <newline>}
 * verbunden).</p>
 *
 * <p>Platzhalter:</p>
 * <ul>
 *   <li>{@code %prefix%} wird durch den Prefix der Sprache ersetzt.</li>
 *   <li>{@code %name%} wird über die {@code replacements}-Map ersetzt. Die Werte werden
 *       dabei escaped, damit Spielereingaben kein MiniMessage einschleusen können.</li>
 *   <li>{@code <name>} wird über {@link TagResolver} aufgelöst
 *       (z. B. {@code Placeholder.unparsed("player", name)}).</li>
 * </ul>
 *
 * <p>Fallback-Reihenfolge: exakte Sprache, dann Basissprache (de_de wird zu de), dann die
 * Standardsprache. Fehlt der Schlüssel überall, wird eine rote Hinweis-Nachricht geliefert.
 * Die Klasse ist thread-safe (relevant für Folia). Beim Neuladen wird die Sprache atomar
 * ersetzt.</p>
 */
public final class MessageUtils {

    private static final String PREFIX_KEY = "prefix";
    private static final String PREFIX_PLACEHOLDER = "%prefix%";
    private static final String LINE_SEPARATOR = "<newline>";

    private final MiniMessage miniMessage;
    private volatile String defaultLang;
    private final Map<String, Map<String, String>> languages = new ConcurrentHashMap<>();

    public MessageUtils(String defaultLang) {
        this(defaultLang, MiniMessage.miniMessage());
    }

    public MessageUtils(String defaultLang, MiniMessage miniMessage) {
        this.defaultLang = normalize(Objects.requireNonNull(defaultLang, "defaultLang"));
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
    }

    // ------------------------------------------------------------------
    // Laden / Verwalten
    // ------------------------------------------------------------------

    /**
     * Lädt (oder ersetzt) eine Sprache aus einer verschachtelten Map, wie sie aus einer
     * YAML-Datei entsteht.
     */
    public void load(String langId, Map<?, ?> data) {
        Objects.requireNonNull(data, "data");
        Map<String, String> flat = new HashMap<>();
        flatten("", data, flat);
        languages.put(normalize(langId), Map.copyOf(flat));
    }

    public void unload(String langId) {
        languages.remove(normalize(langId));
    }

    public void clear() {
        languages.clear();
    }

    public boolean hasLanguage(String langId) {
        return languages.containsKey(normalize(langId));
    }

    public Set<String> loadedLanguages() {
        return Set.copyOf(languages.keySet());
    }

    public String defaultLanguage() {
        return defaultLang;
    }

    /** Ändert die Standardsprache (z. B. nach dem Neuladen der config.yml). */
    public void setDefaultLanguage(String langId) {
        this.defaultLang = normalize(Objects.requireNonNull(langId, "langId"));
    }

    // ------------------------------------------------------------------
    // Abfragen
    // ------------------------------------------------------------------

    /** Unveränderter MiniMessage-Text inklusive Fallbacks, ohne Platzhalter-Ersetzung. */
    public Optional<String> raw(String langId, String key) {
        Objects.requireNonNull(key, "key");
        for (String lang : candidates(langId)) {
            Map<String, String> messages = languages.get(lang);
            if (messages != null) {
                String value = messages.get(key);
                if (value != null) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    public boolean has(String langId, String key) {
        return raw(langId, key).isPresent();
    }

    /** Nachricht in der Standardsprache. */
    public Component component(String key, TagResolver... resolvers) {
        return component(defaultLang, key, Map.of(), resolvers);
    }

    /** Nachricht in der Standardsprache mit {@code %name%}-Ersetzungen. */
    public Component component(String key, Map<String, String> replacements, TagResolver... resolvers) {
        return component(defaultLang, key, replacements, resolvers);
    }

    /** Nachricht in einer bestimmten Sprache. */
    public Component component(String langId, String key, TagResolver... resolvers) {
        return component(langId, key, Map.of(), resolvers);
    }

    /** Nachricht in einer bestimmten Sprache mit {@code %name%}-Ersetzungen. */
    public Component component(String langId, String key, Map<String, String> replacements,
                               TagResolver... resolvers) {
        Optional<String> raw = raw(langId, key);
        if (raw.isEmpty()) {
            return Component.text("Missing message: " + key, NamedTextColor.RED);
        }
        return parse(applyPlaceholders(langId, raw.get(), replacements), resolvers);
    }

    /** Eine Nachricht ohne Formatierung (alle MiniMessage-Tags entfernt), z. B. für die Konsole. */
    public String plain(String langId, String key, Map<String, String> replacements) {
        Optional<String> raw = raw(langId, key);
        if (raw.isEmpty()) {
            return "Missing message: " + key;
        }
        return miniMessage.stripTags(applyPlaceholders(langId, raw.get(), replacements));
    }

    /** Parst beliebigen MiniMessage-Text (ohne Sprachdatei) mit optionalen Resolvern. */
    public Component parse(String miniMessageText, TagResolver... resolvers) {
        return miniMessage.deserialize(miniMessageText, resolvers);
    }

    /** Escaped Text, damit er als reiner Text in MiniMessage eingefügt werden kann. */
    public String escape(String text) {
        return miniMessage.escapeTags(text);
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    // ------------------------------------------------------------------
    // Intern
    // ------------------------------------------------------------------

    private String applyPlaceholders(String langId, String text, Map<String, String> replacements) {
        String result = text;

        if (result.contains(PREFIX_PLACEHOLDER)) {
            String prefix = raw(langId, PREFIX_KEY).orElse("");
            result = result.replace(PREFIX_PLACEHOLDER, prefix);
        }

        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("%" + entry.getKey() + "%", miniMessage.escapeTags(value));
            }
        }
        return result;
    }

    private List<String> candidates(String langId) {
        String fallback = defaultLang;
        String lang = langId == null ? fallback : normalize(langId);
        int underscore = lang.indexOf('_');
        if (underscore > 0) {
            String base = lang.substring(0, underscore);
            return base.equals(fallback) || lang.equals(fallback)
                    ? List.of(lang, fallback)
                    : List.of(lang, base, fallback);
        }
        return lang.equals(fallback) ? List.of(lang) : List.of(lang, fallback);
    }

    private static String normalize(String langId) {
        return langId.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static void flatten(String path, Map<?, ?> map, Map<String, String> out) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                flatten(key, nested, out);
            } else if (value instanceof List<?> list) {
                StringBuilder joined = new StringBuilder();
                for (Object line : list) {
                    if (!joined.isEmpty()) {
                        joined.append(LINE_SEPARATOR);
                    }
                    joined.append(line);
                }
                out.put(key, joined.toString());
            } else {
                out.put(key, String.valueOf(value));
            }
        }
    }
}