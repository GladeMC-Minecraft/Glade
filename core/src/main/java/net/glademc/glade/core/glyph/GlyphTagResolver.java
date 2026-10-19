package net.glademc.glade.core.glyph;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Der MiniMessage-Tag {@code <glyph:id>}, der an dieser Stelle das Bild des Glyphs einfügt.
 *
 * <p>Statt der ID geht auch der vollständige Schlüssel ({@code <glyph:namespace:id>}). Ein
 * unbekannter Glyph bleibt als Text stehen, damit der Tippfehler auffällt.</p>
 */
public final class GlyphTagResolver {

    private GlyphTagResolver() {
    }

    public static TagResolver create(GlyphRegistry registry) {
        return TagResolver.resolver("glyph", (arguments, context) -> {
            String first = arguments.popOr("The <glyph> tag needs an id, for example <glyph:heart>").value();
            String input = arguments.hasNext() ? first + ":" + arguments.pop().value() : first;

            Glyph glyph = registry.resolve(input)
                    .orElseThrow(() -> context.newException("Unknown glyph '" + input + "'", arguments));
            return Tag.inserting(component(glyph));
        });
    }

    /**
     * Das Zeichen des Glyphs als Text. Es ist weiß, damit die Farbe des umgebenden Textes das
     * Bild nicht einfärbt, und nutzt die Schrift des Glyphs, wenn es nicht die Standard-Schrift ist.
     */
    public static Component component(Glyph glyph) {
        Component component = Component.text(glyph.character()).color(NamedTextColor.WHITE);
        String font = glyph.definition().font();
        if (!GlyphParser.DEFAULT_FONT.equals(font)) {
            component = component.font(Key.key(font));
        }
        return component;
    }
}