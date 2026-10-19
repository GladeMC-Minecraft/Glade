package net.glademc.glade.core.glyph;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Erzeugt die Schrift-Dateien ({@code assets/<namespace>/font/<name>.json}), die jedem Glyph
 * sein Bild zuordnen. Glyphs mit der Standard-Schrift landen in
 * {@code assets/minecraft/font/default.json}.
 *
 * <p>Der {@code PackBuilder} führt diese Datei beim Zusammenbauen mit gleichnamigen Dateien aus
 * eigenen oder fremden Packs zusammen, statt sie zu überschreiben.</p>
 */
public final class GlyphFontGenerator {

    private GlyphFontGenerator() {
    }

    /** @return Pfad in der Zip (z. B. {@code assets/minecraft/font/default.json}) zu Dateiinhalt */
    public static Map<String, byte[]> generate(Collection<Glyph> glyphs) {
        Map<String, List<Glyph>> byFont = new TreeMap<>();
        for (Glyph glyph : glyphs) {
            byFont.computeIfAbsent(glyph.definition().font(), font -> new ArrayList<>()).add(glyph);
        }

        Map<String, byte[]> files = new TreeMap<>();
        for (Map.Entry<String, List<Glyph>> entry : byFont.entrySet()) {
            String font = entry.getKey();
            int colon = font.indexOf(':');
            String path = "assets/" + font.substring(0, colon) + "/font/" + font.substring(colon + 1) + ".json";

            List<Glyph> sorted = entry.getValue().stream()
                    .sorted((a, b) -> a.key().compareTo(b.key()))
                    .toList();

            StringBuilder json = new StringBuilder("{\n  \"providers\": [\n");
            for (int i = 0; i < sorted.size(); i++) {
                Glyph glyph = sorted.get(i);
                GlyphDefinition definition = glyph.definition();
                json.append("    {\n")
                        .append("      \"type\": \"bitmap\",\n")
                        .append("      \"file\": \"").append(definition.texture()).append(".png\",\n")
                        .append("      \"ascent\": ").append(definition.ascent()).append(",\n")
                        .append("      \"height\": ").append(definition.height()).append(",\n")
                        .append("      \"chars\": [\"\\u")
                        .append(GlyphCharMap.hex(glyph.character().charAt(0))).append("\"]\n")
                        .append("    }").append(i < sorted.size() - 1 ? "," : "").append("\n");
            }
            json.append("  ]\n}\n");

            files.put(path, json.toString().getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }
}