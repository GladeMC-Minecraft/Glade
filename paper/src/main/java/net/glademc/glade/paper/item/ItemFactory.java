package net.glademc.glade.paper.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.Weapon;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.glademc.glade.core.item.ItemComponents;
import net.glademc.glade.core.item.ItemDefinition;
import net.glademc.glade.core.item.ItemParser;
import net.glademc.glade.core.item.ItemRegistry;
import net.glademc.glade.core.util.MessageUtils;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Baut aus einer {@link ItemDefinition} einen {@link ItemStack}.
 *
 * <p>Jedes Glade-Item trägt seinen vollständigen Schlüssel ({@code namespace:id}) im
 * Persistent Data Container ({@code glade:item_id}), damit es später sicher erkannt werden
 * kann. Ältere Items speichern nur die ID, diese gelten als Items im Namespace {@code glade}.</p>
 *
 * <p>Essen, Effekte, Rückgabe-Item, Cooldown, Haltbarkeit, Verzauberungen, Glint, Seltenheit,
 * Attribute, Waffen- und Werkzeug-Verhalten setzt Glade als echte Minecraft-Komponenten bzw. über
 * die Item-Meta. Minecraft verarbeitet sie
 * selbst, dafür ist kein eigener Listener nötig.</p>
 */
public final class ItemFactory {

    /** Verhindert Endlosschleifen, wenn sich Items gegenseitig als Rückgabe-Item angeben. */
    private static final int MAX_REMAINDER_DEPTH = 3;

    private final NamespacedKey idKey;
    private final MessageUtils messages;
    private final ItemRegistry registry;

    public ItemFactory(JavaPlugin plugin, MessageUtils messages, ItemRegistry registry) {
        this.idKey = new NamespacedKey(plugin, "item_id");
        this.messages = messages;
        this.registry = registry;
    }

    public ItemStack create(ItemDefinition definition, int amount) {
        return create(definition, amount, 0);
    }

    private ItemStack create(ItemDefinition definition, int amount, int depth) {
        Material material = Material.matchMaterial(definition.material());
        if (material == null) {
            material = Material.BARRIER;
        }

        ItemStack stack = new ItemStack(material, Math.max(1, amount));

        stack.editMeta(meta -> meta.getPersistentDataContainer()
                .set(idKey, PersistentDataType.STRING, definition.key()));

        if (definition.itemModel() != null) {
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(definition.itemModel()));
        }
        if (definition.displayName() != null) {
            stack.setData(DataComponentTypes.ITEM_NAME, messages.parse(definition.displayName()));
        }
        if (!definition.lore().isEmpty()) {
            List<Component> lines = definition.lore().stream()
                    .map(line -> messages.parse(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                    .toList();
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        if (definition.maxStackSize() != null) {
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, definition.maxStackSize());
        }

        applyComponents(stack, definition, depth);
        return stack;
    }

    // ------------------------------------------------------------------
    // components
    // ------------------------------------------------------------------

    private void applyComponents(ItemStack stack, ItemDefinition definition, int depth) {
        ItemComponents components = definition.components();

        applyDurabilityAndEnchantments(stack, components);
        applyAttributesAndTools(stack, definition);

        if (components.food() != null) {
            ItemComponents.Food food = components.food();
            stack.setData(DataComponentTypes.FOOD, FoodProperties.food()
                    .nutrition(food.nutrition())
                    .saturation(food.saturation())
                    .canAlwaysEat(food.canAlwaysEat())
                    .build());
        }

        if (components.consumable() != null) {
            stack.setData(DataComponentTypes.CONSUMABLE, buildConsumable(components.consumable()));
        } else if (components.food() != null) {
            // Nur "food" angegeben: Das Item soll sich wie normales Essen verhalten
            stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().build());
        }

        if (components.useRemainder() != null && depth < MAX_REMAINDER_DEPTH) {
            ItemStack remainder = resolveRemainder(components.useRemainder(), depth);
            if (remainder != null) {
                stack.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(remainder));
            }
        }

