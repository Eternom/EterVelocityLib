package fr.eternom.eterVelocityLib.orchestrator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Le jar de Paper, depuis l'API Fill v3 de PaperMC. L'ancienne API (api.papermc.io/v2), celle du script
 * d'installation des œufs Pterodactyl, est fermée (410 « sunset ») : un serveur neuf n'avait plus de server.jar et
 * s'arrêtait aussitôt. L'orchestrateur dépose donc lui-même le bon jar avant le premier démarrage.
 */
final class PaperJar {

    /** Un build de Paper : son numéro, son adresse de téléchargement, sa taille. */
    record Build(String version, int id, String url, long size) {
    }

    private static final String API = "https://fill.papermc.io/v3/projects/paper";
    private static final String AGENT = "EterVelocityLib/orchestrator (Eternom)";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Path cache;

    PaperJar(Path cache) {
        this.cache = cache;
    }

    /** Le dernier build de cette version de Minecraft (« 26.2 »). */
    Build latest(String version) throws IOException {
        JsonObject build = JsonParser.parseString(get(API + "/versions/" + version + "/builds/latest")).getAsJsonObject();
        JsonObject download = build.getAsJsonObject("downloads").getAsJsonObject("server:default");
        return new Build(version, build.get("id").getAsInt(), download.get("url").getAsString(), download.get("size").getAsLong());
    }

    /** Le jar sur le proxy (téléchargé une fois par build). */
    Path local(Build build) throws IOException {
        Files.createDirectories(cache);
        Path jar = cache.resolve("paper-" + build.version() + "-" + build.id() + ".jar");
        if (Files.exists(jar) && Files.size(jar) == build.size()) {
            return jar;
        }
        Path part = cache.resolve(jar.getFileName() + ".part");
        HttpResponse<Path> response = send(HttpRequest.newBuilder(URI.create(build.url())).timeout(Duration.ofMinutes(10))
                .header("User-Agent", AGENT).GET().build(), HttpResponse.BodyHandlers.ofFile(part));
        if (response.statusCode() != 200 || Files.size(part) != build.size()) {
            Files.deleteIfExists(part);
            throw new IOException("téléchargement de Paper " + build.id() + " : HTTP " + response.statusCode());
        }
        Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    private String get(String url) throws IOException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", AGENT).header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("API de PaperMC : HTTP " + response.statusCode() + " pour " + url);
        }
        return response.body();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return http.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompu");
        }
    }
}
