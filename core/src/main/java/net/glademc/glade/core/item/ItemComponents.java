package net.glademc.glade.core.item;

import java.util.List;
import java.util.Map;

/**
 * Die Einstellungen aus dem Abschnitt {@code components} einer Item-Definition.
 * Jede Einstellung ist {@code null}, wenn sie nicht angegeben wurde.
 *
 * @param food         Nährwerte ({@code food})
 * @param consumable   Verhalten beim Essen/Trinken ({@code consumable})
 * @param useRemainder Item, das nach dem Verbrauch zurückbleibt ({@code use_remainder}):
 *                     Vanilla-Material (z. B. {@code GLASS_BOTTLE}) oder Glade-Item ({@code namespace:id})
 * @param useCooldown  Abklingzeit nach der Benutzung ({@code use_cooldown})
 * @param maxDamage    maximale Haltbarkeit ({@code max_damage}) oder {@code null}: das Item hat keine Haltbarkeit
 * @param unbreakable  Item verliert nie Haltbarkeit ({@code unbreakable})
 * @param enchantments Verzauberungen: Resource Location (z. B. {@code minecraft:sharpness}) zu Stufe (1-255)
 * @param glintOverride {@code true}: Item glitzert immer, {@code false}: nie, {@code null}: Standard
 *                      ({@code enchantment_glint_override})
 * @param rarity       Seltenheit großgeschrieben ({@code COMMON}, {@code UNCOMMON}, {@code RARE}, {@code EPIC}) oder {@code null}
 * @param attributeModifiers Attribut-Modifikatoren ({@code attribute_modifiers}), leer = Standardwerte des Materials
 * @param weapon       Waffen-Verhalten ({@code weapon})
 * @param tool         Werkzeug-Verhalten ({@code tool})
 */
public record ItemComponents(
        Food food,
        Consumable consumable,
        String useRemainder,
        UseCooldown useCooldown,
        Integer maxDamage,
        boolean unbreakable,
        Map<String, Integer> enchantments,
        Boolean glintOverride,
        String rarity,
        List<AttributeModifier> attributeModifiers,
        Weapon weapon,
        Tool tool) {

    public static final ItemComponents NONE =
            new ItemComponents(null, null, null, null, null, false, Map.of(), null, null, List.of(), null, null);

    public boolean isEmpty() {
        return food == null && consumable == null && useRemainder == null && useCooldown == null
                && maxDamage == null && !unbreakable && enchantments.isEmpty()
                && glintOverride == null && rarity == null
                && attributeModifiers.isEmpty() && weapon == null && tool == null;
    }

    /**
     * @param nutrition     Hungerpunkte
     * @param saturation    Sättigung
     * @param canAlwaysEat  kann auch mit vollem Hunger gegessen werden
     */
    public record Food(int nutrition, float saturation, boolean canAlwaysEat) {
    }

    /**
     * @param consumeSeconds  Dauer des Essens/Trinkens in Sekunden
     * @param animation       Name der Animation (großgeschrieben, z. B. {@code EAT}) oder {@code null} für den Standard
     * @param sound           Sound beim Essen/Trinken (Resource Location) oder {@code null} für den Standard
     * @param particles       Partikel beim Essen/Trinken
     * @param effects         Statuseffekte beim Verbrauch
     * @param clearEffects    entfernt beim Verbrauch alle Statuseffekte
     * @param teleportDiameter  größer als 0: zufälliger Teleport im Umkreis (Durchmesser in Blöcken)
     * @param playSound       zusätzlicher Sound beim Verbrauch (Resource Location) oder {@code null}
     */
    public record Consumable(
            float consumeSeconds,
            String animation,
            String sound,
            boolean particles,
            List<Effect> effects,
            boolean clearEffects,
            float teleportDiameter,
            String playSound) {
    }

    /**
     * @param effect        Effekt als Resource Location, z. B. {@code minecraft:regeneration}
     * @param durationTicks Dauer in Ticks (20 pro Sekunde), {@code -1} bedeutet unendlich
     * @param amplifier     Stufe minus 1 (0 = Stufe I)
     * @param probability   Wahrscheinlichkeit von 0.0 bis 1.0
     */
    public record Effect(
            String effect,
            int durationTicks,
            int amplifier,
            float probability,
            boolean ambient,
            boolean particles,
            boolean icon) {
    }

    /**
     * @param seconds Abklingzeit in Sekunden
     * @param group   Cooldown-Gruppe (Resource Location) oder {@code null}: dann hat jedes Item seinen eigenen Cooldown
     */
    public record UseCooldown(float seconds, String group) {
    }

    /**
     * @param attribute Attribut als Resource Location, z. B. {@code minecraft:attack_damage}
     * @param amount    Wert des Modifikators
     * @param operation {@code ADD_VALUE}, {@code ADD_MULTIPLIED_BASE} oder {@code ADD_MULTIPLIED_TOTAL}
     * @param slot      Slot-Gruppe kleingeschrieben, z. B. {@code mainhand}, {@code head} oder {@code any}
     * @param id        eigene ID des Modifikators (Resource Location) oder {@code null}: Glade vergibt sie selbst
     */
    public record AttributeModifier(String attribute, double amount, String operation, String slot, String id) {
    }

    /**
     * @param itemDamagePerAttack      Haltbarkeit, die pro Angriff verloren geht
     * @param disableBlockingSeconds   Sekunden, die ein Treffer einen Schild des Gegners ausschaltet
     */
    public record Weapon(int itemDamagePerAttack, float disableBlockingSeconds) {
    }

    /**
     * @param defaultMiningSpeed Abbaugeschwindigkeit, wenn keine Regel passt
     * @param damagePerBlock     Haltbarkeit, die pro abgebautem Block verloren geht
     * @param rules              Regeln für bestimmte Blöcke
     */
    public record Tool(float defaultMiningSpeed, int damagePerBlock, List<ToolRule> rules) {
    }

    /**
     * Eine Abbau-Regel. Entweder {@code tag} ODER {@code blocks} ist gesetzt.
     *
     * @param tag             Block-Tag ohne {@code #} (z. B. {@code minecraft:mineable/pickaxe}) oder {@code null}
     * @param blocks          einzelne Blöcke (Resource Locations), leer wenn ein Tag gesetzt ist
     * @param speed           Abbaugeschwindigkeit für diese Blöcke oder {@code null} für die Standardgeschwindigkeit
     * @param correctForDrops {@code true}: die Blöcke droppen ihre Items, {@code false}: nicht, {@code null}: Standard
     */
    public record ToolRule(String tag, List<String> blocks, Float speed, Boolean correctForDrops) {
    }
}