        if (components.useCooldown() != null) {
            ItemComponents.UseCooldown cooldown = components.useCooldown();
            // Ohne eigene Gruppe bekommt jedes Glade-Item seine eigene, sonst würden sich alle Items
            // desselben Materials (z. B. PAPER) den Cooldown teilen
            String group = cooldown.group() != null ? cooldown.group() : definition.key();
            stack.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(cooldown.seconds())
                    .cooldownGroup(Key.key(group))
                    .build());
        }
    }

    /**
     * Haltbarkeit, Unzerstörbarkeit, Verzauberungen, Glint und Seltenheit. Die Stackgröße ist bei
     * Items mit {@code max_damage} schon beim Einlesen auf 1 gesetzt worden (beides zusammen geht nicht).
     */
    private void applyDurabilityAndEnchantments(ItemStack stack, ItemComponents components) {
        if (components.maxDamage() != null) {
            stack.setData(DataComponentTypes.MAX_DAMAGE, components.maxDamage());
        }

        if (components.unbreakable() || !components.enchantments().isEmpty() || components.rarity() != null) {
            stack.editMeta(meta -> {
                if (components.unbreakable()) {
                    meta.setUnbreakable(true);
                }
                components.enchantments().forEach((name, level) -> {
                    Enchantment enchantment = findEnchantment(name);
                    if (enchantment != null) { // unbekannte Verzauberungen werden beim Laden gemeldet
                        meta.addEnchant(enchantment, level, true);
                    }
                });
                if (components.rarity() != null) {
                    try {
                        meta.setRarity(ItemRarity.valueOf(components.rarity()));
                    } catch (IllegalArgumentException ignored) {
                        // beim Einlesen schon geprüft
                    }
                }
            });
        }

        if (components.glintOverride() != null) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, components.glintOverride());
        }
    }

    /**
     * Attribut-Modifikatoren, Waffen- und Werkzeug-Verhalten.
     *
     * <p>Achtung: {@code attribute_modifiers} ersetzt die Standard-Attribute des Materials komplett
     * (ein Diamantschwert ohne eigene Angaben hätte dann keinen Schaden mehr).</p>
     */
    private void applyAttributesAndTools(ItemStack stack, ItemDefinition definition) {
        ItemComponents components = definition.components();

        if (!components.attributeModifiers().isEmpty()) {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
            int index = 0;
            for (ItemComponents.AttributeModifier modifier : components.attributeModifiers()) {
                index++;
                Attribute attribute = findAttribute(modifier.attribute());
                EquipmentSlotGroup slot = EquipmentSlotGroup.getByName(modifier.slot());
                String keyText = modifier.id() != null ? modifier.id() : definition.key() + "." + index;
                NamespacedKey key = NamespacedKey.fromString(keyText);
                if (attribute == null || slot == null || key == null) {
                    continue; // wird beim Laden gemeldet
                }
                builder.addModifier(attribute,
                        new AttributeModifier(key, modifier.amount(), operation(modifier.operation()), slot),
                        slot);
            }
            stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        }

        if (components.weapon() != null) {
            ItemComponents.Weapon weapon = components.weapon();
            stack.setData(DataComponentTypes.WEAPON, Weapon.weapon()
                    .itemDamagePerAttack(weapon.itemDamagePerAttack())
                    .disableBlockingForSeconds(weapon.disableBlockingSeconds())
                    .build());
        }

        if (components.tool() != null) {
            ItemComponents.Tool tool = components.tool();
            Tool.Builder builder = Tool.tool()
                    .defaultMiningSpeed(tool.defaultMiningSpeed())
                    .damagePerBlock(tool.damagePerBlock());
            for (ItemComponents.ToolRule rule : tool.rules()) {
                RegistryKeySet<BlockType> blocks = findBlocks(rule);
                if (blocks == null) {
                    continue; // wird beim Laden gemeldet
                }
                TriState drops = rule.correctForDrops() == null
                        ? TriState.NOT_SET
                        : TriState.byBoolean(rule.correctForDrops());
                builder.addRule(Tool.rule(blocks, rule.speed(), drops));
            }
            stack.setData(DataComponentTypes.TOOL, builder.build());
        }
    }

    private static AttributeModifier.Operation operation(String name) {
        return switch (name) {
            case "ADD_MULTIPLIED_BASE" -> AttributeModifier.Operation.ADD_SCALAR;
            case "ADD_MULTIPLIED_TOTAL" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
    }

    /** @return das Attribut zum Schlüssel (z. B. {@code minecraft:attack_damage}) oder {@code null} */
    public static Attribute findAttribute(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) {
            return null;
        }
        Registry<Attribute> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
        return registry.get(namespacedKey);
    }

    /**
     * Die Blöcke einer Werkzeug-Regel: ein Tag oder eine Liste einzelner Blöcke.
     *
     * @return die Blockmenge oder {@code null}, wenn ein Tag oder ein Block nicht existiert
     */
    public static RegistryKeySet<BlockType> findBlocks(ItemComponents.ToolRule rule) {
        Registry<BlockType> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK);

        if (rule.tag() != null) {
            NamespacedKey key = NamespacedKey.fromString(rule.tag());
            if (key == null) {
                return null;
            }
            try {
                return registry.getTag(TagKey.create(RegistryKey.BLOCK, key));
            } catch (NoSuchElementException e) {
                return null;
            }
        }

        List<BlockType> blocks = new ArrayList<>();
        for (String name : rule.blocks()) {
            NamespacedKey key = NamespacedKey.fromString(name);
            BlockType type = key == null ? null : registry.get(key);
            if (type == null) {
                return null;
            }
            blocks.add(type);
        }
        return blocks.isEmpty() ? null : RegistrySet.keySetFromValues(RegistryKey.BLOCK, blocks);
    }

    /** @return die Verzauberung zum Schlüssel (z. B. {@code minecraft:sharpness}) oder {@code null} */
    public static Enchantment findEnchantment(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) {
            return null;
        }
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        return registry.get(namespacedKey);
    }

    private Consumable buildConsumable(ItemComponents.Consumable settings) {
        Consumable.Builder builder = Consumable.consumable()
                .consumeSeconds(settings.consumeSeconds())
                .hasConsumeParticles(settings.particles());

        if (settings.animation() != null) {
            try {
                builder.animation(ItemUseAnimation.valueOf(settings.animation()));
            } catch (IllegalArgumentException ignored) {
                // wird beim Laden gemeldet, hier gilt dann die Standard-Animation
            }
        }
        if (settings.sound() != null) {
            builder.sound(Key.key(settings.sound()));
        }

        for (ItemComponents.Effect effect : settings.effects()) {
            NamespacedKey key = NamespacedKey.fromString(effect.effect());
            PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
            if (type == null) {
                continue; // wird beim Laden gemeldet
            }
            PotionEffect potionEffect = new PotionEffect(type, effect.durationTicks(), effect.amplifier(),
                    effect.ambient(), effect.particles(), effect.icon());
            builder.addEffect(ConsumeEffect.applyStatusEffects(List.of(potionEffect), effect.probability()));
        }

        if (settings.clearEffects()) {
            builder.addEffect(ConsumeEffect.clearAllStatusEffects());
        }
        if (settings.teleportDiameter() > 0) {
            builder.addEffect(ConsumeEffect.teleportRandomlyEffect(settings.teleportDiameter()));
        }
        if (settings.playSound() != null) {
            builder.addEffect(ConsumeEffect.playSoundConsumeEffect(Key.key(settings.playSound())));
        }
        return builder.build();
    }

    /** Ein Vanilla-Material ({@code GLASS_BOTTLE}) oder ein Glade-Item ({@code namespace:id}). */
    private ItemStack resolveRemainder(String reference, int depth) {
        if (reference.contains(":") && !reference.startsWith("minecraft:")) {
            ItemDefinition definition = registry.get(reference).orElse(null);
            return definition == null ? null : create(definition, 1, depth + 1);
        }
        Material material = Material.matchMaterial(reference);
        return material == null || !material.isItem() ? null : new ItemStack(material);
    }

    // ------------------------------------------------------------------
    // Erkennung
    // ------------------------------------------------------------------

    /**
     * @return der vollständige Schlüssel ({@code namespace:id}) des Items oder {@code null},
     * wenn es kein Glade-Item ist
     */
    public String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String stored = stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (stored == null) {
            return null;
        }
        // Items aus der Zeit vor den Namespaces haben nur die ID gespeichert
        return stored.contains(":") ? stored : ItemParser.DEFAULT_NAMESPACE + ":" + stored;
    }
}