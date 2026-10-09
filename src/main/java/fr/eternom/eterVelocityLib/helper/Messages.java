package fr.eternom.eterVelocityLib.helper;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import fr.eternom.eterVelocityLib.core.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;

/**
 * MiniMessage avec la palette et le préfixe communs du réseau (config d'EterVelocityLib), dans la langue de chaque
 * joueur. Obtenu par EterVelocityLib.get().messages(...).
 */
public class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    /** Codes § avec les couleurs exactes (hex) que le client accepte dans le MOTD. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final Lang lang;
    private final TagResolver palette;
    private final Component prefix;

    public Messages(Lang lang, TagResolver palette, Component prefix) {
        this.lang = lang;
        this.palette = palette;
        this.prefix = prefix;
    }

    /**
     * Texte de key dans la langue du destinataire, mis en forme. Langue par défaut pour la console, et pour un joueur
     * qui vient d'arriver : son client n'a pas encore envoyé sa langue (getEffectiveLocale() vaut alors null).
     */
    public Component get(CommandSource source, String key, TagResolver tags) {
        Locale clientLocale = source instanceof Player player ? player.getEffectiveLocale() : null;
        String locale = clientLocale == null ? lang.getDefaultLocale() : clientLocale.toString().toLowerCase(Locale.ROOT);
        String raw = lang.get(locale, key);
        return raw == null ? Component.text(key, NamedTextColor.RED) : render(raw, tags);
    }

    /** Message de chat, précédé du préfixe commun. */
    public void send(CommandSource source, String key, TagResolver tags) {
        source.sendMessage(prefix.append(get(source, key, tags)));
    }

    /** Texte MiniMessage mis en forme avec la palette. */
    public Component render(String raw, TagResolver tags) {
        return MINI_MESSAGE.deserialize(raw, palette, tags);
    }

    /**
     * Version « codes § » d'un texte : pour les endroits où Minecraft n'accepte qu'un texte simple (lignes au survol
     * du nombre de joueurs, texte de version du MOTD).
     */
    public String legacy(String raw, TagResolver tags) {
        return LEGACY.serialize(render(raw, tags));
    }
}
