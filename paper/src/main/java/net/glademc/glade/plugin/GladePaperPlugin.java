package net.glademc.glade.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class GladePaperPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Glade wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Glade wurde deaktiviert!");
    }
}