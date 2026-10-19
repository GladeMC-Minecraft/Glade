package net.glademc.glade.core.glyph;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Merkt sich, welchem Glyph welches Zeichen gehört. Die Zuordnung wird von außen gespeichert
 * ({@link #snapshot()}) und beim nächsten Start wieder übergeben, damit sich Zeichen nach einem
 * Neustart nie ändern. Zeichen gelöschter Glyphs bleiben reserviert, damit sie später nicht
 * versehentlich einem anderen Glyph gehören.
 */
public final class GlyphCharMap {

    private final Map<String, Integer> assigned = new TreeMap<>();
    private final Set<Integer> used = new HashSet<>();

    /** @param stored gespeicherte Zuordnung: Schlüssel ({@code namespace:id}) zu Zeichen */
    public GlyphCharMap(Map<String, Integer> stored) {
        for (Map.Entry<String, Integer> entry : stored.entrySet()) {
            int codeUnit = entry.getValue();
            if (codeUnit >= GlyphParser.FIRST_CHAR && codeUnit <= GlyphParser.LAST_CHAR && used.add(codeUnit)) {
                assigned.put(entry.getKey(), codeUnit);
            }
        }
    }

    /**
     * Reserviert ein fest gewähltes Zeichen für einen Glyph.
     *
     * @throws IllegalArgumentException wenn das Zeichen schon einem anderen Glyph gehört
     */
    public void reserve(String key, int codeUnit) {
        for (Map.Entry<String, Integer> entry : assigned.entrySet()) {
            if (entry.getValue() == codeUnit && !entry.getKey().equals(key)) {
                throw new IllegalArgumentException("the character U+" + hex(codeUnit)
                        + " already belongs to the glyph '" + entry.getKey() + "'");
            }
        }
        assigned.put(key, codeUnit);
        used.add(codeUnit);
    }

    /**
     * Das Zeichen für einen Glyph: das bisherige oder das nächste freie.
     *
     * @throws IllegalArgumentException wenn alle Zeichen vergeben sind
     */
    public int charFor(String key) {
        Integer existing = assigned.get(key);
        if (existing != null) {
            return existing;
        }
        for (int codeUnit = GlyphParser.FIRST_CHAR; codeUnit <= GlyphParser.LAST_CHAR; codeUnit++) {
            if (used.add(codeUnit)) {
                assigned.put(key, codeUnit);
                return codeUnit;
            }
        }
        throw new IllegalArgumentException("no free characters left in the private use area");
    }

    /** Die aktuelle Zuordnung (Schlüssel zu Zeichen) zum Speichern. */
    public Map<String, Integer> snapshot() {
        return new TreeMap<>(assigned);
    }

    public static String hex(int codeUnit) {
        return String.format("%04X", codeUnit);
    }
}