package net.glademc.glade.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.glademc.glade.paper.GladePaperPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.Map;
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
                                .executes(context -> reloadConfigs(plugin, context.getSource().getSender()))))
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
}