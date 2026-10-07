package fr.eternom.eterVelocityLib.orchestrator;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import fr.eternom.eterVelocityLib.helper.Messages;
import fr.eternom.eterVelocityLib.orchestrator.ServerStore.Row;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Commande d'administration d'une famille de serveurs (ex : /eterlobby, /eterresourcepool), réservée à permission :
 * list (serveurs gérés, état, joueurs, à jour ou non), status (réglages et version voulue), create (un serveur de plus
 * tout de suite), drain <serveur> (le vider puis le supprimer). Textes communs : lang/ d'EterVelocityLib.
 */
public class PoolCommand implements SimpleCommand {

    private final ServerPool pool;
    private final Messages messages;
    private final String label;
    private final String permission;

    public PoolCommand(ServerPool pool, Messages messages, String label, String permission) {
        this.pool = pool;
        this.messages = messages;
        this.label = label;
        this.permission = permission;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!pool.isReady() && !action.equals("status")) {
            messages.send(source, "orchestrator.not-ready", TagResolver.empty());
            return;
        }
        switch (action) {
            case "list" -> list(source);
            case "status" -> status(source);
            case "create" -> {
                pool.requestCreate();
                messages.send(source, "orchestrator.creating", TagResolver.empty());
            }
            case "drain" -> {
                String name = args.length > 1 ? args[1] : "";
                messages.send(source, pool.requestDrain(name) ? "orchestrator.draining" : "orchestrator.unknown",
                        Placeholder.unparsed("server", name));
            }
            default -> messages.send(source, "orchestrator.usage", Placeholder.unparsed("command", label));
        }
    }

    private void list(CommandSource source) {
        List<Row> rows = pool.rows();
        messages.send(source, rows.isEmpty() ? "orchestrator.list-empty" : "orchestrator.list-header",
                Placeholder.unparsed("count", String.valueOf(rows.size())));
        for (Row row : rows) {
            source.sendMessage(messages.get(source, "orchestrator.list-line", TagResolver.resolver(
                    Placeholder.unparsed("server", row.name()),
                    Placeholder.unparsed("state", row.state().name().toLowerCase(Locale.ROOT)),
                    Placeholder.unparsed("players", String.valueOf(pool.players(row.name()))),
                    Placeholder.unparsed("port", String.valueOf(row.port())),
                    Placeholder.component("version", messages.get(source, row.version().equals(pool.version())
                            ? "orchestrator.up-to-date" : "orchestrator.outdated", TagResolver.empty())))));
        }
    }

    private void status(CommandSource source) {
        ServerPool.Settings settings = pool.settings();
        messages.send(source, "orchestrator.status", TagResolver.resolver(
                Placeholder.unparsed("family", pool.family()),
                Placeholder.component("mode", messages.get(source, settings.dryRun() ? "orchestrator.mode-dry-run"
                        : "orchestrator.mode-real", TagResolver.empty())),
                Placeholder.unparsed("ready", String.valueOf(pool.isReady())),
                Placeholder.unparsed("minimum", String.valueOf(settings.minimum())),
                Placeholder.unparsed("maximum", String.valueOf(settings.maximum())),
                Placeholder.unparsed("capacity", String.valueOf(settings.capacity())),
                Placeholder.unparsed("version", String.valueOf(pool.version()))));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String start = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Stream.of("list", "status", "create", "drain").filter(action -> action.startsWith(start)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("drain")) {
            return pool.activeNames().stream().filter(name -> name.startsWith(args[1])).toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(permission);
    }
}
