package com.eventui.core.commands;

import com.eventui.api.objective.ObjectiveDefinition;
import com.eventui.core.EventUIPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EventCommandTabCompleter implements TabCompleter {

    private final EventUIPlugin plugin;

    public EventCommandTabCompleter(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList(
                    "list", "info", "progress", "start", "reload", "open",
                    "reset", "complete", "fail", "debug", "setprogress", "reloadevent",
                    "setuivar", "getuivar", "clearuivars", "dumpuivars", "skill"
            );

            String partial = args[0].toLowerCase();
            completions = subcommands.stream()
                    .filter(cmd -> cmd.startsWith(partial))
                    .collect(Collectors.toList());

        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            switch (subcommand) {
                case "info", "start", "complete", "fail", "debug", "reloadevent" ->
                        completions = getAvailableEventIds(args[1]);
                case "open" ->
                        completions = getAvailableUIIds(args[1]);
                case "progress" -> {
                    if (sender instanceof Player player) {
                        completions = getPlayerEventIds(player, args[1]);
                    }
                }
                case "reset" -> {
                    completions = Arrays.asList("events", "skills").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "setprogress" -> {
                    if (sender instanceof Player player) {
                        completions = getInProgressEventIds(player, args[1]);
                    }
                }
                case "setuivar", "getuivar", "clearuivars", "dumpuivars" -> {
                    completions = new ArrayList<>();
                    completions.addAll(Arrays.asList("@a", "@p", "@r"));
                    completions.addAll(getOnlinePlayerNames(args[1]));
                    completions = completions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "skill" -> {
                    completions = Arrays.asList("info", "grant", "spend").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }

        } else if (args.length == 3) {
            String subcommand = args[0].toLowerCase();

            if ("setprogress".equals(subcommand)) {
                String eventId = args[1];
                completions = getObjectiveIds(eventId, args[2]);
            }

            if ("setuivar".equals(subcommand) || "getuivar".equals(subcommand)) {
                var target = plugin.getServer().getPlayer(args[1]);
                if (target != null) {
                    completions = getKnownUIVars(target.getUniqueId(), args[2]);
                }
            }

            if ("skill".equals(subcommand)) {
                String skillSubcommand = args[1].toLowerCase();
                completions = new ArrayList<>();
                completions.addAll(Arrays.asList("@a", "@p", "@r"));
                completions.addAll(getOnlinePlayerNames(args[2]));
                completions = completions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if ("reset".equals(subcommand) && ("events".equalsIgnoreCase(args[1]) || "skills".equalsIgnoreCase(args[1]))) {
                completions = getOnlinePlayerNames(args[2]);
            }

        } else if (args.length == 4) {
            String subcommand = args[0].toLowerCase();

            if ("setprogress".equals(subcommand)) {
                completions = Arrays.asList("0", "1", "5", "10", "50", "100");
            }
            if ("setuivar".equals(subcommand)) {
                completions = Arrays.asList("true", "false");
            }

            if ("skill".equals(subcommand)) {
                String skillSubcommand = args[1].toLowerCase();
                if ("info".equals(skillSubcommand)) {
                    completions = getAvailableSkillTreeIds(args[3]);
                } else if ("grant".equals(skillSubcommand)) {
                    completions = getAvailablePointTypes(args[3]);
                } else if ("spend".equals(skillSubcommand)) {
                    completions = getAvailableSkillTreeIds(args[3]);
                }
            }

            if ("reset".equals(subcommand)) {
                if ("events".equalsIgnoreCase(args[1])) {
                    completions = getAvailableEventIdsWithAll(args[3]);
                } else if ("skills".equalsIgnoreCase(args[1])) {
                    completions = getAvailableSkillTreeIdsWithAll(args[3]);
                }
            }
        } else if (args.length == 5) {
            String subcommand = args[0].toLowerCase();

            if ("skill".equals(subcommand)) {
                String skillSubcommand = args[1].toLowerCase();
                if ("spend".equals(skillSubcommand)) {
                    String treeId = args[3];
                    completions = getAvailableNodeIds(treeId, args[4]);
                }
            }
        }

        return completions;
    }

        private List<String> getAvailableEventIds(String partial) {
        return plugin.getStorage().getAllEventDefinitions().keySet().stream()
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

        private List<String> getPlayerEventIds(Player player, String partial) {
        return plugin.getStorage().getAllEventDefinitions().keySet().stream()
                .filter(eventId -> {
                    var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);
                    return progressOpt.isPresent();
                })
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

        private List<String> getInProgressEventIds(Player player, String partial) {
        return plugin.getStorage().getAllEventDefinitions().keySet().stream()
                .filter(eventId -> {
                    var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), eventId);
                    return progressOpt.isPresent() &&
                            progressOpt.get().getState() == com.eventui.api.event.EventState.IN_PROGRESS;
                })
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

        private List<String> getObjectiveIds(String eventId, String partial) {
        var eventOpt = plugin.getStorage().getEventDefinition(eventId);

        return eventOpt.map(eventDefinition -> eventDefinition.getObjectives().stream()
                .map(ObjectiveDefinition::getId)
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList())).orElseGet(List::of);

    }
        private List<String> getAvailableUIIds(String partial) {
        return plugin.getUIConfigs().keySet().stream()
                .filter(uiId -> uiId.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }
        private List<String> getOnlinePlayerNames(String partial) {
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

        private List<String> getKnownUIVars(java.util.UUID playerId, String partial) {
        return plugin.getUIStateManager().getAllVariables(playerId).keySet().stream()
                .filter(key -> key.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getAvailableSkillTreeIds(String partial) {
        return plugin.getSkillTreeStorage().getAllSkillTrees().keySet().stream()
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getAvailableEventIdsWithAll(String partial) {
        List<String> ids = new ArrayList<>();
        ids.add("all");
        ids.addAll(getAvailableEventIds(partial));
        return ids.stream()
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getAvailableSkillTreeIdsWithAll(String partial) {
        List<String> ids = new ArrayList<>();
        ids.add("all");
        ids.addAll(getAvailableSkillTreeIds(partial));
        return ids.stream()
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getAvailablePointTypes(String partial) {
        List<String> pointTypes = new ArrayList<>();

        // Add point types from skill trees
        pointTypes.addAll(plugin.getSkillTreeStorage().getAllSkillTrees().values().stream()
                .map(tree -> tree.getPointType())
                .distinct()
                .toList());

        // Add aliases from config
        if (plugin.getSkillSourcesConfig() != null && plugin.getSkillSourcesConfig().getPointTypeResolver() != null) {
            var aliasesSection = plugin.getConfig().getConfigurationSection("skills.point_type_aliases");
            if (aliasesSection != null) {
                pointTypes.addAll(aliasesSection.getKeys(false));
            }
        }

        // Add point types from point distributions
        if (plugin.getSkillSourcesConfig() != null) {
            var sourcesConfig = plugin.getSkillSourcesConfig();
            pointTypes.addAll(sourcesConfig.getMobKillPointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getPlayerKillPointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getBlockMinePointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getFishingPointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getCropHarvestPointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getAnimalBreedPointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getEventCompletePointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getObjectiveCompletePointDistribution().keySet());
            pointTypes.addAll(sourcesConfig.getPlaytimePointDistribution().keySet());
        }

        return pointTypes.stream()
                .distinct()
                .filter(type -> type.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getAvailableNodeIds(String treeId, String partial) {
        var treeOpt = plugin.getSkillTreeStorage().getSkillTree(treeId);
        if (treeOpt.isEmpty()) {
            return new ArrayList<>();
        }
        return treeOpt.get().getNodes().stream()
                .map(node -> node.getId())
                .filter(id -> id.toLowerCase().startsWith(partial.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

}
