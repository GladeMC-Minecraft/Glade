package net.glademc.glade.paper.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.glademc.glade.core.item.ItemDefinition;
import net.glademc.glade.core.item.ItemRegistry;
import net.glademc.glade.core.util.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gleicht bereits vergebene Glade-Items mit der aktuellen Definition ab.
 *
 * <p>Aktualisiert wird der Name aus {@code display_name}. Er steckt in der Komponente
 * {@code item_name}. Benennt ein Spieler ein Item manuell um (zum Beispiel am Amboss), setzt
 * Minecraft dafür die getrennte Komponente {@code custom_name}, die immer Vorrang hat. Diese
 * fassen wir nie an: Ein manuell umbenanntes Item behält also seinen Namen, auch wenn sich
 * der Name in der Config ändert.</p>
 *
 * <p>Wann aktualisiert wird:</p>
 * <ul>
 *   <li>bei {@code /glade reload items}: Inventar und Endertruhe aller Spieler, die online sind</li>
 *   <li>beim Beitritt eines Spielers (so holen Offline-Spieler die Änderung nach)</li>
 *   <li>beim Öffnen einer Kiste oder eines anderen Behälters</li>
 * </ul>
 */
public final class ItemUpdater {

    private final ItemFactory factory;
    private final ItemRegistry registry;
    private final MessageUtils messages;

    public ItemUpdater(ItemFactory factory, ItemRegistry registry, MessageUtils messages) {
        this.factory = factory;
        this.registry = registry;
        this.messages = messages;
    }

    /**
     * Gleicht einen einzelnen Stack ab.
     *
     * @return {@code true}, wenn der Stack verändert wurde
     */
    public boolean refresh(ItemStack stack) {
        String id = factory.idOf(stack);
        if (id == null) {
            return false;
        }
        ItemDefinition definition = registry.get(id).orElse(null);
        if (definition == null) {
            return false; // unbekanntes Item (z. B. aus der Config entfernt): nicht anfassen
        }

        if (definition.displayName() == null) {
            if (stack.isDataOverridden(DataComponentTypes.ITEM_NAME)) {
                stack.resetData(DataComponentTypes.ITEM_NAME);
                return true;
            }
            return false;
        }

        Component expected = messages.parse(definition.displayName());
        if (expected.equals(stack.getData(DataComponentTypes.ITEM_NAME))) {
            return false;
        }
        stack.setData(DataComponentTypes.ITEM_NAME, expected);
        return true;
    }

    /** @return Anzahl der veränderten Stacks */
    public int refreshInventory(Inventory inventory) {
        int changed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && refresh(stack)) {
                inventory.setItem(slot, stack);
                changed++;
            }
        }
        return changed;
    }

    /** Inventar (inklusive Rüstung und Zweithand) und Endertruhe. Muss auf dem Thread des Spielers laufen. */
    public int refreshPlayer(Player player) {
        return refreshInventory(player.getInventory()) + refreshInventory(player.getEnderChest());
    }

    /** Gleicht alle Spieler ab, die gerade online sind (über deren eigenen Scheduler, auch unter Folia sicher). */
    public void refreshOnlinePlayers(JavaPlugin plugin) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> refreshPlayer(player), null);
        }
    }
}