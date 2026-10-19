package net.glademc.glade.paper.item;

import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.glademc.glade.core.item.ItemComponents;
import net.glademc.glade.core.item.ItemDefinition;
import net.glademc.glade.core.item.ItemParser;
import net.glademc.glade.core.item.ItemRegistry;
import net.glademc.glade.paper.file.GladeFolders;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Liest alle {@code .yml}-Dateien aus {@code plugins/Glade/items/} und füllt die {@link ItemRegistry}.
 *
 * <p>Aufbau:</p>
 * <pre>
 * items/
 * |-- &lt;namespace&gt;/            Name des Ordners = Namespace der Items darin
 * |   |-- &lt;beliebige Unterordner&gt;/
 * |   |   |-- datei.yml
 * |-- datei.yml                Liegt direkt in items/, gilt der Namespace "glade"
 * </pre>
 *
 * <p>Ordnernamen für Namespaces dürfen nur {@code a-z 0-9 _ . -} enthalten (also keine
 * Großbuchstaben oder Leerzeichen), weil sie auch als Ordnername im Resourcepack dienen.
 * Fehler in einzelnen Items oder Dateien werden in der Konsole gemeldet, die übrigen Items
 * werden trotzdem geladen.</p>
 */
public final class ItemLoader {

    /** Ergebnis eines Ladevorgangs. */
    public record Result(int loaded, int errors) {
    }

    private final Logger logger;
    private final GladeFolders folders;
    private final ItemRegistry registry;

    public ItemLoader(JavaPlugin plugin, GladeFolders folders, ItemRegistry registry) {
        this.logger = plugin.getLogger();
        this.folders = folders;
        this.registry = registry;
    }

    public Result reload() {
        Path directory = folders.items();
        Map<String, ItemDefinition> collected = new LinkedHashMap<>();
        int errors = 0;

        for (Path file : listYamlFiles(directory)) {
            Path relative = directory.relativize(file);
            String name = relative.toString().replace('\\', '/');

            String namespace = relative.getNameCount() > 1
                    ? relative.getName(0).toString()
                    : ItemParser.DEFAULT_NAMESPACE;
            if (!ItemParser.isValidNamespace(namespace)) {
                logger.warning("items/" + name + ": the folder name '" + namespace
                        + "' is not a valid namespace (allowed: a-z, 0-9, _ . -). Rename the folder.");
                errors++;
                continue;
            }

            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file.toFile());
            } catch (IOException | InvalidConfigurationException e) {
                logger.warning("Could not read items/" + name + ": " + e.getMessage());
                errors++;
                continue;
            }

            ItemParser.Result parsed = ItemParser.parse("items/" + name, namespace, toMap(yaml));
            for (String error : parsed.errors()) {
                logger.warning(error);
                errors++;
            }
            for (String warning : parsed.warnings()) {
                logger.warning(warning);
            }

            for (ItemDefinition definition : parsed.items()) {
                Material material = Material.matchMaterial(definition.material());
                if (material == null || !material.isItem()) {
                    logger.warning("items/" + name + ": '" + definition.key()
                            + "' has an unknown material: " + definition.material());
                    errors++;
                    continue;
                }
                String problem = validateComponents(definition);
                if (problem != null) {
                    logger.warning("items/" + name + ": '" + definition.key() + "': " + problem);
                    errors++;
                    continue;
                }
                if (collected.containsKey(definition.key())) {
                    logger.warning("items/" + name + ": '" + definition.key()
                            + "' is defined more than once, the first definition is used.");
                    errors++;
                    continue;
                }
                collected.put(definition.key(), definition);
            }
        }

        // Rückgabe-Items prüfen, jetzt kennen wir alle Glade-Items
        for (ItemDefinition definition : collected.values()) {
            String reference = definition.components().useRemainder();
            if (reference != null && !remainderExists(reference, collected)) {
                logger.warning("'" + definition.key() + "': 'components.use_remainder' refers to an unknown item: "
                        + reference + " (the item is loaded without a remainder)");
                errors++;
            }
        }

        registry.replaceAll(collected.values());
        logger.info("Loaded " + collected.size() + " item(s)" + (errors > 0 ? " with " + errors + " problem(s)" : "") + ".");
        return new Result(collected.size(), errors);
    }

    /** Prüft, was nur der Server kennt (Verzauberungen, Animationen, Effekte). @return eine Fehlermeldung oder {@code null} */
    private static String validateComponents(ItemDefinition definition) {
        ItemComponents components = definition.components();

        for (ItemComponents.AttributeModifier modifier : components.attributeModifiers()) {
            if (ItemFactory.findAttribute(modifier.attribute()) == null) {
                return "'components.attribute_modifiers' contains an unknown attribute: " + modifier.attribute();
            }
            if (EquipmentSlotGroup.getByName(modifier.slot()) == null) {
                return "'components.attribute_modifiers' contains an unknown slot: " + modifier.slot();
            }
        }

        if (components.tool() != null) {
            for (ItemComponents.ToolRule rule : components.tool().rules()) {
                if (ItemFactory.findBlocks(rule) == null) {
                    return "'components.tool.rules' refers to an unknown block or tag: "
                            + (rule.tag() != null ? "#" + rule.tag() : String.join(", ", rule.blocks()));
                }
            }
        }

        for (String enchantment : definition.components().enchantments().keySet()) {
            if (ItemFactory.findEnchantment(enchantment) == null) {
                return "'components.enchantments' contains an unknown enchantment: " + enchantment;
            }
        }

        ItemComponents.Consumable consumable = definition.components().consumable();
        if (consumable == null) {
            return null;
        }

        if (consumable.animation() != null) {
            try {
                ItemUseAnimation.valueOf(consumable.animation());
            } catch (IllegalArgumentException e) {
                return "'components.consumable.animation' is unknown: " + consumable.animation().toLowerCase(Locale.ROOT);
            }
        }
        for (ItemComponents.Effect effect : consumable.effects()) {
            NamespacedKey key = NamespacedKey.fromString(effect.effect());
            if (key == null || Registry.EFFECT.get(key) == null) {
                return "'components.consumable.effects' contains an unknown effect: " + effect.effect();
            }
        }
        return null;
    }

    private static boolean remainderExists(String reference, Map<String, ItemDefinition> glade) {
        if (reference.contains(":") && !reference.startsWith("minecraft:")) {
            return glade.containsKey(reference);
        }
        Material material = Material.matchMaterial(reference);
        return material != null && material.isItem();
    }

    private List<Path> listYamlFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return !fileName.startsWith(".") && (fileName.endsWith(".yml") || fileName.endsWith(".yaml"));
                    })
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .forEach(files::add);
            return files;
        } catch (IOException e) {
            logger.warning("Could not list the items folder: " + e.getMessage());
            return List.of();
        }
    }

    private static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                map.put(key, toMap(nested));
            } else if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }
}