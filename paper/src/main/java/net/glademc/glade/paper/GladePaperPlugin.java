package net.glademc.glade.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.glademc.glade.core.GladeCore;
import net.glademc.glade.core.util.MessageUtils;
import net.glademc.glade.paper.command.GladeCommand;
import net.glademc.glade.paper.file.GladeFolders;
import net.glademc.glade.paper.message.MessageLoader;
import net.glademc.glade.paper.pack.PackListener;
import net.glademc.glade.paper.pack.PackService;
import net.glademc.glade.paper.pack.PackSettings;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class GladePaperPlugin extends JavaPlugin {

    /** Wird genutzt, wenn in der config.yml keine (oder eine unbekannte) Sprache steht. */
    public static final String FALLBACK_LANGUAGE = "en";

    private GladeCore core;
    private GladeFolders folders;
    private MessageUtils messages;
    private MessageLoader messageLoader;
    private PackService packService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        folders = new GladeFolders(this);
        try {
            folders.create();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not create the Glade folder structure", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        core = new GladeCore(getPluginMeta().getVersion());
        core.start();

        messages = new MessageUtils(FALLBACK_LANGUAGE);
        messageLoader = new MessageLoader(this, messages);

        packService = new PackService(this, folders);
        getServer().getPluginManager().registerEvents(new PackListener(packService), this);

        reloadConfigs();

        registerCommands();

        getComponentLogger().info(messages.component("general.enabled",
                Map.of("version", core.version())));
    }

    @Override
    public void onDisable() {
        if (packService != null) {
            packService.stop();
        }
        if (messages != null) {
            getComponentLogger().info(messages.component("general.disabled"));
        }
        if (core != null) {
            core.stop();
            core = null;
        }
    }

    private void registerCommands() {
        GladeCommand.registerPermissions(this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        GladeCommand.build(this),
                        "Glade main command",
                        List.of()));
    }

    /**
     * Lädt die config.yml neu und wendet sie an: Sprache, Sprachdateien und der pack-Block
     * (Resourcepack). Wird beim Start und von /glade reload configs aufgerufen.
     */
    public void reloadConfigs() {
        reloadConfig();

        messageLoader.reload();
        String lang = getConfig().getString("lang", FALLBACK_LANGUAGE);
        if (!messages.hasLanguage(lang)) {
            getLogger().warning("Language '" + lang + "' not found in plugins/Glade/messages/, "
                    + "using '" + FALLBACK_LANGUAGE + "' instead.");
            lang = FALLBACK_LANGUAGE;
        }
        messages.setDefaultLanguage(lang);

        packService.reload(PackSettings.load(getConfig(), getLogger()));
    }

    public MessageUtils messages() {
        return messages;
    }

    public GladeFolders folders() {
        return folders;
    }

    public PackService packService() {
        return packService;
    }

    public GladeCore core() {
        return core;
    }
}