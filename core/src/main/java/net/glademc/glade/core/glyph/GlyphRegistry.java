package net.glademc.glade.core.glyph;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Hält alle geladenen Glyphs. Thread-safe: Beim Neuladen wird alles atomar ersetzt.
 * IDs sind über alle Namespaces hinweg eindeutig, deshalb genügt {@code <glyph:id>}.
 */
public final class GlyphRegistry {

    private record State(Map<String, Glyph> byKey, Map<String, Glyph> byId, Map<String, Glyph> byPlaceholder) {
    }

    private volatile State state = new State(Map.of(), Map.of(), Map.of());

    /** Ersetzt alle Glyphs. Bei doppelten IDs oder Platzhaltern gewinnt der zuerst genannte. */
    public void replaceAll(Collection<Glyph> glyphs) {
        Map<String, Glyph> byKey = new TreeMap<>();
        Map<String, Glyph> byId = new TreeMap<>();
        Map<String, Glyph> byPlaceholder = new LinkedHashMap<>();

        for (Glyph glyph : glyphs) {
            if (byId.containsKey(glyph.id())) {
                continue;
            }
            byKey.put(glyph.key(), glyph);
            byId.put(glyph.id(), glyph);
            for (String placeholder : glyph.definition().placeholders()) {
                byPlaceholder.putIfAbsent(placeholder, glyph);
            }
        }
        state = new State(Map.copyOf(byKey), Map.copyOf(byId), Map.copyOf(byPlaceholder));
    }

    /**
     * Sucht einen Glyph über die ID ({@code heart}) oder über den vollständigen
     * Schlüssel ({@code glade:heart}).
     */
    public Optional<Glyph> resolve(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        State current = state;
        return Optional.ofNullable(normalized.contains(":")
                ? current.byKey().get(normalized)
                : current.byId().get(normalized));
    }

    /** Der Glyph zu einem Chat-Platzhalter (z. B. {@code :heart:}). */
    public Optional<Glyph> byPlaceholder(String placeholder) {
        return Optional.ofNullable(state.byPlaceholder().get(placeholder));
    }

    /** Alle Glyphs, nach Schlüssel sortiert. */
    public List<Glyph> all() {
        return state.byKey().values().stream()
                .sorted((a, b) -> a.key().compareTo(b.key()))
                .toList();
    }

    public int size() {
        return state.byKey().size();
    }
}