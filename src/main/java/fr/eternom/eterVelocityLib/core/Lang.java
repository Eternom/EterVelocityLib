package fr.eternom.eterVelocityLib.core;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Fichiers de langue d'un plugin proxy : plugins/<plugin>/lang/<locale>.yml (codes Minecraft : en_us, fr_fr...),
 * comme EterLib côté Paper. Ceux du jar sont copiés au premier démarrage et servent de valeurs par défaut ; on ajoute
 * une langue en déposant un fichier. Recherche : langue exacte, même langue d'une autre région (fr_ca -> fr_fr),
 * langue par défaut, puis textes communs d'EterVelocityLib (common).
 */
public class Lang {

    private static final String[] BUNDLED = {"en_us", "fr_fr"};

    private final Map<String, Map<String, Object>> languages = new HashMap<>();
    private final String defaultLocale;
    private final Lang common;

    /** @param common langues d'EterVelocityLib (textes communs), null pour EterVelocityLib lui-même */
    public Lang(Class<?> owner, Path dataDirectory, String defaultLocale, Logger logger, Lang common) throws IOException {
        this.defaultLocale = defaultLocale.toLowerCase(Locale.ROOT);
        this.common = common;
        for (String locale : BUNDLED) {
            if (owner.getClassLoader().getResource("lang/" + locale + ".yml") != null) {
                YamlFiles.saveDefault(owner, dataDirectory, "lang/" + locale + ".yml");
            }
        }
        Path folder = dataDirectory.resolve("lang");
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                    String locale = file.getFileName().toString().replace(".yml", "").toLowerCase(Locale.ROOT);
                    Map<String, Object> values = new HashMap<>(YamlFiles.loadResource(owner, "lang/" + locale + ".yml"));
                    values.putAll(YamlFiles.load(file));
                    languages.put(locale, values);
                }
            }
        }
        logger.info("Langues : {} (défaut : {})", String.join(", ", languages.keySet()), this.defaultLocale);
    }

    /** Texte brut (MiniMessage) ; une liste YAML est rendue en lignes séparées par \n. null si absent partout. */
    public String get(String locale, String key) {
        Object value = find(locale, key);
        if (value == null) {
            String language = locale.split("_")[0] + "_";
            value = languages.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(language))
                    .map(entry -> entry.getValue().get(key))
                    .filter(found -> found != null)
                    .findFirst()
                    .orElse(null);
        }
        if (value == null) {
            value = find(defaultLocale, key);
        }
        if (value instanceof Iterable<?> lines) {
            StringBuilder joined = new StringBuilder();
            lines.forEach(line -> joined.append(joined.isEmpty() ? "" : "\n").append(line));
            return joined.toString();
        }
        if (value == null && common != null) {
            return common.get(locale, key);
        }
        return value == null ? null : value.toString();
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    private Object find(String locale, String key) {
        Map<String, Object> values = languages.get(locale);
        return values == null ? null : values.get(key);
    }
}
