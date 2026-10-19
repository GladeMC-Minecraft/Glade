package net.glademc.glade.paper.pack;

import java.util.Locale;

/** Wie das Resourcepack an die Spieler ausgeliefert wird ({@code pack.mode} in der config.yml). */
public enum PackMode {

    /** Das Pack liegt extern, es wird nur die URL an die Spieler gesendet. */
    EXTERNAL,

    /** Glade stellt das Pack über einen eingebauten Webserver selbst bereit. */
    SELF_HOST,

    /** Glade lädt das Pack beim Generieren auf den Hermes-Server hoch und nutzt die zurückgegebene URL. */
    HERMES;

    /** Liest einen Wert aus der Config ("external", "self_host", "hermes", ...). Unbekannt ergibt {@code null}. */
    public static PackMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (PackMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}