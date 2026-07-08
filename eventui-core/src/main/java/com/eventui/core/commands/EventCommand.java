package com.eventui.core.commands;

import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventProgress;
import com.eventui.core.EventUIPlugin;
import com.eventui.core.bridge.PluginBridgeMessage;
import com.eventui.core.event.EventProgressImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EventCommand implements CommandExecutor {

    private static final Logger LOGGER = Logger.getLogger("EventUI Commands");

    private final EventUIPlugin plugin;

    public EventCommand(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6EventUI v2 Commands:");
            sender.sendMessage("§e/ev list §7- List all events");
            sender.sendMessage("§e/ev info <id> §7- Show event info");
            sender.sendMessage("§e/ev progress <id> §7- Show your progress");
            sender.sendMessage("§e/ev start <id> §7- Start an event");
            sender.sendMessage("§e/ev reload §7- Reload all events");
            sender.sendMessage("§e/ev open <ui_id> §7- Open custom UI");
            sender.sendMessage("§6§lTesting Commands:");
            sender.sendMessage("§e/ev reset <id|all> §7- Reset progress");
            sender.sendMessage("§e/ev abandon <id> §7- Abandon an event");
            sender.sendMessage("§e/ev complete <id> §7- Instant complete");
            sender.sendMessage("§e/ev fail <id> §7- Mark event as failed");
            sender.sendMessage("§e/ev debug <id> §7- Show debug info");
            sender.sendMessage("§e/ev setprogress <event> <obj> <amount> §7- Set progress");
            sender.sendMessage("§e/ev reloadevent <id> §7- Reload specific event");
            sender.sendMessage("§6§lSkill Tree Commands:");
            sender.sendMessage("§e/ev skill info <player> <tree_id> §7- Show skill progress");
            sender.sendMessage("§e/ev skill grant <player> <point_type> <amount> §7- Grant points");
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "progress" -> handleProgress(sender, args);
            case "start" -> handleStart(sender, args);
            case "abandon" -> handleAbandon(sender, args);
            case "reload" -> handleReload(sender);
            case "open" -> handleOpen(sender, args);
            case "reset" -> handleReset(sender, args);
            case "complete" -> handleComplete(sender, args);
            case "fail" -> handleFail(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "setprogress" -> handleSetProgress(sender, args);
            case "reloadevent" -> handleReloadEvent(sender, args);
            case "setuivar"    -> handleSetUIVar(sender, args);
            case "getuivar"    -> handleGetUIVar(sender, args);
            case "clearuivars" -> handleClearUIVars(sender, args);
            case "dumpuivars"  -> handleDumpUIVars(sender, args);
            case "skill"       -> handleSkill(sender, args);
            default -> sender.sendMessage("§cUnknown command. Use /ev for help");
        }

        return true;
    }

    private void handleList(CommandSender sender) {
        var events = plugin.getStorage().getAllEventDefinitions();

        if (events.isEmpty()) {
            sender.sendMessage("§cNo events loaded!");
            return;
        }

        sender.sendMessage("§6=== Loaded Events ===");
        events.values().forEach(event ->
                sender.sendMessage("§e" + event.getId() + " §7- §f" + event.getDisplayName())
        );
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eventui info <event_id>");
            return;
        }

        String eventId = args[1];
        Optional<EventDefinition> eventOpt = plugin.getStorage().getEventDefinition(eventId);

        if (eventOpt.isEmpty()) {
            sender.sendMessage("§cEvent not found: " + eventId);
            return;
        }

        EventDefinition event = eventOpt.get();
        sender.sendMessage("§6=== Event Info ===");
        sender.sendMessage("§eID: §f" + event.getId());
        sender.sendMessage("§eName: §f" + event.getDisplayName());
        sender.sendMessage("§eDescription: §f" + event.getDescription());
        sender.sendMessage("§eObjectives: §f" + event.getObjectives().size());

        event.getObjectives().forEach(obj ->
                sender.sendMessage("  §7- " + obj.getDescription() + " §8(" + obj.getTargetAmount() + ")")
        );
    }

    private void handleProgress(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can check progress!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eventui progress <event_id>");
            return;
        }

        String eventId = args[1];
        Optional<EventProgress> progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);

        if (progressOpt.isEmpty()) {
            sender.sendMessage("§cYou haven't started this event yet!");
            return;
        }

        EventProgress progress = progressOpt.get();
        sender.sendMessage("§6=== Your Progress ===");
        sender.sendMessage("§eEvent: §f" + eventId);
        sender.sendMessage("§eState: §f" + progress.getState());
        sender.sendMessage("§eOverall Progress: §f" + String.format("%.1f%%", progress.getOverallProgress() * 100));

        progress.getObjectivesProgress().forEach(obj ->
                sender.sendMessage("  §7- " + obj.getObjectiveId() + ": §f" +
                        obj.getCurrentAmount() + "/" + obj.getTargetAmount())
        );
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can start events!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eventui start <event_id>");
            return;
        }

        String eventId = args[1];

        try {
            var eventDefOpt = plugin.getStorage().getEventDefinition(eventId);
            if (eventDefOpt.isEmpty()) {
                sender.sendMessage("§cEvent not found: " + eventId);
                return;
            }

            var eventDef = eventDefOpt.get();
            String repeatableStr = eventDef.getMetadata().getOrDefault("repeatable", "false");
            boolean repeatable = Boolean.parseBoolean(repeatableStr);

            var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);

            if (progressOpt.isPresent()) {
                var progress = progressOpt.get();

                if (progress.getState() == com.eventui.api.event.EventState.COMPLETED) {
                    if (!repeatable) {
                        sender.sendMessage("§cYou have already completed this event!");
                        sender.sendMessage("§7This event cannot be repeated.");
                        return;
                    }

                    plugin.getStorage().removeProgress(player.getUniqueId(), eventId);
                    sender.sendMessage("§eRestarting repeatable event...");
                    notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.AVAILABLE);

                } else if (progress.getState() == com.eventui.api.event.EventState.IN_PROGRESS) {
                    sender.sendMessage("§eThis event is already in progress!");
                    sender.sendMessage("§7Use /eventui progress " + eventId + " to check your progress.");
                    return;
                }
            }
            EventProgressImpl progress = plugin.getStorage().getOrCreateProgress(player.getUniqueId(), eventId);
            progress.start();

            sender.sendMessage("§aStarted event: " + eventDef.getDisplayName());
            notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.IN_PROGRESS);
            if (!eventDef.getObjectives().isEmpty()) {
                var firstObjective = eventDef.getObjectives().getFirst();
                plugin.getEventBridge().notifyProgressUpdate(
                        player.getUniqueId(),
                        eventId,
                        firstObjective.getId(),
                        0,
                        firstObjective.getTargetAmount(),
                        firstObjective.getDescription()
                );
            }

        } catch (Exception e) {
            sender.sendMessage("§cFailed to start event: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error en /ev start para " + sender.getName(), e);
        }
    }

        private void notifyStateChange(UUID playerId, String eventId, com.eventui.api.event.EventState newState) {
        java.util.Map<String, String> payload = java.util.Map.of(
                "event_id", eventId,
                "new_state", newState.name()
        );

        com.eventui.api.bridge.BridgeMessage message = new PluginBridgeMessage(
                com.eventui.api.bridge.MessageType.EVENT_STATE_CHANGED,
                payload,
                playerId
        );

        plugin.getEventBridge().sendMessage(message);
        plugin.getPlayerDataManager().requestSave(playerId, "event state changed: " + eventId + " -> " + newState);

        LOGGER.fine("Notified state change to client: event=" + eventId + ", newState=" + newState);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("eventui.admin")) {
            sender.sendMessage("§cYou don't have permission!");
            return;
        }

        sender.sendMessage("§eReloading events...");

        try {
            plugin.reloadEvents();
            int eventCount = plugin.getStorage().getAllEventDefinitions().size();
            sender.sendMessage("§a✓ Events reloaded successfully!");
            sender.sendMessage("§7Loaded " + eventCount + " event(s)");
            int notifiedPlayers = notifyAllClientsReload();

            if (notifiedPlayers > 0) {
                sender.sendMessage("§7Notified " + notifiedPlayers + " online player(s) to refresh their UI");
            }

        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload: " + e.getMessage());
            LOGGER.severe("Failed to reload events: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error en /ev reload para " + sender.getName(), e);
        }
    }


        private void handleReset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can reset events!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eventui reset <event_id|all>");
            return;
        }

        String eventId = args[1];

        try {
            if (eventId.equalsIgnoreCase("all")) {
                var allEvents = plugin.getStorage().getAllEventDefinitions();
                int resetCount = 0;
                for (EventDefinition eventDef : allEvents.values()) {
                    var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventDef.getId());

                    if (progressOpt.isPresent()) {
                        plugin.getStorage().removeProgress(player.getUniqueId(), eventDef.getId());
                        notifyStateChange(player.getUniqueId(), eventDef.getId(), com.eventui.api.event.EventState.AVAILABLE);
                        resetCount++;
                    }
                }

                sender.sendMessage("§a✓ Reset completed!");
                sender.sendMessage("§7Cleared progress for " + resetCount + " event(s).");
                sender.sendMessage("§7Press K to refresh the events screen.");

            } else {

                var eventOpt = plugin.getStorage().getEventDefinition(eventId);
                if (eventOpt.isEmpty()) {
                    sender.sendMessage("§cEvent not found: " + eventId);
                    return;
                }
                var eventDef = eventOpt.get();

                String repeatableStr = eventDef.getMetadata().getOrDefault("repeatable", "false");
                boolean repeatable = Boolean.parseBoolean(repeatableStr);

                if (!repeatable) {
                    sender.sendMessage("§cThis event is not repeatable and cannot be reset.");
                    sender.sendMessage("§7Use §e/ev reset all §7to force-reset everything (testing only).");
                    return;
                }

                var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);
                if (progressOpt.isEmpty()) {
                    sender.sendMessage("§7You haven't started this event yet.");
                    return;
                }

                plugin.getStorage().removeProgress(player.getUniqueId(), eventId);

                sender.sendMessage("§a✓ Event reset: " + eventDef.getDisplayName());
                sender.sendMessage("§7Progress cleared. You can start it again.");

                notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.AVAILABLE);
            }

        } catch (Exception e) {
            sender.sendMessage("§cFailed to reset: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error en /ev reset para " + sender.getName(), e);
        }
    }

        private void handleComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eventui complete <event_id>");
            return;
        }

        String eventId = args[1];

        try {
            var eventDefOpt = plugin.getStorage().getEventDefinition(eventId);
            if (eventDefOpt.isEmpty()) {
                sender.sendMessage("§cEvent not found: " + eventId);
                return;
            }

            var eventDef = eventDefOpt.get();
            var progress = plugin.getStorage().getOrCreateProgress(player.getUniqueId(), eventId);
            if (progress.getState() == com.eventui.api.event.EventState.AVAILABLE) {
                progress.start();
            }
            for (var objective : eventDef.getObjectives()) {
                var objProgress = progress.getObjectiveProgress(objective.getId());
                if (objProgress != null) {
                    objProgress.setProgress(objective.getTargetAmount());
                }
            }

            progress.complete();

            sender.sendMessage("§a✓ Event completed: " + eventDef.getDisplayName());
            if (sender instanceof Player) {
                plugin.getRewardManager().giveRewards((Player) sender, eventDef);
            }

            notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.COMPLETED);

            if (!eventDef.getObjectives().isEmpty()) {
                var lastObjective = eventDef.getObjectives().getLast();
                plugin.getEventBridge().notifyProgressUpdate(
                        player.getUniqueId(),
                        eventId,
                        lastObjective.getId(),
                        lastObjective.getTargetAmount(),
                        lastObjective.getTargetAmount(),
                        lastObjective.getDescription()
                );
            }

        } catch (Exception e) {
            sender.sendMessage("§cFailed to complete: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error en /ev complete para " + sender.getName(), e);
        }
    }

        private void handleDebug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /eventui debug <event_id>");
            return;
        }

        String eventId = args[1];

        var eventDefOpt = plugin.getStorage().getEventDefinition(eventId);
        if (eventDefOpt.isEmpty()) {
            sender.sendMessage("§cEvent not found: " + eventId);
            return;
        }

        var eventDef = eventDefOpt.get();

        sender.sendMessage("§6═══ DEBUG INFO ═══");
        sender.sendMessage("§eEvent: §f" + eventDef.getDisplayName());
        sender.sendMessage("§eID: §7" + eventId);
        sender.sendMessage("§ePlayer: §7" + player.getName());

        var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);

        if (progressOpt.isEmpty()) {
            sender.sendMessage("§eState: §7AVAILABLE (not started)");
            sender.sendMessage("§eProgress: §70%");
        } else {
            var progress = progressOpt.get();
            sender.sendMessage("§eState: §f" + progress.getState());
            sender.sendMessage("§eOverall Progress: §a" + String.format("%.1f%%", progress.getOverallProgress() * 100));
            sender.sendMessage("§eStarted At: §7" + (progress.getStartedAt() > 0 ? new java.util.Date(progress.getStartedAt()) : "N/A"));

            sender.sendMessage("§eObjectives:");
            for (var objDef : eventDef.getObjectives()) {
                var objProgress = progress.getObjectivesProgress().stream()
                        .filter(op -> op.getObjectiveId().equals(objDef.getId()))
                        .findFirst()
                        .orElse(null);

                if (objProgress != null) {
                    String status = objProgress.isCompleted() ? "§a✓" : "§7○";
                    sender.sendMessage(String.format("  %s %s: §f%d/%d §7(%s)",
                            status,
                            objDef.getDescription(),
                            objProgress.getCurrentAmount(),
                            objProgress.getTargetAmount(),
                            objProgress.isCompleted() ? "COMPLETED" : "PENDING"
                    ));
                }
            }
        }

        sender.sendMessage("§6═══════════════");
    }

        private void handleSetProgress(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return;
        }

        if (args.length < 4) {
            sender.sendMessage("§cUsage: /ev setprogress <event_id> <objective_id> <amount>");
            return;
        }

        String eventId = args[1];
        String objectiveId = args[2];
        int amount;

        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a number!");
            return;
        }

        try {
            var eventDefOpt = plugin.getStorage().getEventDefinition(eventId);
            if (eventDefOpt.isEmpty()) {
                sender.sendMessage("§cEvent not found: " + eventId);
                return;
            }

            var eventDef = eventDefOpt.get();
            var objectiveOpt = eventDef.getObjectives().stream()
                    .filter(obj -> obj.getId().equals(objectiveId))
                    .findFirst();
            if (objectiveOpt.isEmpty()) {
                sender.sendMessage("§cObjective not found: " + objectiveId);
                return;
            }

            var objective = objectiveOpt.get();
            var progress = plugin.getStorage().getOrCreateProgress(player.getUniqueId(), eventId);

            if (progress.getState() == com.eventui.api.event.EventState.AVAILABLE) {
                progress.start();
                notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.IN_PROGRESS);
            }

            var objProgress = progress.getObjectiveProgress(objectiveId);
            if (objProgress != null) {
                objProgress.setProgress(amount);

                sender.sendMessage("§a✓ Progress updated!");
                sender.sendMessage(String.format("§7%s: §f%d/%d",
                        objective.getDescription(),
                        amount,
                        objective.getTargetAmount()));

                plugin.getEventBridge().notifyProgressUpdate(
                        player.getUniqueId(),
                        eventId,
                        objectiveId,
                        amount,
                        objective.getTargetAmount(),
                        objective.getDescription()
                );
                plugin.getPlayerDataManager().requestSave(
                        player.getUniqueId(),
                        "progress manually set: " + eventId + "/" + objectiveId
                );

                if (progress.areAllObjectivesCompleted()) {
                    progress.complete();
                    sender.sendMessage("§6§l✓ EVENT COMPLETED!");
                    notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.COMPLETED);
                }
            }

        } catch (Exception e) {
            sender.sendMessage("§cFailed to set progress: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error en /ev setprogress para " + sender.getName(), e);
        }
    }

        private void handleReloadEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ev reloadevent <event_id>");
            return;
        }

        String eventId = args[1];

        try {
            var currentEventOpt = plugin.getStorage().getEventDefinition(eventId);
            if (currentEventOpt.isEmpty()) {
                sender.sendMessage("§cEvent not found: " + eventId);
                sender.sendMessage("§7Use /ev reload to load all events.");
                return;
            }

            java.io.File eventsDir = new java.io.File(plugin.getDataFolder(), "events");
            java.io.File[] files = eventsDir.listFiles((dir, name) -> (name.endsWith(".yml") || name.endsWith(".yaml")) && name.contains(eventId));

            if (files == null || files.length == 0) {
                sender.sendMessage("§cCouldn't find YAML file for event: " + eventId);
                return;
            }

            var newEventDef = plugin.getConfigLoader().loadEventFromFile(files[0]);

            plugin.getStorage().registerEvent(newEventDef);

            sender.sendMessage("§a✓ Event reloaded: " + newEventDef.getDisplayName());
            sender.sendMessage("§7File: " + files[0].getName());
            sender.sendMessage("§7Objectives: " + newEventDef.getObjectives().size());

        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload event: " + e.getMessage());
            sender.sendMessage("§7Check console for details.");
            LOGGER.log(Level.SEVERE, "Error en /ev reload para " + sender.getName(), e);
        }
    }

        private int notifyAllClientsReload() {
        Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();

        if (onlinePlayers.isEmpty()) {
            return 0;
        }

        for (Player player : onlinePlayers) {
            java.util.Map<String, String> payload = java.util.Map.of(
                    "reason", "server_reload",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            );

            com.eventui.api.bridge.BridgeMessage message =
                    new PluginBridgeMessage(
                            com.eventui.api.bridge.MessageType.EVENT_RELOAD_NOTIFICATION,
                            payload,
                            player.getUniqueId()
                    );

            plugin.getEventBridge().sendMessage(message);
        }

        LOGGER.info("✓ Sent reload notification to " + onlinePlayers.size() + " player(s)");

        return onlinePlayers.size();
    }
    private void handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can open UIs!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ev open <ui_id>");
            sender.sendMessage("§7Available UIs:");
            plugin.getUIConfigs().keySet().forEach(uiId ->
                    sender.sendMessage("  §e" + uiId)
            );
            return;
        }

        String uiId = args[1];
        if (!plugin.getUIConfigs().containsKey(uiId)) {
            sender.sendMessage("§cUI not found: " + uiId);
            sender.sendMessage("§7Use §e/ev open §7(without ID) to list available UIs.");
            return;
        }

        try {
            java.util.Map<String, String> payload = java.util.Map.of(
                    "screen_id", uiId
            );

            com.eventui.api.bridge.BridgeMessage message = new com.eventui.core.bridge.PluginBridgeMessage(
                    com.eventui.api.bridge.MessageType.OPEN_UI_COMMAND,
                    payload,
                    player.getUniqueId()
            );

            plugin.getEventBridge().sendMessage(message);
            player.sendMessage("§aOpening UI: §e" + uiId);

        } catch (Exception e) {
            sender.sendMessage("§cFailed to open UI: " + e.getMessage());
            LOGGER.severe("Failed to open UI " + uiId + " for " + player.getName());
            LOGGER.log(java.util.logging.Level.SEVERE, "Error en /ev open para " + sender.getName(), e);
        }
    }

        private void handleFail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ev fail <event_id>");
            return;
        }

        String eventId = args[1];

        try {
            var eventDefOpt = plugin.getStorage().getEventDefinition(eventId);
            if (eventDefOpt.isEmpty()) {
                sender.sendMessage("§cEvent not found: " + eventId);
                return;
            }

            var eventDef = eventDefOpt.get();
            var progress = plugin.getStorage().getOrCreateProgress(player.getUniqueId(), eventId);

            if (progress.getState() == com.eventui.api.event.EventState.AVAILABLE) {
                progress.start();
            }

            progress.fail();

            plugin.getObjectiveTracker().unregisterActiveEvent(player.getUniqueId(), eventId);

            sender.sendMessage("§c✘ Event FAILED: §f" + eventDef.getDisplayName());
            sender.sendMessage("§7This event is now marked as failed.");

            notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.FAILED);

        } catch (Exception e) {
            sender.sendMessage("§cFailed to fail event: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error en /ev fail para " + sender.getName(), e);

        }
    }

        private void handleAbandon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can abandon events!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ev abandon <event_id>");
            return;
        }

        String eventId = args[1];

        var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);
        if (progressOpt.isEmpty()) {
            sender.sendMessage("§7You haven't started this event.");
            return;
        }

        var progress = progressOpt.get();
        if (progress.getState() != com.eventui.api.event.EventState.IN_PROGRESS) {
            sender.sendMessage("§cThis event is not in progress.");
            return;
        }

        var eventOpt = plugin.getStorage().getEventDefinition(eventId);
        if (eventOpt.isEmpty()) {
            sender.sendMessage("§cEvent not found: " + eventId);
            return;
        }

        var eventDef = eventOpt.get();

        String repeatableStr = eventDef.getMetadata().getOrDefault("repeatable", "false");
        boolean repeatable = Boolean.parseBoolean(repeatableStr);

        if (repeatable) {
            plugin.getStorage().removeProgress(player.getUniqueId(), eventId);
            plugin.getObjectiveTracker().unregisterActiveEvent(player.getUniqueId(), eventId);

            sender.sendMessage("§eEvent abandoned: §f" + eventDef.getDisplayName());
            sender.sendMessage("§aYou can start it again.");

            notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.AVAILABLE);
        } else {
            progress.fail();
            plugin.getObjectiveTracker().unregisterActiveEvent(player.getUniqueId(), eventId);

            sender.sendMessage("§c✘ Event FAILED: §f" + eventDef.getDisplayName());
            sender.sendMessage("§7This event is not repeatable and cannot be restarted.");

            notifyStateChange(player.getUniqueId(), eventId, com.eventui.api.event.EventState.FAILED);
        }
    }
    private void handleSetUIVar(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUso: /eventui setuivar <jugador|@a|@p|@r> <key> <valor>");
            return;
        }

        List<Player> targets = resolvePlayers(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage("§cNo se encontraron jugadores para: " + args[1]);
            return;
        }

        String key   = args[2];
        String value = args[3];

        for (Player target : targets) {
            plugin.getUIStateManager().setVariable(target.getUniqueId(), key, value);
        }

        sender.sendMessage("§a✓ UIVar §e" + key + " §7= §f" + value +
                " §7→ §a" + targets.size() + " jugador(es)");
    }


    private void handleGetUIVar(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUso: /eventui getuivar <jugador> <key>");
            return;
        }

        var target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJugador no encontrado: " + args[1]);
            return;
        }

        String value = plugin.getUIStateManager()
                .getVariable(target.getUniqueId(), args[2]);

        if (value == null) {
            sender.sendMessage("§7[" + target.getName() + "] §e" + args[2] + " §7= §cnull (no definida)");
        } else {
            sender.sendMessage("§7[" + target.getName() + "] §e" + args[2] + " §7= §f" + value);
        }
    }

    private void handleClearUIVars(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /eventui clearuivars <jugador|@a|@p|@r>");
            return;
        }

        List<Player> targets = resolvePlayers(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage("§cNo se encontraron jugadores para: " + args[1]);
            return;
        }

        for (Player target : targets) {
            plugin.getUIStateManager().clearVariables(target.getUniqueId());
        }

        sender.sendMessage("§a✓ UI state cleared for " + targets.size() + " jugador(es)");
    }


    private void handleDumpUIVars(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /eventui dumpuivars <jugador>");
            return;
        }

        var target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJugador no encontrado: " + args[1]);
            return;
        }

        var vars = plugin.getUIStateManager().getAllVariables(target.getUniqueId());

        sender.sendMessage("§6UIState de " + target.getName() + " (" + vars.size() + " vars):");
        if (vars.isEmpty()) {
            sender.sendMessage("  §7(vacío)");
        } else {
            vars.forEach((k, v) -> sender.sendMessage("  §e" + k + " §7= §f" + v));
        }
    }
    private List<Player> resolvePlayers(CommandSender sender, String selector) {
        List<Player> result = switch (selector.toLowerCase()) {
            case "@a" -> new ArrayList<>(plugin.getServer().getOnlinePlayers());

            case "@r" -> {
                var online = new ArrayList<Player>(plugin.getServer().getOnlinePlayers());
                if (online.isEmpty()) yield new ArrayList<>();
                yield new ArrayList<>(List.of(online.get(new Random().nextInt(online.size()))));
            }

            case "@p" -> {
                if (sender instanceof Player p) {
                    yield plugin.getServer().getOnlinePlayers().stream()
                            .min(Comparator.comparingDouble(pl ->
                                    pl.getLocation().distanceSquared(p.getLocation())))
                            .map(pl -> new ArrayList<Player>(List.of(pl)))
                            .orElseGet(ArrayList::new);
                }
                yield plugin.getServer().getOnlinePlayers().stream()
                        .findFirst()
                        .map(pl -> new ArrayList<Player>(List.of(pl)))
                        .orElseGet(ArrayList::new);
            }

            default -> {
                var p = plugin.getServer().getPlayer(selector);
                yield p != null ? new ArrayList<>(List.of(p)) : new ArrayList<>();
            }
        };

        Collections.reverse(result);
        return result;
    }

    private void handleSkill(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ev skill <info|grant> [args...]");
            sender.sendMessage("§e/ev skill info <player> <tree_id> §7- Show skill progress");
            sender.sendMessage("§e/ev skill grant <player> <point_type> <amount> §7- Grant points");
            return;
        }

        String subcommand = args[1].toLowerCase();
        switch (subcommand) {
            case "info" -> handleSkillInfo(sender, args);
            case "grant" -> handleSkillGrant(sender, args);
            default -> sender.sendMessage("§cUnknown subcommand. Use /ev skill for help");
        }
    }

    private void handleSkillInfo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /ev skill info <player> <tree_id>");
            return;
        }

        var target = plugin.getServer().getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[2]);
            return;
        }

        String treeId = args[3];
        var treeDef = plugin.getSkillTreeStorage().getSkillTree(treeId);
        if (treeDef.isEmpty()) {
            sender.sendMessage("§cSkill tree not found: " + treeId);
            return;
        }

        var skillProgress = plugin.getSkillProgressStorage().getProgress(target.getUniqueId());
        var tree = treeDef.get();

        sender.sendMessage("§6═══ SKILL TREE: " + tree.getDisplayName() + " ═══");
        sender.sendMessage("§ePlayer: §f" + target.getName());
        sender.sendMessage("§ePoint Type: §f" + tree.getPointType());

        int available = skillProgress.map(p -> p.getAvailablePoints(tree.getPointType())).orElse(0);
        int total = skillProgress.map(p -> p.getTotalEarnedPoints(tree.getPointType())).orElse(0);

        sender.sendMessage("§ePoints: §a" + available + " §7/ §f" + total + " earned");
        sender.sendMessage("§eNodes:");

        for (var node : tree.getNodes()) {
            int level = skillProgress.map(p -> p.getNodeLevel(treeId, node.getId())).orElse(0);
            String status = level == 0 ? "§7[LOCKED]" : (level >= node.getMaxLevel() ? "§a[MAXED]" : "§e[" + level + "/" + node.getMaxLevel() + "]");
            sender.sendMessage("  §f" + node.getDisplayName() + " " + status);
        }

        sender.sendMessage("§6═══════════════════");
    }

    private void handleSkillGrant(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /ev skill grant <player> <point_type> <amount>");
            return;
        }

        var target = plugin.getServer().getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[2]);
            return;
        }

        String pointType = args[3];
        int amount;

        try {
            amount = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a number!");
            return;
        }

        if (amount <= 0) {
            sender.sendMessage("§cAmount must be positive!");
            return;
        }

        var skillProgress = plugin.getSkillProgressStorage().getOrCreateProgress(target.getUniqueId());
        skillProgress.addEarnedPoints(pointType, amount);

        plugin.getPlayerDataManager().requestSave(target.getUniqueId(), "skill grant: " + pointType + " x" + amount);

        sender.sendMessage("§a✓ Granted §e" + amount + " §a" + pointType + " to §f" + target.getName());
        target.sendMessage("§a✓ You received §e" + amount + " §a" + pointType + "!");
    }
}
