package net.glademc.glade.paper.pack;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Sendet das Resourcepack an Spieler, die dem Server beitreten. */
public final class PackListener implements Listener {

    private final PackService packService;

    public PackListener(PackService packService) {
        this.packService = packService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        packService.send(event.getPlayer());
    }
}