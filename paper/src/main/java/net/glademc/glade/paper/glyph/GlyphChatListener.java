package net.glademc.glade.paper.glyph;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.glademc.glade.core.glyph.Glyph;
import net.glademc.glade.core.glyph.GlyphRegistry;
import net.glademc.glade.core.glyph.GlyphTagResolver;
import net.glademc.glade.paper.GladePaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ersetzt im Chat die Platzhalter der Glyphs (z. B. {@code :heart:}) durch ihr Bild und schlägt
 * sie per Tab vor.
 *
 * <p>Wer einen Platzhalter nutzen darf, steht in der Permission des Glyphs ({@code permission}) oder,
 * wenn dort nichts steht, in {@code glyphs.default_permission} der config.yml. Ist beides leer,
 * darf jeder.</p>
 */
public final class GlyphChatListener implements Listener {

    private final GladePaperPlugin plugin;
    private final GlyphRegistry registry;
    private final Map<UUID, List<String>> completions = new ConcurrentHashMap<>();

    public GlyphChatListener(GladePaperPlugin plugin, GlyphRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component message = event.message();
        String plain = PlainTextComponentSerializer.plainText().serialize(message);

        boolean changed = false;
        for (Glyph glyph : registry.all()) {
            for (String placeholder : glyph.definition().placeholders()) {
                if (plain.contains(placeholder) && canUse(player, glyph)) {
                    message = message.replaceText(TextReplacementConfig.builder()
                            .matchLiteral(placeholder)
                            .replacement(GlyphTagResolver.component(glyph))
                            .build());
                    changed = true;
                }
            }
        }
        if (changed) {
            event.message(message);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        completions.remove(event.getPlayer().getUniqueId());
    }

    /** Aktualisiert die Tab-Vorschläge eines Spielers. Muss auf dem Thread des Spielers laufen. */
    public void refresh(Player player) {
        List<String> previous = completions.remove(player.getUniqueId());
        if (previous != null) {
            player.removeAdditionalChatCompletions(previous);
        }

        List<String> next = new ArrayList<>();
        for (Glyph glyph : registry.all()) {
            if (glyph.definition().tabComplete() && canUse(player, glyph)) {
                next.addAll(glyph.definition().placeholders());
            }
        }
        if (!next.isEmpty()) {
            player.addAdditionalChatCompletions(next);
            completions.put(player.getUniqueId(), next);
        }
    }

    /** Aktualisiert die Tab-Vorschläge aller Spieler, die gerade online sind (nach /glade reload glyphs). */
    public void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> refresh(player), null);
        }
    }

    private boolean canUse(Player player, Glyph glyph) {
        String permission = glyph.definition().permission();
        if (permission == null) {
            permission = plugin.getConfig().getString("glyphs.default_permission", "");
        }
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }
}