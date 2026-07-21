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

    public void sendNodeLeveledUp(Player player, String nodeName, String treeName,
                                  int currentLevel, int maxLevel, String pointType) {
        if (!config.isEnabled() || !config.isSkillNodeLeveledUpEnabled()) return;
        send(player, config.getSkillNodeLeveledUpFormat(),
                Placeholder.unparsed("node_name", nodeName),
                Placeholder.unparsed("tree_name", treeName),
                Placeholder.unparsed("current_level", String.valueOf(currentLevel)),
                Placeholder.unparsed("max_level", String.valueOf(maxLevel)),
                Placeholder.unparsed("point_type", pointType),
                Placeholder.unparsed("player", player.getName()));
    }

    public void sendNodeMaxed(Player player, String nodeName, String treeName) {
        if (!config.isEnabled() || !config.isSkillNodeMaxedEnabled()) return;
        send(player, config.getSkillNodeMaxedFormat(),
                Placeholder.unparsed("node_name", nodeName),
                Placeholder.unparsed("tree_name", treeName),
                Placeholder.unparsed("player", player.getName()));
    }

    public void sendPointsGranted(Player player, int amount, String pointType) {
        if (!config.isEnabled() || !config.isSkillPointsGrantedEnabled()) return;
        send(player, config.getSkillPointsGrantedFormat(),
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("point_type", pointType),
                Placeholder.unparsed("player", player.getName()));
    }

    public void sendInsufficientPoints(Player player, String nodeName, int cost,
                                       int available, String pointType) {
        if (!config.isEnabled() || !config.isSkillInsufficientPointsEnabled()) return;
        send(player, config.getSkillInsufficientPointsFormat(),
                Placeholder.unparsed("node_name", nodeName),
                Placeholder.unparsed("cost", String.valueOf(cost)),
                Placeholder.unparsed("available", String.valueOf(available)),
                Placeholder.unparsed("point_type", pointType),
                Placeholder.unparsed("player", player.getName()));
    }

    public void sendRequirementsNotMet(Player player, String nodeName, String treeName) {
        if (!config.isEnabled() || !config.isSkillRequirementsNotMetEnabled()) return;
        send(player, config.getSkillRequirementsNotMetFormat(),
                Placeholder.unparsed("node_name", nodeName),
                Placeholder.unparsed("tree_name", treeName),
                Placeholder.unparsed("player", player.getName()));
    }

    public void sendAlreadyMaxed(Player player, String nodeName, int maxLevel) {
        if (!config.isEnabled() || !config.isSkillAlreadyMaxedEnabled()) return;
        send(player, config.getSkillAlreadyMaxedFormat(),
                Placeholder.unparsed("node_name", nodeName),
                Placeholder.unparsed("max_level", String.valueOf(maxLevel)),
                Placeholder.unparsed("player", player.getName()));
    }

    public void sendExclusiveBranchBlocked(Player player, String groupName) {
        if (!config.isEnabled() || !config.isSkillExclusiveBranchBlockedEnabled()) return;
        send(player, config.getSkillExclusiveBranchBlockedFormat(),
                Placeholder.unparsed("group_name", groupName),
                Placeholder.unparsed("player", player.getName()));
    }

    public void sendPointSourceCooldown(Player player, String source, long remainingSeconds) {
        if (!config.isEnabled() || !config.isPointSourceCooldownEnabled()) return;
        send(player, config.getPointSourceCooldownFormat(),
                Placeholder.unparsed("source", source),
                Placeholder.unparsed("remaining", String.valueOf(remainingSeconds)),
                Placeholder.unparsed("player", player.getName()));
    }

    private void send(Player player, String format, TagResolver... resolvers) {
        try {
            Component component = MM.deserialize(format, resolvers);
            plugin.adventure().player(player).sendMessage(component);
        } catch (Exception e) {
            LOGGER.warning("EventMessenger: formato inválido → " + e.getMessage());
            player.sendMessage(MM.stripTags(format));  
        }
    }
}
