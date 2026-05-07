package com.eventui.core.messaging;

import com.eventui.core.EventUIPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public class EventMessenger {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private static final MiniMessage MM = MiniMessage.miniMessage();


    private final EventUIPlugin plugin;
    private EventMessageConfig config;

    public EventMessenger(EventUIPlugin plugin, EventMessageConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reload(EventMessageConfig newConfig) {
        this.config = newConfig;
    }


    public void sendProgress(Player player, String description, int current, int target) {
        if (!config.isEnabled() || !config.isProgressEnabled()) return;
        send(player, config.getProgressFormat(),
                Placeholder.unparsed("description", description),
                Placeholder.unparsed("current",     String.valueOf(current)),
                Placeholder.unparsed("target",      String.valueOf(target)),
                Placeholder.unparsed("player",      player.getName()));
    }

    public void sendObjectiveCompleted(Player player, String description) {
        if (!config.isEnabled() || !config.isObjectiveCompletedEnabled()) return;
        send(player, config.getObjectiveCompletedFormat(),
                Placeholder.unparsed("description", description),
                Placeholder.unparsed("player",      player.getName()));
    }

    public void sendEventStarted(Player player, String eventName) {
        if (!config.isEnabled() || !config.isEventStartedEnabled()) return;
        send(player, config.getEventStartedFormat(),
                Placeholder.unparsed("event_name", eventName),
                Placeholder.unparsed("player",     player.getName()));
    }

    public void sendEventCompleted(Player player, String eventName) {
        if (!config.isEnabled() || !config.isEventCompletedEnabled()) return;
        send(player, config.getEventCompletedFormat(),
                Placeholder.unparsed("event_name", eventName),
                Placeholder.unparsed("player",     player.getName()));
    }

    public void sendEventFailed(Player player, String eventName) {
        if (!config.isEnabled() || !config.isEventFailedEnabled()) return;
        send(player, config.getEventFailedFormat(),
                Placeholder.unparsed("event_name", eventName),
                Placeholder.unparsed("player",     player.getName()));
    }

    public void sendEventLocked(Player player) {
        if (!config.isEnabled() || !config.isEventLockedEnabled()) return;
        send(player, config.getEventLockedFormat(),
                Placeholder.unparsed("player", player.getName()));
    }

    private void send(Player player, String format, TagResolver... resolvers) {
        try {
            Component component = MM.deserialize(format, resolvers);
            plugin.adventure().player(player).sendMessage(component);
        } catch (Exception e) {
            LOGGER.warning("EventMessenger: formato inválido → " + e.getMessage());
            player.sendMessage(MM.stripTags(format));  // fallback a texto plano
        }
    }
}
