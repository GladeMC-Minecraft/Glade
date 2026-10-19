package net.glademc.glade.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.glademc.glade.core.item.ItemDefinition;
import net.glademc.glade.paper.GladePaperPlugin;
import net.glademc.glade.paper.item.ItemGiver;
import net.glademc.glade.paper.item.ItemLoader;
import net.glademc.glade.paper.item.ItemMenu;
import net.kyori.adventure.key.Key;
import net.glademc.glade.paper.pack.HermesState;
import net.glademc.glade.paper.pack.PackBuilder;
import net.glademc.glade.paper.pack.PackMode;
import net.glademc.glade.paper.pack.PackSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Der Befehl {@code /glade} (Brigadier über das Paper-Lifecycle-Event).
 *
 * <p>Permission-Schema für Befehle: {@code glade.command.<befehl>.<unterbefehl>.<unterbefehl>...}</p>
 *
 * <ul>
 *   <li>{@code /glade} benötigt {@code glade.command.glade}</li>
 *   <li>{@code /glade reload} (zeigt die Verwendung) benötigt {@code glade.command.glade.reload}</li>
 *   <li>{@code /glade reload configs} benötigt {@code glade.command.glade.reload.configs}</li>
 *   <li>{@code /glade reload items} benötigt {@code glade.command.glade.reload.items}</li>
 *   <li>{@code /glade generatepack} benötigt {@code glade.command.glade.generatepack}</li>
 *   <li>{@code /glade menu} benötigt {@code glade.command.glade.menu}
 *       (Items daraus nehmen: {@code glade.command.glade.menu.take})</li>
 *   <li>{@code /glade give <spieler> <item> [menge]} benötigt {@code glade.command.glade.give}</li>
 *   <li>{@code /glade sendpack} (zeigt die Verwendung) benötigt {@code glade.command.glade.sendpack}</li>
 *   <li>{@code /glade sendpack all} benötigt {@code glade.command.glade.sendpack.all}</li>
 *   <li>{@code /glade sendpack <spieler>} benötigt {@code glade.command.glade.sendpack.player}</li>
 * </ul>
 *
 * <p>Die Berechtigung wird beim Ausführen geprüft. Fehlt sie, bekommt der Sender die Nachricht
 * {@code general.no_permission} mit der fehlenden Permission.</p>
 */
public final class GladeCommand {

    private static final String PERMISSION_PREFIX = "glade.command";

    public static final String PERMISSION_BASE = permission("glade");
    public static final String PERMISSION_RELOAD = permission("glade", "reload");
    public static final String PERMISSION_RELOAD_CONFIGS = permission("glade", "reload", "configs");
    public static final String PERMISSION_RELOAD_ITEMS = permission("glade", "reload", "items");
    public static final String PERMISSION_GENERATEPACK = permission("glade", "generatepack");
    public static final String PERMISSION_MENU = permission("glade", "menu");
    public static final String PERMISSION_MENU_TAKE = permission("glade", "menu", "take");
    public static final String PERMISSION_GIVE = permission("glade", "give");
    public static final String PERMISSION_SENDPACK = permission("glade", "sendpack");
    public static final String PERMISSION_SENDPACK_ALL = permission("glade", "sendpack", "all");
    public static final String PERMISSION_SENDPACK_PLAYER = permission("glade", "sendpack", "player");

    /** Verhindert, dass zwei Pack-Generierungen gleichzeitig laufen. */
    private static final AtomicBoolean GENERATING = new AtomicBoolean();

    private GladeCommand() {
    }

    /**
     * Baut eine Permission nach dem Schema {@code glade.command.<teil>.<teil>...}.
     * Beispiel: {@code permission("glade", "reload")} ergibt {@code glade.command.glade.reload}.
     */
    public static String permission(String... path) {
        return PERMISSION_PREFIX + "." + String.join(".", path);
    }

    /** Registriert die Permissions (Standard: nur Operatoren). */
    public static void registerPermissions(GladePaperPlugin plugin) {
        PluginManager manager = plugin.getServer().getPluginManager();
        addPermission(manager, PERMISSION_BASE, "Allows using /glade");
        addPermission(manager, PERMISSION_RELOAD, "Allows using /glade reload");
        addPermission(manager, PERMISSION_RELOAD_CONFIGS, "Allows using /glade reload configs");
        addPermission(manager, PERMISSION_RELOAD_ITEMS, "Allows using /glade reload items");
        addPermission(manager, PERMISSION_GENERATEPACK, "Allows using /glade generatepack");
        addPermission(manager, PERMISSION_MENU, "Allows opening /glade menu");
        addPermission(manager, PERMISSION_MENU_TAKE, "Allows taking items out of /glade menu");
        addPermission(manager, PERMISSION_GIVE, "Allows using /glade give");
        addPermission(manager, PERMISSION_SENDPACK, "Allows using /glade sendpack");
        addPermission(manager, PERMISSION_SENDPACK_ALL, "Allows using /glade sendpack all");
        addPermission(manager, PERMISSION_SENDPACK_PLAYER, "Allows using /glade sendpack <player>");
    }

