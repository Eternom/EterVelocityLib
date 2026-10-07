package fr.eternom.eterVelocityLib;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import fr.eternom.eterVelocityLib.core.Config;
import fr.eternom.eterVelocityLib.core.Lang;
import fr.eternom.eterVelocityLib.helper.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Socle commun des plugins Eter du PROXY (Velocity), comme EterLib côté Paper : langue par défaut, palette et préfixe
 * réglés une seule fois (config.yml), langues avec textes communs, et le moteur des serveurs jetables (orchestrator).
 * Chargé au démarrage du proxy, avant les plugins qui en dépendent (@Dependency(id = "etervelocitylib")).
 */
@Plugin(id = "etervelocitylib", name = "EterVelocityLib", version = "1.1.2", authors = {"NadTum"},
        description = "Socle commun des plugins Eter du proxy")
public final class EterVelocityLib {

    private static EterVelocityLib instance;

    private final Logger logger;
    private final String defaultLocale;
    private final TagResolver palette;
    private final Component prefix;
    private final Lang common;
    private final Path dataDirectory;

    @Inject
    public EterVelocityLib(Logger logger, @DataDirectory Path dataDirectory) throws IOException {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        // Tout est lu ici, au chargement : les plugins qui en dépendent sont chargés après et peuvent s'en servir
        Config config = new Config(EterVelocityLib.class, dataDirectory);
        this.defaultLocale = config.getString("default-language", "en_us");
        List<TagResolver> colors = new ArrayList<>();
        for (String name : config.getKeys("colors")) {
            TextColor color = parseColor(config.getString("colors." + name, "white"));
            if (color != null) {
                colors.add(TagResolver.resolver(name, Tag.styling(color)));
            }
        }
        this.palette = TagResolver.resolver(colors);
        this.prefix = MiniMessage.miniMessage().deserialize(config.getString("prefix", ""), palette);
        this.common = new Lang(EterVelocityLib.class, dataDirectory, defaultLocale, logger, null);
        instance = this;
    }

    public static EterVelocityLib get() {
        return instance;
    }

    /** Messages d'un plugin proxy : son dossier lang/, avec la langue par défaut, la palette, le préfixe et les textes communs. */
    public Messages messages(Class<?> owner, Path dataDirectory, Logger pluginLogger) throws IOException {
        return new Messages(new Lang(owner, dataDirectory, defaultLocale, pluginLogger, common), palette, prefix);
    }

    /** Dossier d'EterVelocityLib : la config d'EterLib commune aux serveurs créés (EterLib-config.yml). */
    public Path dataDirectory() {
        return dataDirectory;
    }

    public Logger logger() {
        return logger;
    }

    private static TextColor parseColor(String value) {
        return value.startsWith("#") ? TextColor.fromHexString(value) : NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }
}
