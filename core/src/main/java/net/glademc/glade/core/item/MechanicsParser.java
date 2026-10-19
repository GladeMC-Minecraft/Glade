package net.glademc.glade.core.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Liest den Abschnitt {@code mechanics} einer Item-Definition.
 *
 * <pre>
 * mechanics:
 *   use_permission: glade.use.ruby   # nötig zum Benutzen (Rechtsklick und alle Klick-Aktionen)
 *   cooldown: 5                      # Sekunden zwischen zwei Benutzungen, pro Spieler
 *   consume: true                    # verbraucht bei jeder Benutzung ein Item (nicht im Kreativmodus)
 *   cancel_vanilla: false            # bricht die normale Aktion ab (z. B. Block platzieren)
 *   droppable: false                 # Item kann nicht fallen gelassen werden (Standard true)
 *   movable: false                   # Item kann nicht in Behälter gelegt werden (Standard true)
 *   commands:
 *     right_click:                   # right_click, left_click, shift_right_click, shift_left_click
 *       - "console: give %player% diamond 1"
 *       - "player: spawn"
 *       - "message: &lt;green&gt;Hallo %player%!"
 * </pre>
 *
 * <p>Aktionen ohne Präfix führt der Spieler aus. Platzhalter: {@code %player%}, {@code %uuid%},
 * {@code %world%}.</p>
 */
final class MechanicsParser {

    private static final Set<String> KNOWN = Set.of(
            "use_permission", "cooldown", "consume", "cancel_vanilla", "droppable", "movable", "commands");
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("[A-Za-z0-9_.*-]+");

    private MechanicsParser() {
    }

    static ItemMechanics parse(Object raw, String where, List<String> warnings) {
        if (raw == null) {
            return ItemMechanics.NONE;
        }
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("'mechanics' must be a section");
        }
        Map<String, Object> section = lowerKeys(rawMap);

        for (String key : section.keySet()) {
            if (!KNOWN.contains(key)) {
                warnings.add(where + ": unknown mechanics option '" + key + "' is ignored (supported: "
                        + String.join(", ", KNOWN.stream().sorted().toList()) + ")");
            }
        }

        String permission = text(section.get("use_permission"));
        if (permission != null) {
            permission = permission.trim();
            if (!PERMISSION_PATTERN.matcher(permission).matches()) {
                throw new IllegalArgumentException("'mechanics.use_permission' is not a valid permission: " + permission);
            }
        }

        float cooldown = 0f;
        if (section.containsKey("cooldown")) {
            cooldown = (float) number(section.get("cooldown"), "mechanics.cooldown");
            if (cooldown < 0) {
                throw new IllegalArgumentException("'mechanics.cooldown' must not be negative");
            }
        }

        boolean consume = bool(section.get("consume"), false);
        boolean cancelVanilla = bool(section.get("cancel_vanilla"), false);
        boolean droppable = bool(section.get("droppable"), true);
        boolean movable = bool(section.get("movable"), true);

        Map<ItemMechanics.Click, List<ItemMechanics.Action>> commands = parseCommands(section.get("commands"), where, warnings);

        if (consume && commands.isEmpty()) {
            warnings.add(where + ": 'mechanics.consume' has no effect without 'mechanics.commands'");
        }
        if (cooldown > 0 && commands.isEmpty()) {
            warnings.add(where + ": 'mechanics.cooldown' has no effect without 'mechanics.commands' "
                    + "(for food use 'components.use_cooldown')");
        }

        return new ItemMechanics(permission, cooldown, consume, cancelVanilla, droppable, movable, commands);
    }

    private static Map<ItemMechanics.Click, List<ItemMechanics.Action>> parseCommands(
            Object raw, String where, List<String> warnings) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("'mechanics.commands' must be a section (right_click, left_click, ...)");
        }

        Map<ItemMechanics.Click, List<ItemMechanics.Action>> result = new EnumMap<>(ItemMechanics.Click.class);
        for (Map.Entry<String, Object> entry : lowerKeys(rawMap).entrySet()) {
            ItemMechanics.Click click = click(entry.getKey());
            if (click == null) {
                warnings.add(where + ": unknown click type 'mechanics.commands." + entry.getKey()
                        + "' is ignored (supported: right_click, left_click, shift_right_click, shift_left_click)");
                continue;
            }

            List<String> lines = new ArrayList<>();
            if (entry.getValue() instanceof List<?> list) {
                for (Object line : list) {
                    lines.add(String.valueOf(line));
                }
            } else if (entry.getValue() != null) {
                lines.add(String.valueOf(entry.getValue()));
            }

            List<ItemMechanics.Action> actions = new ArrayList<>();
            for (String line : lines) {
                actions.add(action(line, "mechanics.commands." + entry.getKey()));
            }
            if (!actions.isEmpty()) {
                result.put(click, List.copyOf(actions));
            }
        }
        return Map.copyOf(result);
    }

    private static ItemMechanics.Click click(String name) {
        return switch (name) {
            case "right_click" -> ItemMechanics.Click.RIGHT_CLICK;
            case "left_click" -> ItemMechanics.Click.LEFT_CLICK;
            case "shift_right_click" -> ItemMechanics.Click.SHIFT_RIGHT_CLICK;
            case "shift_left_click" -> ItemMechanics.Click.SHIFT_LEFT_CLICK;
            default -> null;
        };
    }

    private static ItemMechanics.Action action(String line, String path) {
        String text = line.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        ItemMechanics.ActionType type = ItemMechanics.ActionType.PLAYER;
        String value = text;
        if (lower.startsWith("console:")) {
            type = ItemMechanics.ActionType.CONSOLE;
            value = text.substring("console:".length());
        } else if (lower.startsWith("player:")) {
            value = text.substring("player:".length());
        } else if (lower.startsWith("message:")) {
            type = ItemMechanics.ActionType.MESSAGE;
            value = text.substring("message:".length());
        }

        value = value.trim();
        if (type != ItemMechanics.ActionType.MESSAGE) {
            while (value.startsWith("/")) {
                value = value.substring(1).trim();
            }
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("'" + path + "' contains an empty action: " + line);
        }
        return new ItemMechanics.Action(type, value);
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
}