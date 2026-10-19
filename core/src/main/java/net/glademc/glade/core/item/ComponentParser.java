package net.glademc.glade.core.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Liest den Abschnitt {@code components} einer Item-Definition.
 *
 * <pre>
 * components:
 *   food:
 *     nutrition: 6                  # Pflicht
 *     saturation: 8.0               # Standard 0
 *     can_always_eat: true          # Standard false
 *   consumable:
 *     consume_seconds: 1.6          # Standard 1.6
 *     animation: eat                # eat, drink, block, bow, ... (wird beim Laden geprüft)
 *     sound: entity.generic.eat     # Standard: Vanilla-Sound
 *     particles: true               # Standard true
 *     effects:                      # Statuseffekte beim Verbrauch
 *       - effect: regeneration      # Pflicht
 *         duration: 10              # Pflicht, in Sekunden (oder "infinite")
 *         amplifier: 1              # Standard 0 (= Stufe I)
 *         probability: 1.0          # Standard 1.0
 *         ambient: false            # Standard false
 *         particles: true           # Standard true
 *         icon: true                # Standard true
 *     clear_effects: false          # entfernt alle Statuseffekte
 *     teleport_diameter: 0          # größer als 0: zufälliger Teleport
 *     play_sound: entity.player.burp   # zusätzlicher Sound beim Verbrauch
 *   use_remainder: GLASS_BOTTLE     # Material oder namespace:id eines Glade-Items
 *   use_cooldown:
 *     seconds: 5                    # Pflicht
 *     group: glade:heal             # optional
 *   max_damage: 500                 # Haltbarkeit (ganze Zahl über 0), das Item ist dann nicht stapelbar
 *   unbreakable: true               # verliert nie Haltbarkeit
 *   enchantments:                   # Verzauberungen (Name: Stufe 1-255)
 *     sharpness: 5
 *     minecraft:unbreaking: 3
 *   enchantment_glint_override: true   # true = glitzert immer, false = nie (Alias: glint)
 *   rarity: epic                    # common, uncommon, rare, epic
 *   attribute_modifiers:            # ersetzt die Standard-Attribute des Materials!
 *     - attribute: attack_damage    # Pflicht (z. B. attack_damage, attack_speed, armor, max_health)
 *       amount: 6                   # Pflicht
 *       operation: add_value        # add_value (Standard), add_multiplied_base, add_multiplied_total
 *       slot: mainhand              # any (Standard), mainhand, offhand, hand, head, chest, legs, feet, armor, body
 *       id: minecraft:base_attack_damage   # optional
 *   weapon:
 *     item_damage_per_attack: 1     # Standard 1
 *     disable_blocking_for_seconds: 0   # Standard 0
 *   tool:
 *     default_mining_speed: 1.0     # Standard 1.0
 *     damage_per_block: 1           # Standard 1
 *     rules:
 *       - blocks: "#minecraft:mineable/pickaxe"   # ein Tag (mit #) oder Block bzw. Liste von Blöcken
 *         speed: 8.0                # optional
 *         correct_for_drops: true   # optional
 * </pre>
 *
 * <p>Ist {@code food} ohne {@code consumable} angegeben, wird das Item beim Laden wie normales
 * Essen behandelt (die Standard-Werte von {@code consumable} gelten dann).</p>
 */
final class ComponentParser {

    private static final Pattern LOCATION_PATTERN = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Set<String> KNOWN = Set.of("food", "consumable", "use_remainder", "use_cooldown",
            "max_damage", "unbreakable", "enchantments", "enchantment_glint_override", "glint", "rarity",
            "attribute_modifiers", "weapon", "tool");
    private static final Set<String> OPERATIONS = Set.of("ADD_VALUE", "ADD_MULTIPLIED_BASE", "ADD_MULTIPLIED_TOTAL");
    private static final Pattern SLOT_PATTERN = Pattern.compile("[a-z_]+");
    private static final Set<String> RARITIES = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC");

    private ComponentParser() {
    }

