package net.glademc.glade.paper.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.glademc.glade.core.item.ItemDefinition;
import net.glademc.glade.paper.GladePaperPlugin;
import net.glademc.glade.paper.command.GladeCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Das Menü {@code /glade menu}: zeigt alle Items seitenweise an.
 *
 * <ul>
 *   <li>Linksklick: ein Item erhalten</li>
 *   <li>Shift-Linksklick: einen vollen Stack erhalten</li>
 *   <li>Pfeile unten: blättern</li>
 * </ul>
 *
 * <p>Items erhält nur, wer die Permission {@code glade.command.glade.menu.take} hat.</p>
 */
public final class ItemMenu implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final GladePaperPlugin plugin;
    private final boolean canTake;
    private final List<ItemDefinition> definitions;
    private final int page;
    private final int pages;
    private final Inventory inventory;

    public ItemMenu(GladePaperPlugin plugin, Player viewer, int requestedPage) {
        this.plugin = plugin;
        this.canTake = viewer.hasPermission(GladeCommand.PERMISSION_MENU_TAKE);
        this.definitions = new ArrayList<>(plugin.itemRegistry().all());
        this.pages = Math.max(1, (int) Math.ceil(definitions.size() / (double) PAGE_SIZE));
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));

        Map<String, String> placeholders = Map.of(
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(pages));
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.messages().component("menu.title", placeholders));

        fill(placeholders);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    private void fill(Map<String, String> placeholders) {
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= definitions.size()) {
                break;
            }
            inventory.setItem(slot, displayStack(definitions.get(index)));
        }

        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, button(Material.ARROW, plugin.messages().component("menu.previous")));
        }
        inventory.setItem(SLOT_INFO, button(Material.BOOK, plugin.messages().component("menu.page", placeholders)));
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW, plugin.messages().component("menu.next")));
        }
    }

    /** Das Item mit zusätzlichen Lore-Zeilen (ID und Klick-Hinweis) für die Anzeige im Menü. */
    private ItemStack displayStack(ItemDefinition definition) {
        ItemStack stack = plugin.itemFactory().create(definition, 1);

        List<Component> lines = new ArrayList<>();
        ItemLore existing = stack.getData(DataComponentTypes.LORE);
        if (existing != null) {
            lines.addAll(existing.lines());
        }
        lines.add(Component.empty());
        lines.add(plugin.messages().component("menu.lore_id", Map.of("id", definition.key()))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (canTake) {
            lines.add(plugin.messages().component("menu.lore_take")
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        return stack;
    }

    private static ItemStack button(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        return stack;
    }

    /** Wird vom {@link MenuListener} bei einem Klick in das Menü aufgerufen. */
    public void handleClick(Player player, int slot, ClickType click) {
        if (slot == SLOT_PREVIOUS && page > 0) {
            openPage(player, page - 1);
            return;
        }
        if (slot == SLOT_NEXT && page < pages - 1) {
            openPage(player, page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        int index = page * PAGE_SIZE + slot;
        if (index >= definitions.size()) {
            return;
        }
        if (!(click == ClickType.LEFT || click == ClickType.SHIFT_LEFT)) {
            return;
        }
        if (!player.hasPermission(GladeCommand.PERMISSION_MENU_TAKE)) {
            player.sendMessage(plugin.messages().component("general.no_permission",
                    Map.of("permission", GladeCommand.PERMISSION_MENU_TAKE)));
            return;
        }

        ItemDefinition definition = definitions.get(index);
        ItemStack single = plugin.itemFactory().create(definition, 1);
        int amount = click == ClickType.SHIFT_LEFT ? single.getMaxStackSize() : 1;
        ItemGiver.give(plugin, player, definition, amount);
        player.sendMessage(plugin.messages().component("items.given",
                Map.of("amount", String.valueOf(amount), "item", definition.key())));
    }

    private void openPage(Player player, int newPage) {
        // eine Tick später öffnen, damit das aktuelle Klick-Event sauber beendet ist
        player.getScheduler().run(plugin, task -> new ItemMenu(plugin, player, newPage).open(player), null);
    }
}