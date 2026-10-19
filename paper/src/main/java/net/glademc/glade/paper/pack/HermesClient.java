package net.glademc.glade.paper.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Lädt ein Pack auf den Hermes-Server (Supabase) hoch.
 *
 * <ol>
 *   <li>Anfrage an die Edge Function {@code hermes-upload} mit dem Key im Header
 *       {@code x-hermes-key} sowie SHA-1 und Größe der Datei.</li>
 *   <li>Die Function prüft Key und Limits und antwortet mit {@code publicUrl} (Download-Link)
 *       und {@code uploadUrl} (einmalige Upload-Adresse, fehlt wenn die Datei schon existiert).</li>
 *   <li>Die Zip wird per PUT an die {@code uploadUrl} gesendet.</li>
 * </ol>
 */
public final class HermesClient {

    /** Adresse der Edge Function, vor dem ersten Einsatz mit der eigenen Project-Ref ersetzen. */
    public static final String DEFAULT_ENDPOINT =
            "https://zpdnbxgqdfdkwulmkacr.supabase.co/functions/v1/hermes-upload";

    private final String endpoint;
    private final HttpClient http;

    public HermesClient() {
        this(DEFAULT_ENDPOINT);
    }

    public HermesClient(String endpoint) {
        this.endpoint = endpoint;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Lädt die Zip hoch.
     *
     * @return URL und Hash des gehosteten Packs
     * @throws IOException bei Netzwerkfehlern oder wenn Hermes den Upload ablehnt (Meldung ist lesbar)
     */
    public HermesState upload(Path zip, String key) throws IOException {
        if (endpoint.contains("YOUR-PROJECT-REF")) {
            throw new IOException("The Hermes endpoint is not configured (HermesClient.DEFAULT_ENDPOINT)");
        }

        long size = Files.size(zip);
        String sha1 = sha1(zip);

        JsonObject request = new JsonObject();
        request.addProperty("sha1", sha1);
        request.addProperty("size", size);

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-hermes-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                .build());

        JsonObject body = parse(response.body());
        if (response.statusCode() != 200) {
            String error = string(body, "error");
            throw new IOException(error != null ? error : "Hermes answered with HTTP " + response.statusCode());
        }

        String publicUrl = string(body, "publicUrl");
        if (publicUrl == null) {
            throw new IOException("Invalid answer from Hermes");
        }

        String uploadUrl = string(body, "uploadUrl");
        if (uploadUrl != null) {
            HttpResponse<String> upload = send(HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/zip")
                    .header("x-upsert", "true")
                    .PUT(HttpRequest.BodyPublishers.ofFile(zip))
                    .build());
            if (upload.statusCode() / 100 != 2) {
                throw new IOException("Upload to Hermes failed (HTTP " + upload.statusCode() + ")");
            }
        }

        return new HermesState(publicUrl, sha1);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static JsonObject parse(String text) {
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        String value = json.get(key).getAsString();
        return value.isBlank() ? null : value;
    }

    private static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}