    /**
     * @param raw      Inhalt von {@code components} oder {@code null}
     * @param where    Beschreibung der Stelle für Meldungen
     * @param warnings hier landen nicht kritische Hinweise (z. B. unbekannte Komponenten)
     * @throws IllegalArgumentException bei ungültigen Werten (Meldung ist lesbar)
     */
    static ItemComponents parse(Object raw, String where, List<String> warnings) {
        if (raw == null) {
            return ItemComponents.NONE;
        }
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("'components' must be a section");
        }
        Map<String, Object> section = lowerKeys(rawMap);

        for (String key : section.keySet()) {
            if (!KNOWN.contains(key)) {
                warnings.add(where + ": unknown component '" + key + "' is ignored (supported: "
                        + String.join(", ", KNOWN.stream().sorted().toList()) + ")");
            }
        }

        Object glint = section.containsKey("enchantment_glint_override")
                ? section.get("enchantment_glint_override")
                : section.get("glint");

        return new ItemComponents(
                parseFood(section.get("food")),
                parseConsumable(section.get("consumable")),
                parseRemainder(section.get("use_remainder")),
                parseCooldown(section.get("use_cooldown")),
                parseMaxDamage(section.get("max_damage")),
                bool(section.get("unbreakable"), false),
                parseEnchantments(section.get("enchantments")),
                glint == null ? null : Boolean.parseBoolean(String.valueOf(glint).trim()),
                parseRarity(section.get("rarity")),
                parseAttributeModifiers(section.get("attribute_modifiers")),
                parseWeapon(section.get("weapon")),
                parseTool(section.get("tool")));
    }

    // ------------------------------------------------------------------
    // food
    // ------------------------------------------------------------------

    private static ItemComponents.Food parseFood(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> food = section(raw, "components.food");

        if (!food.containsKey("nutrition")) {
            throw new IllegalArgumentException("'components.food.nutrition' is missing");
        }
        int nutrition = (int) number(food.get("nutrition"), "components.food.nutrition");
        if (nutrition < 0) {
            throw new IllegalArgumentException("'components.food.nutrition' must not be negative");
        }

        float saturation = food.containsKey("saturation")
                ? (float) number(food.get("saturation"), "components.food.saturation") : 0f;
        if (saturation < 0) {
            throw new IllegalArgumentException("'components.food.saturation' must not be negative");
        }

        boolean canAlwaysEat = bool(food.get("can_always_eat"), false);
        return new ItemComponents.Food(nutrition, saturation, canAlwaysEat);
    }

    // ------------------------------------------------------------------
    // consumable
    // ------------------------------------------------------------------

    private static ItemComponents.Consumable parseConsumable(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> consumable = section(raw, "components.consumable");

        float seconds = consumable.containsKey("consume_seconds")
                ? (float) number(consumable.get("consume_seconds"), "components.consumable.consume_seconds") : 1.6f;
        if (seconds < 0) {
            throw new IllegalArgumentException("'components.consumable.consume_seconds' must not be negative");
        }

        String animation = text(consumable.get("animation"));
        if (animation != null) {
            animation = animation.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        }

        String sound = location(text(consumable.get("sound")), "components.consumable.sound");
        boolean particles = bool(consumable.get("particles"), true);

        List<ItemComponents.Effect> effects = new ArrayList<>();
        Object rawEffects = consumable.get("effects");
        if (rawEffects != null) {
            if (!(rawEffects instanceof List<?> list)) {
                throw new IllegalArgumentException("'components.consumable.effects' must be a list");
            }
            for (int i = 0; i < list.size(); i++) {
                effects.add(parseEffect(list.get(i), "components.consumable.effects[" + (i + 1) + "]"));
            }
        }

        boolean clearEffects = bool(consumable.get("clear_effects"), false);

        float teleportDiameter = consumable.containsKey("teleport_diameter")
                ? (float) number(consumable.get("teleport_diameter"), "components.consumable.teleport_diameter") : 0f;
        if (teleportDiameter < 0) {
            throw new IllegalArgumentException("'components.consumable.teleport_diameter' must not be negative");
        }

        String playSound = location(text(consumable.get("play_sound")), "components.consumable.play_sound");

        return new ItemComponents.Consumable(seconds, animation, sound, particles,
                List.copyOf(effects), clearEffects, teleportDiameter, playSound);
    }

    private static ItemComponents.Effect parseEffect(Object raw, String path) {
        Map<String, Object> effect = section(raw, path);

        String type = location(text(effect.get("effect")), path + ".effect");
        if (type == null) {
            throw new IllegalArgumentException("'" + path + ".effect' is missing");
        }

        if (!effect.containsKey("duration")) {
            throw new IllegalArgumentException("'" + path + ".duration' is missing (seconds or 'infinite')");
        }
        int durationTicks = duration(effect.get("duration"), path + ".duration");

        int amplifier = effect.containsKey("amplifier")
                ? (int) number(effect.get("amplifier"), path + ".amplifier") : 0;
        if (amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("'" + path + ".amplifier' must be between 0 and 255");
        }

        float probability = effect.containsKey("probability")
                ? (float) number(effect.get("probability"), path + ".probability") : 1f;
        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("'" + path + ".probability' must be between 0.0 and 1.0");
        }

        return new ItemComponents.Effect(
                type,
                durationTicks,
                amplifier,
                probability,
                bool(effect.get("ambient"), false),
                bool(effect.get("particles"), true),
                bool(effect.get("icon"), true));
    }

    // ------------------------------------------------------------------
    // use_remainder / use_cooldown
    // ------------------------------------------------------------------

    private static String parseRemainder(Object raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.contains(":")) {
            return location(value, "components.use_remainder");
        }
        return value;
    }

    private static ItemComponents.UseCooldown parseCooldown(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> cooldown = section(raw, "components.use_cooldown");

        if (!cooldown.containsKey("seconds")) {
            throw new IllegalArgumentException("'components.use_cooldown.seconds' is missing");
        }
        float seconds = (float) number(cooldown.get("seconds"), "components.use_cooldown.seconds");
        if (seconds <= 0) {
            throw new IllegalArgumentException("'components.use_cooldown.seconds' must be greater than 0");
        }
        String group = location(text(cooldown.get("group")), "components.use_cooldown.group");
        return new ItemComponents.UseCooldown(seconds, group);
    }

    // ------------------------------------------------------------------
    // Haltbarkeit, Verzauberungen, Seltenheit
    // ------------------------------------------------------------------

    private static Integer parseMaxDamage(Object raw) {
        if (raw == null) {
            return null;
        }
        int maxDamage = (int) number(raw, "components.max_damage");
        if (maxDamage < 1) {
            throw new IllegalArgumentException("'components.max_damage' must be at least 1");
        }
        return maxDamage;
    }

    private static Map<String, Integer> parseEnchantments(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("'components.enchantments' must be a section (enchantment: level)");
        }

        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String key = location(name, "components.enchantments");
            int level = (int) number(entry.getValue(), "components.enchantments." + name);
            if (level < 1 || level > 255) {
                throw new IllegalArgumentException("'components.enchantments." + name + "' must be between 1 and 255");
            }
            enchantments.put(key, level);
        }
        return java.util.Collections.unmodifiableMap(enchantments);
    }

    private static String parseRarity(Object raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        String rarity = value.trim().toUpperCase(Locale.ROOT);
        if (!RARITIES.contains(rarity)) {
            throw new IllegalArgumentException("'components.rarity' must be one of: common, uncommon, rare, epic");
        }
        return rarity;
    }

    // ------------------------------------------------------------------
    // Attribute, Waffe, Werkzeug
    // ------------------------------------------------------------------

    private static List<ItemComponents.AttributeModifier> parseAttributeModifiers(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("'components.attribute_modifiers' must be a list");
        }

        List<ItemComponents.AttributeModifier> modifiers = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String path = "components.attribute_modifiers[" + (i + 1) + "]";
            Map<String, Object> entry = section(list.get(i), path);

            String attribute = attribute(text(entry.get("attribute")), path + ".attribute");
            if (attribute == null) {
                throw new IllegalArgumentException("'" + path + ".attribute' is missing");
            }
            if (!entry.containsKey("amount")) {
                throw new IllegalArgumentException("'" + path + ".amount' is missing");
            }
            double amount = number(entry.get("amount"), path + ".amount");
            if (Double.isNaN(amount) || Double.isInfinite(amount)) {
                throw new IllegalArgumentException("'" + path + ".amount' must be a finite number");
            }

            String operation = operation(text(entry.get("operation")), path + ".operation");

            String slot = text(entry.get("slot"));
            slot = slot == null ? "any" : slot.trim().toLowerCase(Locale.ROOT);
            if (!SLOT_PATTERN.matcher(slot).matches()) {
                throw new IllegalArgumentException("'" + path + ".slot' is not a valid slot: " + slot);
            }

            String id = location(text(entry.get("id")), path + ".id");
            if (id == null) {
                // Mit diesen IDs zeigt Minecraft den Tooltip wie bei Vanilla-Waffen an (grün, inklusive Grundwert)
                boolean handSlot = slot.equals("mainhand") || slot.equals("hand") || slot.equals("any");
                if (handSlot && operation.equals("ADD_VALUE")) {
                    if (attribute.equals("minecraft:attack_damage")) {
                        id = "minecraft:base_attack_damage";
                    } else if (attribute.equals("minecraft:attack_speed")) {
                        id = "minecraft:base_attack_speed";
                    }
                }
            }

            modifiers.add(new ItemComponents.AttributeModifier(attribute, amount, operation, slot, id));
        }
        return List.copyOf(modifiers);
    }

    private static ItemComponents.Weapon parseWeapon(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> weapon = section(raw, "components.weapon");

        int damage = weapon.containsKey("item_damage_per_attack")
                ? (int) number(weapon.get("item_damage_per_attack"), "components.weapon.item_damage_per_attack") : 1;
        if (damage < 0) {
            throw new IllegalArgumentException("'components.weapon.item_damage_per_attack' must not be negative");
        }

        float disableBlocking = weapon.containsKey("disable_blocking_for_seconds")
                ? (float) number(weapon.get("disable_blocking_for_seconds"), "components.weapon.disable_blocking_for_seconds") : 0f;
        if (disableBlocking < 0) {
            throw new IllegalArgumentException("'components.weapon.disable_blocking_for_seconds' must not be negative");
        }
        return new ItemComponents.Weapon(damage, disableBlocking);
    }

    private static ItemComponents.Tool parseTool(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> tool = section(raw, "components.tool");

        float speed = tool.containsKey("default_mining_speed")
                ? (float) number(tool.get("default_mining_speed"), "components.tool.default_mining_speed") : 1f;
        if (speed < 0) {
            throw new IllegalArgumentException("'components.tool.default_mining_speed' must not be negative");
        }

        int damagePerBlock = tool.containsKey("damage_per_block")
                ? (int) number(tool.get("damage_per_block"), "components.tool.damage_per_block") : 1;
        if (damagePerBlock < 0) {
            throw new IllegalArgumentException("'components.tool.damage_per_block' must not be negative");
        }

        List<ItemComponents.ToolRule> rules = new ArrayList<>();
        Object rawRules = tool.get("rules");
        if (rawRules != null) {
            if (!(rawRules instanceof List<?> list)) {
                throw new IllegalArgumentException("'components.tool.rules' must be a list");
            }
            for (int i = 0; i < list.size(); i++) {
                rules.add(parseToolRule(list.get(i), "components.tool.rules[" + (i + 1) + "]"));
            }
        }
        return new ItemComponents.Tool(speed, damagePerBlock, List.copyOf(rules));
    }

    private static ItemComponents.ToolRule parseToolRule(Object raw, String path) {
        Map<String, Object> rule = section(raw, path);

        Object rawBlocks = rule.get("blocks");
        if (rawBlocks == null) {
            throw new IllegalArgumentException("'" + path + ".blocks' is missing");
        }
        List<String> entries = new ArrayList<>();
        if (rawBlocks instanceof List<?> list) {
            for (Object entry : list) {
                entries.add(String.valueOf(entry).trim());
            }
        } else {
            entries.add(String.valueOf(rawBlocks).trim());
        }
        if (entries.isEmpty() || entries.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("'" + path + ".blocks' must not be empty");
        }

        String tag = null;
        List<String> blocks = new ArrayList<>();
        boolean hasTag = entries.stream().anyMatch(entry -> entry.startsWith("#"));
        if (hasTag) {
            if (entries.size() != 1) {
                throw new IllegalArgumentException("'" + path + ".blocks': a tag cannot be combined with other entries, use one rule per tag");
            }
            tag = location(entries.get(0).substring(1), path + ".blocks");
        } else {
            for (String entry : entries) {
                blocks.add(location(entry, path + ".blocks"));
            }
        }

        Float speed = null;
        if (rule.containsKey("speed")) {
            speed = (float) number(rule.get("speed"), path + ".speed");
            if (speed < 0) {
                throw new IllegalArgumentException("'" + path + ".speed' must not be negative");
            }
        }
        Boolean correctForDrops = rule.containsKey("correct_for_drops")
                ? Boolean.valueOf(bool(rule.get("correct_for_drops"), false)) : null;

        return new ItemComponents.ToolRule(tag, List.copyOf(blocks), speed, correctForDrops);
    }

    /** Normalisiert ein Attribut: entfernt Präfixe wie "generic." und ergänzt "minecraft:". */
    private static String attribute(String value, String path) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String namespace = "minecraft";
        String name = normalized;
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            namespace = normalized.substring(0, colon);
            name = normalized.substring(colon + 1);
        }
        for (String prefix : List.of("generic.", "player.")) {
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
            }
        }
        return location(namespace + ":" + name, path);
    }

    /** Akzeptiert die Vanilla-Namen und die alten Bukkit-Namen und liefert den Vanilla-Namen großgeschrieben. */
    private static String operation(String value, String path) {
        if (value == null) {
            return "ADD_VALUE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        normalized = switch (normalized) {
            case "ADD_NUMBER" -> "ADD_VALUE";
            case "ADD_SCALAR" -> "ADD_MULTIPLIED_BASE";
            case "MULTIPLY_SCALAR_1" -> "ADD_MULTIPLIED_TOTAL";
            default -> normalized;
        };
        if (!OPERATIONS.contains(normalized)) {
            throw new IllegalArgumentException("'" + path + "' must be one of: add_value, add_multiplied_base, add_multiplied_total");
        }
        return normalized;
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    private static Map<String, Object> section(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("'" + path + "' must be a section");
        }
        return lowerKeys(map);
    }

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

    private static double number(Object value, String path) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + path + "' must be a number");
        }
    }

    private static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value).trim());
    }

    /** Sekunden (auch mit Nachkommastellen) oder "infinite" bzw. -1 für unendlich; Ergebnis in Ticks. */
    private static int duration(Object value, String path) {
        if (value instanceof String text && text.trim().equalsIgnoreCase("infinite")) {
            return -1;
        }
        double seconds = number(value, path);
        if (seconds == -1) {
            return -1;
        }
        if (seconds < 0) {
            throw new IllegalArgumentException("'" + path + "' must not be negative (or use 'infinite')");
        }
        return (int) Math.max(1, Math.round(seconds * 20));
    }

    /** Prüft eine Resource Location und ergänzt {@code minecraft:}. {@code null} bleibt {@code null}. */
    private static String location(String value, String path) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!LOCATION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("'" + path + "' is not a valid resource location: " + value);
        }
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}