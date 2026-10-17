package net.glademc.glade.core;

import net.glademc.glade.api.GladeApi;

/**
 * Plattformunabhängiger Kern von Glade.
 */
public final class GladeCore implements GladeApi {

    private final String version;

    public GladeCore(String version) {
        this.version = version;
    }

    public void start() {
        // Hier werden später Registries, Config-Loader und Pack-Builder initialisiert.
    }

    public void stop() {
        // Hier werden später Ressourcen freigegeben.
    }

    @Override
    public String version() {
        return version;
    }
}
