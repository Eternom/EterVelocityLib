package fr.eternom.eterVelocityLib.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import fr.eternom.eterVelocityLib.helper.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Le pack de ressources du réseau, imposé par le proxy aux joueurs Java. Le dépôt EterCraft/resourcepack publie le pack
 * dans le bucket public et met à jour un manifeste (pack.json : url, sha1, version) ; ce module le relit toutes les
 * poll-seconds : un nouveau pack est proposé aux joueurs qui arrivent, et à ceux en ligne si push-to-online. Les joueurs
 * Bedrock (Floodgate : UUID qui commence par des zéros) sont ignorés. Réglages : config.yml > resource-pack.
 */
public final class ResourcePacks {

    /** Le pack en cours : son adresse, son empreinte, sa version (le commit qui l'a construit). */
    private record Pack(String url, byte[] sha1, String version) {
    }

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private final Messages messages;
    private final String manifestUrl;
    private final boolean required;
    private final int pollSeconds;
    private final boolean pushToOnline;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile Pack current;

    public ResourcePacks(ProxyServer proxy, Object plugin, Logger logger, Messages messages, String manifestUrl, boolean required,
                         int pollSeconds, boolean pushToOnline) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        this.manifestUrl = manifestUrl;
        this.required = required;
        this.pollSeconds = Math.max(30, pollSeconds);
        this.pushToOnline = pushToOnline;
    }

    public void start() {
        proxy.getEventManager().register(plugin, this);
        proxy.getScheduler().buildTask(plugin, this::poll).repeat(pollSeconds, TimeUnit.SECONDS).schedule();
        logger.info("Pack de ressources : manifeste {} (relu toutes les {} s)", manifestUrl, pollSeconds);
    }

    /** Relit le manifeste ; un pack nouveau devient le pack en cours (et est envoyé aux joueurs en ligne si réglé). */
    private void poll() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(manifestUrl)).timeout(Duration.ofSeconds(10))
                    .header("Cache-Control", "no-cache").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                logger.warn("Pack de ressources : manifeste illisible ({}), on garde le pack actuel", response.statusCode());
                return;
            }
            JsonObject manifest = JsonParser.parseString(response.body()).getAsJsonObject();
            Pack next = new Pack(manifest.get("url").getAsString(), HexFormat.of().parseHex(manifest.get("sha1").getAsString()),
                    manifest.has("version") ? manifest.get("version").getAsString() : "?");
            Pack before = current;
            if (before != null && java.util.Arrays.equals(before.sha1(), next.sha1())) {
                return;
            }
            current = next;
            logger.info("Pack de ressources : version {} ({})", next.version(), next.url());
            if (before != null && pushToOnline) {
                proxy.getAllPlayers().forEach(this::send);
            }
        } catch (Exception e) {
            logger.warn("Pack de ressources : manifeste injoignable ({}), on garde le pack actuel", e.getMessage());
        }
    }

    /** Première arrivée sur un serveur du réseau : le pack est proposé (imposé si required). */
    @Subscribe
    public void onConnect(ServerPostConnectEvent event) {
        if (event.getPreviousServer() == null) {
            send(event.getPlayer());
        }
    }

    private void send(Player player) {
        Pack pack = current;
        if (pack == null || bedrock(player)) {
            return;
        }
        ResourcePackInfo info = proxy.createResourcePackBuilder(pack.url())
                .setId(UUID.nameUUIDFromBytes(pack.sha1()))
                .setHash(pack.sha1())
                .setShouldForce(required)
                .setPrompt(messages.get(player, "resource-pack.prompt", TagResolver.empty()))
                .build();
        player.sendResourcePackOffer(info);
    }

    /** Joueur Bedrock (Floodgate) : pas de pack Java. */
    private static boolean bedrock(Player player) {
        return player.getUniqueId().getMostSignificantBits() == 0;
    }

    /** Pack refusé ou impossible à charger alors qu'il est imposé : le joueur est déconnecté avec une explication. */
    @Subscribe
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!required) {
            return;
        }
        String key = switch (event.getStatus()) {
            case DECLINED -> "resource-pack.declined";
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> "resource-pack.failed";
            default -> null;
        };
        if (key != null) {
            event.getPlayer().disconnect(messages.get(event.getPlayer(), key, TagResolver.empty()));
        }
    }
}
