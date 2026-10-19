package net.glademc.glade.core.glyph;

import net.glademc.glade.core.item.ItemParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest Glyph-Definitionen aus der verschachtelten Map einer YAML-Datei.
 *
 * <pre>
 * heart:                             # ID des Glyphs (a-z, 0-9, _ -), über alle Namespaces eindeutig
 *   texture: glyph/heart             # optional, Standard: &lt;namespace&gt;:glyph/&lt;id&gt; (ohne .png)
 *   height: 8                        # optional, Standard 8
 *   ascent: 8                        # optional, Standard: wie height; höchstens height
 *   font: minecraft:default          # optional
 *   permission: glade.glyph.heart    # optional
 *   placeholders:                    # optional
 *     - ":heart:"
 *   tabcomplete: true                # optional, Standard true
 *   char: "\uE000"                   # optional, sonst vergibt Glade ein Zeichen
 * </pre>
 *
 * <p>Eine Textur ohne Namespace gehört zum Namespace des Glyphs. Die Datei liegt dann unter
 * {@code pack/assets/&lt;namespace&gt;/textures/&lt;pfad&gt;.png} und darf höchstens 256x256 Pixel groß sein.</p>
 */
public final class GlyphParser {

    public static final String DEFAULT_FONT = "minecraft:default";
    /** Privater Unicode-Bereich, aus dem Glade Zeichen vergibt. */
    public static final int FIRST_CHAR = 0xE000;
    public static final int LAST_CHAR = 0xF8FF;

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("[A-Za-z0-9_.*-]+");
    private static final Pattern ESCAPED_CHAR = Pattern.compile("(?i)(?:\\\\u|u\\+)([0-9a-f]{4})");
    private static final Set<String> KNOWN = Set.of(
            "texture", "ascent", "height", "font", "permission", "placeholders", "tabcomplete", "char");

    /** Ergebnis des Einlesens einer Datei: gültige Glyphs, Fehler (Glyph wird übersprungen) und Hinweise. */
    public record Result(List<GlyphDefinition> glyphs, List<String> errors, List<String> warnings) {
    }

    private GlyphParser() {
    }

    public static Result parse(String source, String namespace, Map<?, ?> root) {
        List<GlyphDefinition> glyphs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!ItemParser.isValidNamespace(namespace)) {
            errors.add(source + ": '" + namespace + "' is not a valid namespace (allowed: a-z, 0-9, _ . -)");
            return new Result(glyphs, errors, warnings);
        }

        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String where = source + ": '" + id + "'";

            if (!ID_PATTERN.matcher(id).matches()) {
                errors.add(where + " has an invalid id (allowed: a-z, 0-9, _ -)");
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> section)) {
                errors.add(where + " must be a section");
                continue;
            }

            try {
                glyphs.add(parseGlyph(namespace, id, lowerKeys(section), where, warnings));
            } catch (IllegalArgumentException e) {
                errors.add(where + ": " + e.getMessage());
            }
        }
        return new Result(glyphs, errors, warnings);
    }

    private static GlyphDefinition parseGlyph(String namespace, String id, Map<String, Object> section,
                                              String where, List<String> warnings) {
        for (String key : section.keySet()) {
            if (!KNOWN.contains(key)) {
                warnings.add(where + ": unknown option '" + key + "' is ignored (supported: "
                        + String.join(", ", KNOWN.stream().sorted().toList()) + ")");
            }
        }

        String texture = text(section.get("texture"));
        if (texture != null && texture.toLowerCase(Locale.ROOT).endsWith(".png")) {
            texture = texture.substring(0, texture.length() - 4);
            warnings.add(where + ": 'texture' must not end with .png, the ending was removed");
        }
        texture = location(texture, namespace, "texture");
        if (texture == null) {
            texture = namespace + ":glyph/" + id;
        }

        int height = section.containsKey("height") ? (int) number(section.get("height"), "height") : 8;
        if (height < 1) {
            throw new IllegalArgumentException("'height' must be at least 1");
        }
        int ascent = section.containsKey("ascent") ? (int) number(section.get("ascent"), "ascent") : height;
        if (ascent > height) {
            throw new IllegalArgumentException("'ascent' (" + ascent + ") must not be greater than 'height' (" + height + ")");
        }

        String font = location(text(section.get("font")), "minecraft", "font");
        if (font == null) {
            font = DEFAULT_FONT;
        }

        String permission = text(section.get("permission"));
        if (permission != null) {
            permission = permission.trim();
            if (!PERMISSION_PATTERN.matcher(permission).matches()) {
                throw new IllegalArgumentException("'permission' is not a valid permission: " + permission);
            }
        }

        List<String> placeholders = new ArrayList<>();
        Object rawPlaceholders = section.get("placeholders");
        List<?> list = rawPlaceholders instanceof List<?> l ? l
                : rawPlaceholders == null ? List.of() : List.of(rawPlaceholders);
        Set<String> seen = new HashSet<>();
        for (Object value : list) {
            String placeholder = String.valueOf(value).trim();
            if (placeholder.length() < 2 || placeholder.length() > 32
                    || placeholder.matches(".*[\\s<>].*")) {
                throw new IllegalArgumentException("'placeholders' entry '" + placeholder
                        + "' must be 2-32 characters without spaces, < or >");
            }
            if (seen.add(placeholder)) {
                placeholders.add(placeholder);
            }
        }

        boolean tabComplete = section.get("tabcomplete") == null
                || Boolean.parseBoolean(String.valueOf(section.get("tabcomplete")).trim());

        Integer explicitChar = parseChar(text(section.get("char")));

        return new GlyphDefinition(namespace, id, texture, ascent, height, font, permission,
                List.copyOf(placeholders), tabComplete, explicitChar);
    }

    /** Ein Zeichen als Buchstabe, als {@code \\uE000} oder als {@code U+E000}. */
    private static Integer parseChar(String value) {
        if (value == null) {
            return null;
        }
        int codeUnit;
        Matcher escaped = ESCAPED_CHAR.matcher(value.trim());
        if (escaped.matches()) {
            codeUnit = Integer.parseInt(escaped.group(1), 16);
        } else if (value.length() == 1) {
            codeUnit = value.charAt(0);
        } else {
            throw new IllegalArgumentException("'char' must be a single character, \\uE000 or U+E000");
        }
        if (codeUnit < FIRST_CHAR || codeUnit > LAST_CHAR) {
            throw new IllegalArgumentException("'char' must be in the private use area U+E000 to U+F8FF");
        }
        return codeUnit;
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    private static Map<String, Object> lowerKeys(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        return result;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static double number(Object value, String key) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be a number");
        }
    }

    private static String location(String value, String defaultNamespace, String key) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!LOCATION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("'" + key + "' is not a valid resource location: " + value);
        }
        return normalized.contains(":") ? normalized : defaultNamespace + ":" + normalized;
    }
}