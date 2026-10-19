package net.glademc.glade.paper.mechanics;

import net.glademc.glade.core.item.ItemDefinition;
import net.glademc.glade.core.item.ItemMechanics;
import net.glademc.glade.paper.GladePaperPlugin;
import net.glademc.glade.paper.item.ItemMenu;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Setzt den Abschnitt {@code mechanics} der Items um:
 *
 * <ul>
 *   <li>Befehle und Nachrichten bei Rechts-/Linksklick (auch mit Shift)</li>
 *   <li>Permission zum Benutzen, Cooldown, Verbrauch eines Items pro Benutzung</li>
 *   <li>Items, die nicht fallen gelassen oder nicht in Behälter gelegt werden können</li>
 * </ul>
 *
 * <p>Nicht abgedeckt: Items, die beim Tod fallen gelassen werden, und Hopper.</p>
 */
public final class MechanicsListener implements Listener {

    private final GladePaperPlugin plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public MechanicsListener(GladePaperPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Klicks
    // ------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL || event.getHand() == null || event.getItem() == null) {
            return;
        }

        ItemDefinition definition = definitionOf(event.getItem());
        if (definition == null || definition.mechanics().isEmpty()) {
            return;
        }
        ItemMechanics mechanics = definition.mechanics();

        Player player = event.getPlayer();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean sneaking = player.isSneaking();
        ItemMechanics.Click click = right
                ? (sneaking ? ItemMechanics.Click.SHIFT_RIGHT_CLICK : ItemMechanics.Click.RIGHT_CLICK)
                : (sneaking ? ItemMechanics.Click.SHIFT_LEFT_CLICK : ItemMechanics.Click.LEFT_CLICK);
        List<ItemMechanics.Action> actions = mechanics.actions(click);

        // Permission: gilt für jeden Rechtsklick und für jeden Klick, für den Aktionen angegeben sind
        if (mechanics.usePermission() != null && (right || !actions.isEmpty())
                && !player.hasPermission(mechanics.usePermission())) {
            event.setCancelled(true);
            player.sendMessage(plugin.messages().component("general.no_permission",
                    Map.of("permission", mechanics.usePermission())));
            return;
        }

        if (actions.isEmpty()) {
            return;
        }

        // Cooldown
        String cooldownKey = player.getUniqueId() + "|" + definition.key();
        long now = System.currentTimeMillis();
        if (mechanics.cooldownSeconds() > 0) {
            Long until = cooldowns.get(cooldownKey);
            if (until != null && until > now) {
                event.setCancelled(true);
                long remaining = (long) Math.ceil((until - now) / 1000.0);
                player.sendMessage(plugin.messages().component("mechanics.cooldown",
                        Map.of("seconds", String.valueOf(remaining))));
                return;
            }
            cooldowns.put(cooldownKey, now + (long) (mechanics.cooldownSeconds() * 1000));
        }

        if (mechanics.cancelVanilla()) {
            event.setCancelled(true);
        }

        run(player, actions);

        if (mechanics.consume() && player.getGameMode() != GameMode.CREATIVE) {
            consumeOne(player, event.getHand());
        }
    }

    private void run(Player player, List<ItemMechanics.Action> actions) {
        for (ItemMechanics.Action action : actions) {
            String value = replacePlaceholders(action.value(), player);
            switch (action.type()) {
                case PLAYER -> player.getScheduler().run(plugin, task -> player.performCommand(value), null);
                case CONSOLE -> plugin.getServer().getGlobalRegionScheduler().run(plugin, task ->
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), value));
                case MESSAGE -> player.sendMessage(plugin.messages().parse(value));
            }
        }
    }

    private static String replacePlaceholders(String text, Player player) {
        return text
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName());
    }

    private static void consumeOne(Player player, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(hand);
        if (stack.getAmount() <= 1) {
            inventory.setItem(hand, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(hand, stack);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String prefix = event.getPlayer().getUniqueId() + "|";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    // ------------------------------------------------------------------
    // Fallenlassen
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemDefinition definition = definitionOf(event.getItemDrop().getItemStack());
        if (definition != null && !definition.mechanics().droppable()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().component("mechanics.not_droppable"));
        }
    }

    // ------------------------------------------------------------------
    // Verschieben in Behälter
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !isContainer(top)) {
            return;
        }

        List<ItemStack> moving = new ArrayList<>();
        if (clicked.equals(top)) {
            // Etwas in den Behälter legen: Item am Cursor oder per Zahlen-/Zweithand-Taste
            moving.add(event.getCursor());
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
                moving.add(player.getInventory().getItem(event.getHotbarButton()));
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                moving.add(player.getInventory().getItemInOffHand());
            }
        } else if (event.isShiftClick()) {
            // Shift-Klick im eigenen Inventar schiebt das Item in den Behälter
            moving.add(event.getCurrentItem());
        }

        if (moving.stream().anyMatch(this::isImmovable)) {
            event.setCancelled(true);
            player.sendMessage(plugin.messages().component("mechanics.not_movable"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!isContainer(top) || !isImmovable(event.getOldCursor())) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
            player.sendMessage(plugin.messages().component("mechanics.not_movable"));
        }
    }

    /** Behälter ist alles außer dem eigenen Inventar (inklusive Crafting-Raster) und dem Glade-Menü. */
    private static boolean isContainer(Inventory top) {
        return top.getType() != InventoryType.CRAFTING
                && top.getType() != InventoryType.PLAYER
                && !(top.getHolder(false) instanceof ItemMenu);
    }

    private boolean isImmovable(ItemStack stack) {
        ItemDefinition definition = definitionOf(stack);
        return definition != null && !definition.mechanics().movable();
    }

    private ItemDefinition definitionOf(ItemStack stack) {
        String key = plugin.itemFactory().idOf(stack);
        return key == null ? null : plugin.itemRegistry().get(key).orElse(null);
    }
}