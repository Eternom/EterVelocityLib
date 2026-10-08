package fr.eternom.eterVelocityLib.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Accès à la base du réseau depuis le proxy (la même que les serveurs Paper ; accès lus dans la config d'EterLib).
 * Pilote MariaDB téléchargé au premier démarrage dans libs/ et ajouté au proxy : Velocity n'a pas le chargement de
 * bibliothèques de Paper. Une connexion par opération (peu d'appels côté proxy), paramètres toujours liés (jamais
 * concaténés). Bloquant : jamais sur un fil d'événement qui attend.
 */
public final class Sql {

    private static final String DRIVER_VERSION = "3.5.6";

    private final Driver driver;
    private final String jdbcUrl;
    private final Properties credentials = new Properties();

    private Sql(Driver driver, String jdbcUrl, String username, String password) {
        this.driver = driver;
        this.jdbcUrl = jdbcUrl;
        credentials.setProperty("user", username);
        credentials.setProperty("password", password);
    }

    /** eterLib : la config d'EterLib à plat (database.host, database.port...), lue avec YamlFiles. */
    public static Sql open(Map<String, Object> eterLib, Path libs, Consumer<Path> addToClasspath) throws IOException {
        String url = "jdbc:mariadb://" + eterLib.getOrDefault("database.host", "localhost") + ":"
                + eterLib.getOrDefault("database.port", 3306) + "/" + eterLib.getOrDefault("database.name", "eternom");
        return new Sql(loadDriver(libs, addToClasspath), url, String.valueOf(eterLib.getOrDefault("database.username", "root")),
                String.valueOf(eterLib.getOrDefault("database.password", "")));
    }

    public Connection connect() throws SQLException {
        return driver.connect(jdbcUrl, credentials);
    }

    /** Lignes (colonne en minuscules -> valeur). */
    public List<Map<String, Object>> query(String sql, Object... values) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement statement = prepare(connection, sql, values);
             ResultSet result = statement.executeQuery()) {
            ResultSetMetaData meta = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), result.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    public int execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = prepare(connection, sql, values)) {
            return statement.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... values) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i] instanceof java.util.UUID uuid ? uuid.toString() : values[i]);
        }
        return statement;
    }

    /** libs/mariadb-java-client-<version>.jar, téléchargé depuis Maven Central s'il manque, puis ajouté au proxy. */
    private static Driver loadDriver(Path libs, Consumer<Path> addToClasspath) throws IOException {
        Path jar = libs.resolve("mariadb-java-client-" + DRIVER_VERSION + ".jar");
        if (!Files.exists(jar)) {
            Files.createDirectories(libs);
            URI source = URI.create("https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/" + DRIVER_VERSION
                    + "/mariadb-java-client-" + DRIVER_VERSION + ".jar");
            Path temporary = libs.resolve(jar.getFileName() + ".part");
            try (InputStream in = source.toURL().openStream()) {
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        addToClasspath.accept(jar);
        try {
            return (Driver) Class.forName("org.mariadb.jdbc.Driver", true, Sql.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IOException("Pilote MariaDB illisible : " + jar, e);
        }
    }
}
