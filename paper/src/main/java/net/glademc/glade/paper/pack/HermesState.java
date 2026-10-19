package net.glademc.glade.paper.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Der zuletzt auf Hermes hochgeladene Stand des Packs (URL und SHA-1-Hash).
 * Wird in {@code plugins/Glade/pack/hermes.json} gespeichert, damit nach einem Neustart
 * nichts erneut hochgeladen werden muss.
 */
public record HermesState(String url, String sha1) {

    /** @return der gespeicherte Stand oder {@code null}, wenn es keinen (gültigen) gibt */
    public static HermesState load(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String url = json.has("url") ? json.get("url").getAsString() : "";
            String sha1 = json.has("sha1") ? json.get("sha1").getAsString() : "";
            if (url.isBlank() || sha1.isBlank()) {
                return null;
            }
            return new HermesState(url, sha1);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public void save(Path file) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("url", url);
        json.addProperty("sha1", sha1);
        Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}