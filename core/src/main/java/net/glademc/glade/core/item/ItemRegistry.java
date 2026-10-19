package net.glademc.glade.core.item;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Hält alle geladenen Item-Definitionen, nach vollständigem Schlüssel ({@code namespace:id}).
 * Thread-safe: Beim Neuladen wird die komplette Map atomar ersetzt, laufende Lesezugriffe
 * sehen immer einen konsistenten Stand.
 */
public final class ItemRegistry {

    private volatile Map<String, ItemDefinition> items = Map.of();

    /** Ersetzt alle Definitionen. Bei doppelten Schlüsseln gewinnt die letzte. */
    public void replaceAll(Collection<ItemDefinition> definitions) {
        Map<String, ItemDefinition> next = new TreeMap<>();
        for (ItemDefinition definition : definitions) {
            next.put(definition.key(), definition);
        }
        items = Map.copyOf(next);
    }

    /** @param key vollständiger Schlüssel, z. B. {@code glade:ruby} */
    public Optional<ItemDefinition> get(String key) {
        return Optional.ofNullable(items.get(key));
    }

    /**
     * Sucht ein Item über den vollständigen Schlüssel oder, wenn kein Namespace angegeben ist,
     * über die ID. Die ID allein funktioniert nur, wenn sie in genau einem Namespace vorkommt.
     */
    public Optional<ItemDefinition> resolve(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            return get(normalized);
        }
        List<ItemDefinition> matches = items.values().stream()
                .filter(definition -> definition.id().equals(normalized))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    public boolean contains(String key) {
        return items.containsKey(key);
    }

    /** Alle Schlüssel ({@code namespace:id}), alphabetisch sortiert. */
    public List<String> ids() {
        return items.keySet().stream().sorted().toList();
    }

    /** Alle Definitionen, nach Schlüssel sortiert. */
    public List<ItemDefinition> all() {
        return items.values().stream()
                .sorted((a, b) -> a.key().compareTo(b.key()))
                .toList();
    }

    public int size() {
        return items.size();
    }
}