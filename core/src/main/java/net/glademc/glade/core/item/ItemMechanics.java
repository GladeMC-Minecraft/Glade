package net.glademc.glade.core.item;

import java.util.List;
import java.util.Map;

/**
 * Das Verhalten eines Items aus dem Abschnitt {@code mechanics} (alles, was Glade selbst
 * umsetzt und was Minecraft nicht kennt).
 *
 * @param usePermission Permission, die man zum Benutzen braucht, oder {@code null}
 * @param cooldownSeconds Sekunden zwischen zwei Benutzungen pro Spieler ({@code 0} = kein Cooldown)
 * @param consume       verbraucht bei jeder Benutzung ein Item (nur wenn für den Klick Aktionen laufen)
 * @param cancelVanilla bricht die normale Aktion (z. B. Block platzieren) ab, wenn Aktionen laufen
 * @param droppable     {@code false}: das Item kann nicht fallen gelassen werden
 * @param movable       {@code false}: das Item kann nicht in Behälter gelegt werden
 * @param commands      Aktionen pro Klick-Art
 */
public record ItemMechanics(
        String usePermission,
        float cooldownSeconds,
        boolean consume,
        boolean cancelVanilla,
        boolean droppable,
        boolean movable,
        Map<Click, List<Action>> commands) {

    public static final ItemMechanics NONE = new ItemMechanics(null, 0f, false, false, true, true, Map.of());

    /** Art des Klicks. Die Shift-Varianten gelten, wenn der Spieler dabei schleicht. */
    public enum Click {
        RIGHT_CLICK, LEFT_CLICK, SHIFT_RIGHT_CLICK, SHIFT_LEFT_CLICK
    }

    /** Was eine Aktion tut. */
    public enum ActionType {
        /** Der Spieler führt den Befehl aus. */
        PLAYER,
        /** Die Konsole führt den Befehl aus. */
        CONSOLE,
        /** Dem Spieler wird eine Nachricht (MiniMessage) gesendet. */
        MESSAGE
    }

    /**
     * @param type  Art der Aktion
     * @param value Befehl (ohne führenden Schrägstrich) oder Nachricht, mit Platzhaltern wie {@code %player%}
     */
    public record Action(ActionType type, String value) {
    }

    public boolean isEmpty() {
        return usePermission == null && cooldownSeconds == 0f && !consume && !cancelVanilla
                && droppable && movable && commands.isEmpty();
    }

    /** Die Aktionen für eine Klick-Art, leer wenn keine angegeben sind. */
    public List<Action> actions(Click click) {
        return commands.getOrDefault(click, List.of());
    }
}