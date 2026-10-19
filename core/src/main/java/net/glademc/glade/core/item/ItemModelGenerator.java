package net.glademc.glade.core.item;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Erzeugt für Items mit {@code pack.generate_model: true} die Dateien, die ins Resourcepack
 * gehören (jeweils im Namespace des Items):
 *
 * <ul>
 *   <li>{@code assets/<namespace>/items/<id>.json}: Item-Definition, die von der Komponente
 *       {@code item_model} ({@code <namespace>:<id>}) angesprochen wird</li>
 *   <li>{@code assets/<namespace>/models/item/<id>.json}: das Modell mit Eltern-Modell und Textur</li>
 * </ul>
 *
 * <p>Die Textur selbst legst du unter {@code pack/assets/<namespace>/textures/...png} ab. Alle Werte
 * wurden vom {@link ItemParser} geprüft, deshalb ist kein weiteres Escaping nötig.</p>
 */
public final class ItemModelGenerator {

    private ItemModelGenerator() {
    }

    /** @return Pfad in der Zip (z. B. {@code assets/glade/items/ruby.json}) zu Dateiinhalt */
    public static Map<String, byte[]> generate(Collection<ItemDefinition> definitions) {
        Map<String, byte[]> files = new TreeMap<>();
        for (ItemDefinition definition : definitions) {
            ItemDefinition.GeneratedModel model = definition.generated();
            if (model == null) {
                continue;
            }

            String namespace = definition.namespace();
            String id = definition.id();
            String itemJson = "{\n"
                    + "  \"model\": {\n"
                    + "    \"type\": \"minecraft:model\",\n"
                    + "    \"model\": \"" + namespace + ":item/" + id + "\"\n"
                    + "  }\n"
                    + "}\n";
            String modelJson = "{\n"
                    + "  \"parent\": \"" + model.parentModel() + "\",\n"
                    + "  \"textures\": {\n"
                    + "    \"layer0\": \"" + model.texture() + "\"\n"
                    + "  }\n"
                    + "}\n";

            files.put("assets/" + namespace + "/items/" + id + ".json",
                    itemJson.getBytes(StandardCharsets.UTF_8));
            files.put("assets/" + namespace + "/models/item/" + id + ".json",
                    modelJson.getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }
}