package net.glademc.glade.paper.file;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verwaltet die Ordnerstruktur von {@code plugins/Glade/}:
 *
 * <pre>
 * Glade/
 * |-- items/
 * |-- glyphs/
 * |-- pack/
 * |   |-- external_packs/
 * |   |-- assets/
 * |   |   |-- minecraft/
 * |   |       |-- pack.mcmeta
 * |   |       |-- pack.png
 * |-- recipes/
 * </pre>
 *
 * <p>Fehlende Ordner werden beim Start angelegt, {@code pack.mcmeta} und {@code pack.png}
 * werden nur geschrieben, wenn sie noch nicht existieren (eigene Änderungen bleiben erhalten).</p>
 */
public final class GladeFolders {

    private static final String PACK_MCMETA_RESOURCE = "pack/assets/minecraft/pack.mcmeta";
    private static final String PACK_ICON_RESOURCE = "pack/assets/minecraft/pack.png";

    private final JavaPlugin plugin;
    private final Path root;

    public GladeFolders(JavaPlugin plugin) {
        this.plugin = plugin;
        this.root = plugin.getDataFolder().toPath();
    }

    /** Legt alle Ordner und die Standard-pack.mcmeta an, falls sie fehlen. */
    public void create() throws IOException {
        Files.createDirectories(items());
        Files.createDirectories(glyphs());
        Files.createDirectories(externalPacks());
        Files.createDirectories(packMinecraft());
        Files.createDirectories(recipes());

        if (Files.notExists(packMcmeta())) {
            plugin.saveResource(PACK_MCMETA_RESOURCE, false);
        }
        if (Files.notExists(packIcon())) {
            plugin.saveResource(PACK_ICON_RESOURCE, false);
        }
    }

    public Path root() {
        return root;
    }

    public Path items() {
        return root.resolve("items");
    }

    public Path glyphs() {
        return root.resolve("glyphs");
    }

    public Path pack() {
        return root.resolve("pack");
    }

    public Path externalPacks() {
        return pack().resolve("external_packs");
    }

    public Path packAssets() {
        return pack().resolve("assets");
    }

    public Path packMinecraft() {
        return packAssets().resolve("minecraft");
    }

    public Path packMcmeta() {
        return packMinecraft().resolve("pack.mcmeta");
    }

    public Path packIcon() {
        return packMinecraft().resolve("pack.png");
    }

    public Path recipes() {
        return root.resolve("recipes");
    }
}