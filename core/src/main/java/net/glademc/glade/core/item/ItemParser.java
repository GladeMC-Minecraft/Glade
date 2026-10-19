package net.glademc.glade.core.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Liest Item-Definitionen aus der verschachtelten Map einer YAML-Datei.
 *
 * <p>Format (die Schlüssel sind nicht von Groß-/Kleinschreibung abhängig):</p>
 * <pre>
 * ruby:                            # ID des Items
 *   material: PAPER                # Pflicht
 *   display_name: "&lt;red&gt;Rubin"     # optional (Alias: itemname)
 *   lore:                          # optional, Liste oder einzelner Text
 *     - "&lt;gray&gt;Ein Rubin"
 *   max_stack_size: 16             # optional, 1-99
 *   components:                    # optional: food, consumable, use_remainder, use_cooldown, max_damage, ...
 *     food:
 *       nutrition: 6
 *   mechanics:                     # optional: use_permission, cooldown, commands, droppable, ...
 *     droppable: false
 *   pack:                          # optional
 *     generate_model: true         # Glade erzeugt das Modell
 *     parent_model: item/generated # Standard: minecraft:item/generated
 *     texture: item/ruby           # Standard: glade:item/&lt;id&gt; (Alias: textures: [..])
 *     model: glade:custom/ruby     # alternativ: vorhandenes Modell nutzen (ohne generate_model)
 * </pre>
 *
 * <p>Der Namespace kommt vom Ordner ({@code items/<namespace>/...}). Resource Locations ohne
 * Namespace werden ergänzt: Texturen und Modelle bekommen den Namespace des Items,
 * Eltern-Modelle {@code minecraft:}. Die Standardtextur ist {@code <namespace>:item/<id>}.</p>
 */
public final class ItemParser {

    /** Namespace für Dateien, die direkt in {@code items/} liegen (nicht in einem Namespace-Ordner). */
    public static final String DEFAULT_NAMESPACE = "glade";

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    /**
     * Ergebnis des Einlesens einer Datei: gültige Items, Fehlermeldungen (betroffene Items werden
     * übersprungen) und Hinweise (z. B. unbekannte Komponenten, die Items werden trotzdem geladen).
     */
    public record Result(List<ItemDefinition> items, List<String> errors, List<String> warnings) {
    }

    private ItemParser() {
    }

    public static boolean isValidNamespace(String namespace) {
        return NAMESPACE_PATTERN.matcher(namespace).matches();
    }

    /**
     * @param source    Name der Datei (nur für Fehlermeldungen)
     * @param namespace Namespace der Items in dieser Datei
     * @param root      Inhalt der YAML-Datei
     */
    public static Result parse(String source, String namespace, Map<?, ?> root) {
        List<ItemDefinition> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!isValidNamespace(namespace)) {
            errors.add(source + ": '" + namespace + "' is not a valid namespace (allowed: a-z, 0-9, _ . -)");
            return new Result(items, errors, warnings);
        }

        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String where = source + ": '" + id + "'";

            if (!ID_PATTERN.matcher(id).matches()) {
                errors.add(where + " has an invalid id (allowed: a-z, 0-9, _ . -)");
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> section)) {
                errors.add(where + " must be a section");
                continue;
            }

            try {
                items.add(parseItem(namespace, id, lowerKeys(section), where, warnings));
            } catch (IllegalArgumentException e) {
                errors.add(where + ": " + e.getMessage());
            }
        }
        return new Result(items, errors, warnings);
    }

    private static ItemDefinition parseItem(String namespace, String id, Map<String, Object> section,
                                            String where, List<String> warnings) {
        String material = text(section, "material");
        if (material == null) {
            throw new IllegalArgumentException("'material' is missing");
        }
        material = material.trim().toUpperCase(Locale.ROOT);

        String displayName = text(section, "display_name");
        if (displayName == null) {
            displayName = text(section, "itemname");
        }

        List<String> lore = new ArrayList<>();
        Object rawLore = section.get("lore");
        if (rawLore instanceof List<?> list) {
            for (Object line : list) {
                lore.add(String.valueOf(line));
            }
        } else if (rawLore != null) {
            lore.add(String.valueOf(rawLore));
        }

        Integer maxStackSize = null;
        Object rawStack = section.get("max_stack_size");
        if (rawStack != null) {
            maxStackSize = integer(rawStack, "max_stack_size");
            if (maxStackSize < 1 || maxStackSize > 99) {
                throw new IllegalArgumentException("'max_stack_size' must be between 1 and 99");
            }
        }

        String itemModel = null;
        ItemDefinition.GeneratedModel generated = null;

        Object rawPack = section.get("pack");
        if (rawPack != null) {
            if (!(rawPack instanceof Map<?, ?> packSection)) {
                throw new IllegalArgumentException("'pack' must be a section");
            }
            Map<String, Object> pack = lowerKeys(packSection);

            boolean generate = pack.containsKey("generate_model")
                    && Boolean.parseBoolean(String.valueOf(pack.get("generate_model")));
            String model = text(pack, "model");

            if (generate && model != null) {
                throw new IllegalArgumentException("use either 'pack.generate_model' or 'pack.model', not both");
            }

            if (generate) {
                String parent = location(text(pack, "parent_model"), "minecraft", "pack.parent_model");
                if (parent == null) {
                    parent = "minecraft:item/generated";
                }

                String texture = text(pack, "texture");
                if (texture == null && pack.get("textures") instanceof List<?> textures && !textures.isEmpty()) {
                    texture = String.valueOf(textures.get(0));
                }
                texture = location(texture, namespace, "pack.texture");
                if (texture == null) {
                    texture = namespace + ":item/" + id;
                }

                generated = new ItemDefinition.GeneratedModel(parent, texture);
                itemModel = namespace + ":" + id;
            } else if (model != null) {
                itemModel = location(model, namespace, "pack.model");
            }
        }

        ItemComponents components = ComponentParser.parse(section.get("components"), where, warnings);

        // Items mit Haltbarkeit sind in Minecraft nie stapelbar
        if (components.maxDamage() != null) {
            if (maxStackSize != null && maxStackSize > 1) {
                throw new IllegalArgumentException(
                        "'components.max_damage' cannot be combined with a 'max_stack_size' above 1 (damageable items are not stackable)");
            }
            maxStackSize = 1;
        }

        ItemMechanics mechanics = MechanicsParser.parse(section.get("mechanics"), where, warnings);

        return new ItemDefinition(namespace, id, material, displayName, List.copyOf(lore), maxStackSize,
                itemModel, generated, components, mechanics);
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

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static int integer(Object value, String key) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be a number");
        }
    }

    /** Prüft eine Resource Location und ergänzt den Standard-Namespace. {@code null} bleibt {@code null}. */
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