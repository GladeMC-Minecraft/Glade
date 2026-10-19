package net.glademc.glade.core.glyph;

import java.util.List;

/**
 * Eine Glyph-Definition aus einer YAML-Datei im Ordner {@code glyphs/<namespace>/}.
 *
 * @param namespace    Namespace des Glyphs (Name des Ordners direkt unter {@code glyphs/})
 * @param id           ID des Glyphs, über alle Namespaces hinweg eindeutig (nur {@code a-z 0-9 _ -})
 * @param texture      Textur als Resource Location ohne Endung, z. B. {@code glade:glyph/heart}
 * @param ascent       senkrechter Versatz des Bildes (höchstens so groß wie {@code height})
 * @param height       Größe des Bildes
 * @param font         Schrift, in der das Zeichen liegt (Standard {@code minecraft:default})
 * @param permission   Permission für Chat-Platzhalter und Tab-Completion oder {@code null} für die Standard-Permission
 * @param placeholders Platzhalter für den Chat, z. B. {@code :heart:}
 * @param tabComplete  Platzhalter im Chat per Tab vorschlagen
 * @param explicitChar fest gewähltes Zeichen (Code-Einheit im Bereich U+E000 bis U+F8FF) oder {@code null}: Glade vergibt es
 */
public record GlyphDefinition(
        String namespace,
        String id,
        String texture,
        int ascent,
        int height,
        String font,
        String permission,
        List<String> placeholders,
        boolean tabComplete,
        Integer explicitChar) {

    /** Der vollständige Schlüssel, z. B. {@code glade:heart}. */
    public String key() {
        return namespace + ":" + id;
    }
}