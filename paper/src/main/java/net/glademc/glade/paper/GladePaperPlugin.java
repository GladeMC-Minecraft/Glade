package net.glademc.glade.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.glademc.glade.core.GladeCore;
import net.glademc.glade.core.item.ItemModelGenerator;
import net.glademc.glade.core.item.ItemRegistry;
import net.glademc.glade.core.util.MessageUtils;
import net.glademc.glade.paper.command.GladeCommand;
import net.glademc.glade.paper.file.GladeFolders;
import net.glademc.glade.paper.item.ItemFactory;
import net.glademc.glade.paper.item.ItemLoader;
import net.glademc.glade.paper.item.ItemUpdateListener;
import net.glademc.glade.paper.item.ItemUpdater;
import net.glademc.glade.paper.item.MenuListener;
import net.glademc.glade.paper.mechanics.MechanicsListener;
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
    private volatile PackSettings packSettings;
    private ItemRegistry itemRegistry;
    private ItemLoader itemLoader;
    private ItemFactory itemFactory;
    private ItemUpdater itemUpdater;

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

        itemRegistry = new ItemRegistry();
        itemFactory = new ItemFactory(this, messages, itemRegistry);
        itemLoader = new ItemLoader(this, folders, itemRegistry);
        itemUpdater = new ItemUpdater(itemFactory, itemRegistry, messages);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ItemUpdateListener(itemUpdater), this);
        getServer().getPluginManager().registerEvents(new MechanicsListener(this), this);

        reloadConfigs();
        reloadItems();

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

        reloadPack();
    }

    /** Wendet den pack-Block der config.yml neu an (z. B. nach dem Generieren der glade-pack.zip). */
    public void reloadPack() {
        packSettings = PackSettings.load(getConfig(), getLogger());
        packService.reload(packSettings);
    }

    /**
     * Liest alle Item-Dateien aus plugins/Glade/items/ neu ein und gleicht danach die Namen der
     * Items von allen Spielern ab, die gerade online sind.
     */
    public ItemLoader.Result reloadItems() {
        ItemLoader.Result result = itemLoader.reload();
        itemUpdater.refreshOnlinePlayers(this);
        return result;
    }

    /** Von Glade erzeugte Dateien fürs Resourcepack (Item-Modelle), Pfad in der Zip zu Inhalt. */
    public Map<String, byte[]> generatedPackFiles() {
        return ItemModelGenerator.generate(itemRegistry.all());
    }

    public ItemRegistry itemRegistry() {
        return itemRegistry;
    }

    public ItemFactory itemFactory() {
        return itemFactory;
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

    /** Die zuletzt eingelesenen Einstellungen des pack-Blocks. */
    public PackSettings packSettings() {
        return packSettings;
    }

    public GladeCore core() {
        return core;
    }
}