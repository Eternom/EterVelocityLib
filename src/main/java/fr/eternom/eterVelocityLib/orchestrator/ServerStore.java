package fr.eternom.eterVelocityLib.orchestrator;

import fr.eternom.eterVelocityLib.core.Sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * La table <famille>_servers, SEULE source de vérité des serveurs créés par l'orchestrateur de cette famille : un serveur du panel qui n'y
 * figure pas n'est jamais supprimé (le panel héberge aussi les serveurs des clients). Même base que le réseau (accès lus
 * dans le modèle de config d'EterLib). Une connexion par opération (Sql) :
 * peu d'appels, pas de pool.
 */
final class ServerStore {

    enum State { CREATING, ACTIVE, DRAINING }

    /** Un lobby géré. panelId est null tant que le panel n'a pas répondu à la création. */
    record Row(String name, Integer panelId, String identifier, String externalId, String version, State state,
               String host, int port, long stateSince) {

        Row with(State newState) {
            return new Row(name, panelId, identifier, externalId, version, newState, host, port, System.currentTimeMillis());
        }

        /** Créé par le panel : son identifiant, et l'adresse où il l'a déployé. */
        Row withPanel(int id, String newIdentifier, String newHost, int newPort) {
            return new Row(name, id, newIdentifier, externalId, version, state, newHost, newPort, stateSince);
        }
    }

    /** Table des serveurs de CETTE famille (eterlobby_servers, eterresource_servers...). */
    private final String table;
    /** Tables où un serveur supprimé laisse une trace par son nom (eter_servers, eterhub_lobbies...). */
    private final List<String> traces;

    private final Sql sql;

    ServerStore(String table, List<String> traces, Sql sql) throws SQLException {
        this.table = table;
        this.traces = List.copyOf(traces);
        this.sql = sql;
        try (Connection connection = connect(); PreparedStatement create = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + table + " (name VARCHAR(32) PRIMARY KEY, panel_id INT NULL,"
                        + " identifier VARCHAR(16) NULL, external_id VARCHAR(64) NOT NULL, version VARCHAR(1024) NOT NULL,"
                        + " state VARCHAR(16) NOT NULL, host VARCHAR(255) NOT NULL, port INT NOT NULL, state_since BIGINT NOT NULL)"
                        + " DEFAULT CHARSET = utf8mb4")) {
            create.executeUpdate();
        }
    }

    List<Row> all() throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement select = connection.prepareStatement("SELECT * FROM " + table);
             ResultSet result = select.executeQuery()) {
            while (result.next()) {
                int panelId = result.getInt("panel_id");
                rows.add(new Row(result.getString("name"), result.wasNull() ? null : panelId, result.getString("identifier"),
                        result.getString("external_id"), result.getString("version"), State.valueOf(result.getString("state")),
                        result.getString("host"), result.getInt("port"), result.getLong("state_since")));
            }
        }
        return rows;
    }

    void save(Row row) throws SQLException {
        execute("REPLACE INTO " + table + " (name, panel_id, identifier, external_id, version, state, host, port, state_since)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", row.name(), row.panelId(), row.identifier(), row.externalId(),
                row.version(), row.state().name(), row.host(), row.port(), row.stateSince());
    }

    /**
     * Serveur supprimé : sa ligne, et ses traces dans les tables des plugins (traces), pour qu'il ne reste pas
     * « hors ligne » dans les menus. Une table absente est ignorée.
     */
    void delete(String name) throws SQLException {
        execute("DELETE FROM " + table + " WHERE name = ?", name);
        for (String trace : traces) {
            try {
                execute("DELETE FROM " + trace + " WHERE name = ?", name);
            } catch (SQLException missingTable) {
                // plugin pas encore installé sur le réseau
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        return sql.connect();
    }
}
