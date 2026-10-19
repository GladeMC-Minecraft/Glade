package net.glademc.glade.core.item;

import java.util.List;

/**
 * Eine Item-Definition aus einer YAML-Datei im Ordner {@code items/<namespace>/}.
 *
 * @param namespace    Namespace des Items (Name des Ordners direkt unter {@code items/})
 * @param id           ID innerhalb des Namespaces (nur {@code a-z 0-9 _ . -})
 * @param material     Name des Vanilla-Materials (z. B. {@code PAPER})
 * @param displayName  Anzeigename als MiniMessage oder {@code null}
 * @param lore         Lore-Zeilen als MiniMessage
 * @param maxStackSize eigene maximale Stackgröße (1-99) oder {@code null}
 * @param itemModel    Resource Location für die {@code item_model}-Komponente oder {@code null}
 * @param generated    Angaben zur Modell-Generierung oder {@code null}, wenn kein Modell erzeugt wird
 * @param components   Einstellungen aus dem Abschnitt {@code components} (nie {@code null})
 * @param mechanics    Verhalten aus dem Abschnitt {@code mechanics} (nie {@code null})
 */
public record ItemDefinition(
        String namespace,
        String id,
        String material,
        String displayName,
        List<String> lore,
        Integer maxStackSize,
        String itemModel,
        GeneratedModel generated,
        ItemComponents components,
        ItemMechanics mechanics) {

    /** Der vollständige Schlüssel, z. B. {@code glade:ruby}. */
    public String key() {
        return namespace + ":" + id;
    }

    /**
     * Wird ein Modell von Glade erzeugt, steht hier, wie es aussieht.
     *
     * @param parentModel Eltern-Modell, z. B. {@code minecraft:item/generated} oder {@code minecraft:item/handheld}
     * @param texture     Textur als Resource Location, z. B. {@code glade:item/ruby}
     */
    public record GeneratedModel(String parentModel, String texture) {
    }
}