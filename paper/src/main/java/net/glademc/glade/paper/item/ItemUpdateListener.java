package net.glademc.glade.paper.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;

/** Hält Glade-Items beim Beitritt und beim Öffnen von Behältern auf dem aktuellen Stand. */
public final class ItemUpdateListener implements Listener {

    private final ItemUpdater updater;

    public ItemUpdateListener(ItemUpdater updater) {
        this.updater = updater;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updater.refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder(false) instanceof ItemMenu) {
            return; // das Menü baut seine Items bei jedem Öffnen frisch auf
        }
        updater.refreshInventory(inventory);
    }
}