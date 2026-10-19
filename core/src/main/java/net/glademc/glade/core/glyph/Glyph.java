package net.glademc.glade.core.glyph;

/**
 * Ein geladener Glyph: die Definition samt dem Zeichen, das ihm zugewiesen wurde.
 *
 * @param definition die Definition aus der YAML-Datei
 * @param character  das zugewiesene Zeichen (ein Zeichen im privaten Unicode-Bereich)
 */
public record Glyph(GlyphDefinition definition, String character) {

    public String key() {
        return definition.key();
    }

    public String id() {
        return definition.id();
    }
}