    private static void addPermission(PluginManager manager, String name, String description) {
        if (manager.getPermission(name) == null) {
            manager.addPermission(new Permission(name, description, PermissionDefault.OP));
        }
    }

    public static LiteralCommandNode<CommandSourceStack> build(GladePaperPlugin plugin) {
        return Commands.literal("glade")
                .executes(context -> usage(plugin, context.getSource().getSender()))
                .then(Commands.literal("reload")
                        .executes(context -> reloadUsage(plugin, context.getSource().getSender()))
                        .then(Commands.literal("configs")
                                .executes(context -> reloadConfigs(plugin, context.getSource().getSender())))
                        .then(Commands.literal("items")
                                .executes(context -> reloadItems(plugin, context.getSource().getSender()))))
                .then(Commands.literal("generatepack")
                        .executes(context -> generatePack(plugin, context.getSource().getSender())))
                .then(Commands.literal("menu")
                        .executes(context -> menu(plugin, context.getSource().getSender())))
                .then(Commands.literal("give")
                        .executes(context -> giveUsage(plugin, context.getSource().getSender()))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("item", ArgumentTypes.key())
                                        .suggests(itemSuggestions(plugin))
                                        .executes(context -> give(plugin, context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
                                                .executes(context -> give(plugin, context,
                                                        IntegerArgumentType.getInteger(context, "amount")))))))
                .then(Commands.literal("sendpack")
                        .executes(context -> sendPackUsage(plugin, context.getSource().getSender()))
                        .then(Commands.literal("all")
                                .executes(context -> sendPackAll(plugin, context.getSource().getSender())))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(context -> sendPackPlayer(plugin, context))))
                .build();
    }

    /**
     * Prüft die Berechtigung. Fehlt sie, wird die Nachricht {@code general.no_permission}
     * gesendet und {@code false} zurückgegeben.
     */
    private static boolean checkPermission(GladePaperPlugin plugin, CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(plugin.messages().component("general.no_permission",
                Map.of("permission", permission)));
        return false;
    }

    private static int usage(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_BASE)) {
            return 0;
        }
        sender.sendMessage(plugin.messages().component("command.usage"));
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadUsage(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_RELOAD)) {
            return 0;
        }
        sender.sendMessage(plugin.messages().component("command.reload_usage"));
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadConfigs(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_RELOAD_CONFIGS)) {
            return 0;
        }
        try {
            plugin.reloadConfigs();
            sender.sendMessage(plugin.messages().component("general.reload_success"));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
            sender.sendMessage(plugin.messages().component("general.reload_failed"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int generatePack(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_GENERATEPACK)) {
            return 0;
        }
        if (!GENERATING.compareAndSet(false, true)) {
            sender.sendMessage(plugin.messages().component("command.generatepack_busy"));
            return 0;
        }

        sender.sendMessage(plugin.messages().component("command.generatepack_start"));

        // Dateizugriffe und Netzwerk laufen asynchron, damit der Server nicht stockt
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                PackBuilder.Result result = new PackBuilder(plugin.folders(), plugin.getLogger(), plugin.generatedPackFiles()).build();
                PackSettings settings = plugin.packSettings();
                boolean hermes = settings.mode() == PackMode.HERMES;

                if (!hermes) {
                    plugin.reloadPack();
                }
                sender.sendMessage(plugin.messages().component("command.generatepack_success", Map.of(
                        "files", String.valueOf(result.files()),
                        "size", String.format(Locale.ROOT, "%.2f MB", result.bytes() / 1_048_576.0),
                        "time", result.millis() + " ms")));

                if (hermes) {
                    uploadToHermes(plugin, sender, result, settings);
                }
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Pack generation failed", e);
                sender.sendMessage(plugin.messages().component("command.generatepack_failed",
                        Map.of("error", String.valueOf(e.getMessage()))));
            } finally {
                GENERATING.set(false);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static void uploadToHermes(GladePaperPlugin plugin, CommandSender sender,
                                       PackBuilder.Result result, PackSettings settings) {
        sender.sendMessage(plugin.messages().component("command.generatepack_hermes_start"));
        try {
            HermesState state = plugin.packService().uploadToHermes(result.zip(), settings.hermesKey());
            sender.sendMessage(plugin.messages().component("command.generatepack_hermes_success",
                    Map.of("url", state.url())));
        } catch (IOException e) {
            plugin.getLogger().warning("Hermes upload failed: " + e.getMessage());
            sender.sendMessage(plugin.messages().component("command.generatepack_hermes_failed",
                    Map.of("error", String.valueOf(e.getMessage()))));
        }
    }

    private static int sendPackUsage(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_SENDPACK)) {
            return 0;
        }
        sender.sendMessage(plugin.messages().component("command.sendpack_usage"));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendPackAll(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_SENDPACK_ALL)) {
            return 0;
        }
        if (!plugin.packService().isReady()) {
            sender.sendMessage(plugin.messages().component("command.sendpack_not_ready"));
            return 0;
        }
        Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
        if (players.isEmpty()) {
            sender.sendMessage(plugin.messages().component("command.sendpack_no_players"));
            return 0;
        }
        dispatchPack(plugin, players);
        sender.sendMessage(plugin.messages().component("command.sendpack_all",
                Map.of("count", String.valueOf(players.size()))));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendPackPlayer(GladePaperPlugin plugin, CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        if (!checkPermission(plugin, sender, PERMISSION_SENDPACK_PLAYER)) {
            return 0;
        }
        if (!plugin.packService().isReady()) {
            sender.sendMessage(plugin.messages().component("command.sendpack_not_ready"));
            return 0;
        }

        List<Player> targets = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource());
        if (targets.isEmpty()) {
            sender.sendMessage(plugin.messages().component("command.sendpack_no_players"));
            return 0;
        }

        dispatchPack(plugin, targets);
        if (targets.size() == 1) {
            sender.sendMessage(plugin.messages().component("command.sendpack_player",
                    Map.of("player", targets.get(0).getName())));
        } else {
            sender.sendMessage(plugin.messages().component("command.sendpack_all",
                    Map.of("count", String.valueOf(targets.size()))));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Sendet das Pack über den Scheduler des jeweiligen Spielers (sicher auch unter Folia). */
    private static void dispatchPack(GladePaperPlugin plugin, Collection<? extends Player> players) {
        for (Player player : players) {
            player.getScheduler().run(plugin, task -> plugin.packService().send(player), null);
        }
    }

    private static int reloadItems(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_RELOAD_ITEMS)) {
            return 0;
        }
        ItemLoader.Result result = plugin.reloadItems();
        sender.sendMessage(plugin.messages().component("command.reload_items_success", Map.of(
                "count", String.valueOf(result.loaded()),
                "errors", String.valueOf(result.errors()))));
        return Command.SINGLE_SUCCESS;
    }

    private static int menu(GladePaperPlugin plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().component("general.player_only"));
            return 0;
        }
        if (!checkPermission(plugin, sender, PERMISSION_MENU)) {
            return 0;
        }
        new ItemMenu(plugin, player, 0).open(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int giveUsage(GladePaperPlugin plugin, CommandSender sender) {
        if (!checkPermission(plugin, sender, PERMISSION_GIVE)) {
            return 0;
        }
        sender.sendMessage(plugin.messages().component("command.give_usage"));
        return Command.SINGLE_SUCCESS;
    }

    private static SuggestionProvider<CommandSourceStack> itemSuggestions(GladePaperPlugin plugin) {
        return (context, builder) -> {
            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String key : plugin.itemRegistry().ids()) {
                String bareId = key.substring(key.indexOf(':') + 1);
                if (key.startsWith(typed) || bareId.startsWith(typed)) {
                    builder.suggest(key);
                }
            }
            return builder.buildFuture();
        };
    }

    private static int give(GladePaperPlugin plugin, CommandContext<CommandSourceStack> context, int amount)
            throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        if (!checkPermission(plugin, sender, PERMISSION_GIVE)) {
            return 0;
        }

        // Ohne Namespace (z. B. "ruby") liefert Minecraft den Namespace "minecraft": dann über die ID suchen
        Key key = context.getArgument("item", Key.class);
        ItemDefinition definition = plugin.itemRegistry().get(key.asString())
                .or(() -> key.namespace().equals("minecraft")
                        ? plugin.itemRegistry().resolve(key.value())
                        : Optional.empty())
                .orElse(null);
        if (definition == null) {
            sender.sendMessage(plugin.messages().component("items.not_found", Map.of("item", key.asString())));
            return 0;
        }

        List<Player> targets = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource());
        if (targets.isEmpty()) {
            sender.sendMessage(plugin.messages().component("command.sendpack_no_players"));
            return 0;
        }

        Map<String, String> placeholders = Map.of(
                "amount", String.valueOf(amount),
                "item", definition.key());
        for (Player target : targets) {
            target.getScheduler().run(plugin, task -> {
                ItemGiver.give(plugin, target, definition, amount);
                target.sendMessage(plugin.messages().component("items.given", placeholders));
            }, null);
        }

        if (targets.size() == 1) {
            Player target = targets.get(0);
            if (!target.equals(sender)) {
                sender.sendMessage(plugin.messages().component("items.given_other", Map.of(
                        "amount", String.valueOf(amount),
                        "item", definition.key(),
                        "player", target.getName())));
            }
        } else {
            sender.sendMessage(plugin.messages().component("command.give_success_multiple", Map.of(
                    "amount", String.valueOf(amount),
                    "item", definition.key(),
                    "count", String.valueOf(targets.size()))));
        }
        return Command.SINGLE_SUCCESS;
    }
}