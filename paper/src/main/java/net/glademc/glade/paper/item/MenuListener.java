package net.glademc.glade.paper.item;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Verhindert, dass Items aus dem {@link ItemMenu} verschoben werden, und leitet Klicks weiter. */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof ItemMenu menu)) {
            return;
        }
        event.setCancelled(true);

        if (event.getClickedInventory() != top) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            menu.handleClick(player, event.getSlot(), event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ItemMenu) {
            event.setCancelled(true);
        }
    }
}