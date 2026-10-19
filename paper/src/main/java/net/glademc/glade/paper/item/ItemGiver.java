package net.glademc.glade.paper.item;

import net.glademc.glade.core.item.ItemDefinition;
import net.glademc.glade.paper.GladePaperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Gibt einem Spieler Items. Was nicht ins Inventar passt, wird vor ihm fallen gelassen. */
public final class ItemGiver {

    private ItemGiver() {
    }

    /** Muss auf dem Thread des Spielers laufen (Command, Klick-Event oder Spieler-Scheduler). */
    public static void give(GladePaperPlugin plugin, Player player, ItemDefinition definition, int amount) {
        ItemStack stack = plugin.itemFactory().create(definition, amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }
}