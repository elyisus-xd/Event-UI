package com.eventui.core.tracking;

import com.eventui.api.event.EventState;
import com.eventui.api.objective.ObjectiveDefinition;
import com.eventui.api.objective.ObjectiveType;
import com.eventui.core.EventUIPlugin;
import com.eventui.core.event.EventDefinitionImpl;
import com.eventui.core.event.EventProgressImpl;
import com.eventui.core.objective.ObjectiveProgressImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.*;
import org.bukkit.generator.structure.GeneratedStructure;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ObjectiveTracker implements Listener {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final EventUIPlugin plugin;

    private final Map<UUID, Set<String>> activeEventsByPlayer = new ConcurrentHashMap<>();
    private final Map<ObjectiveType, Set<String>> eventsByObjectiveType = new ConcurrentHashMap<>();

    public ObjectiveTracker(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    private <C> void processAction(Player player, ObjectiveType type,
                                   int amount, C context,
                                   ObjectiveMatcher<C> matcher) {

        Set<String> activeEvents = getRelevantActiveEvents(player.getUniqueId(), type);
        Set<String> allTypeEvents = eventsByObjectiveType.getOrDefault(type, Set.of());
        Set<String> eventsToCheck = new HashSet<>(activeEvents);
        eventsToCheck.addAll(allTypeEvents);

        if (eventsToCheck.isEmpty()) return;

        UUID playerId = player.getUniqueId();

        for (String eventId : eventsToCheck) {
            var defOpt = plugin.getStorage().getEventDefinition(eventId);
            if (defOpt.isEmpty()) continue;

            EventDefinitionImpl eventDef = (EventDefinitionImpl) defOpt.get();

            if (!tryAutoStart(player, eventDef, playerId)) continue;

            var progressOpt = plugin.getStorage().getProgress(playerId, eventId);
            if (progressOpt.isEmpty()) continue;

            EventProgressImpl progress = (EventProgressImpl) progressOpt.get();

            for (ObjectiveDefinition objective : eventDef.getObjectives()) {
                if (objective.getType() != type) continue;
                if (!matcher.matches(context, objective)) continue;

                ObjectiveProgressImpl objProgress = progress.getObjectiveProgress(objective.getId());

                if (objProgress == null) {
                    progress.registerObjective(objective.getId(), objective.getTargetAmount());
                    objProgress = progress.getObjectiveProgress(objective.getId());
                }

                if (objProgress.isCompleted()) continue;
                boolean completed = objProgress.increment(amount);

                plugin.getEventBridge().notifyProgressUpdate(
                        playerId, eventDef.getId(), objective.getId(),
                        objProgress.getCurrentAmount(), objProgress.getTargetAmount(),
                        objective.getDescription()
                );

                plugin.getMessenger().sendProgress(
                        player,
                        objective.getDescription(),
                        objProgress.getCurrentAmount(),
                        objProgress.getTargetAmount()
                );

                if (completed) {
                    plugin.getMessenger().sendObjectiveCompleted(player, objective.getDescription());
                    checkEventCompletion(player, eventDef, progress);
                }
            }
        }
    }




    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        String block = event.getBlock().getType().getKey().toString();
        processAction(event.getPlayer(), ObjectiveType.MINE_BLOCK, 1, block,
                (b, obj) -> b.equals(obj.getParameters().get("block_id")));

        String tool = event.getPlayer().getInventory().getItemInMainHand().getType().getKey().toString();
        processAction(event.getPlayer(), ObjectiveType.BREAK_WITH_TOOL, 1,
                new String[]{tool, block},
                (ctx, obj) -> ctx[0].equals(obj.getParameters().get("tool_type")) &&
                        (obj.getParameters().get("block_id") == null ||
                                ctx[1].equals(obj.getParameters().get("block_id"))));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        String block = event.getBlock().getType().getKey().toString();
        processAction(event.getPlayer(), ObjectiveType.PLACE_BLOCK, 1, block,
                (b, obj) -> b.equals(obj.getParameters().get("block_id")));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) return;
        String entity = event.getEntity().getType().getKey().toString();
        processAction(player, ObjectiveType.KILL_ENTITY, 1, entity,
                (e, obj) -> e.equals(obj.getParameters().get("entity_type")));
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String item = event.getRecipe().getResult().getType().getKey().toString();
        int amount = event.isShiftClick() ? 1 : event.getRecipe().getResult().getAmount();
        processAction(player, ObjectiveType.CRAFT_ITEM, amount, item,
                (i, obj) -> i.equals(obj.getParameters().get("item_id")));
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        processAction(event.getPlayer(), ObjectiveType.SMELT_ITEM, event.getItemAmount(),
                event.getItemType().getKey().toString(),
                (i, obj) -> i.equals(obj.getParameters().get("item_id")));
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        processAction(event.getPlayer(), ObjectiveType.CONSUME_ITEM, 1,
                event.getItem().getType().getKey().toString(),
                (i, obj) -> i.equals(obj.getParameters().get("item_id")));
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        String entity = event.getEntity().getType().getKey().toString();
        int damage = (int) Math.ceil(event.getFinalDamage());
        processAction(player, ObjectiveType.DAMAGE_ENTITY, damage, entity,
                (e, obj) -> e.equals(obj.getParameters().get("entity_type")));
    }

    @EventHandler
    public void onEntityTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        String entity = event.getEntity().getType().getKey().toString();
        processAction(player, ObjectiveType.TAME_ENTITY, 1, entity,
                (e, obj) -> e.equals(obj.getParameters().get("entity_type")));
    }

    @EventHandler
    public void onEntityBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        String entity = event.getEntity().getType().getKey().toString();
        processAction(player, ObjectiveType.BREED_ENTITY, 1, entity,
                (e, obj) -> e.equals(obj.getParameters().get("entity_type")));
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        String item = event.getItem().getType().getKey().toString();
        processAction(event.getEnchanter(), ObjectiveType.ENCHANT_ITEM, 1, item,
                (i, obj) -> obj.getParameters().get("item_type") == null ||
                        i.equals(obj.getParameters().get("item_type")));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        String block = event.getClickedBlock().getType().getKey().toString();
        processAction(event.getPlayer(), ObjectiveType.INTERACT, 1,
                new String[]{"block", block},
                (ctx, obj) -> "block".equals(obj.getParameters().get("target_type")) &&
                        ctx[1].equals(obj.getParameters().get("target_id")));
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        String entity = event.getRightClicked().getType().getKey().toString();
        processAction(event.getPlayer(), ObjectiveType.INTERACT, 1,
                new String[]{"entity", entity},
                (ctx, obj) -> "entity".equals(obj.getParameters().get("target_type")) &&
                        ctx[1].equals(obj.getParameters().get("target_id")));
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        String dimension = getDimensionName(event.getPlayer().getWorld().getEnvironment());
        processAction(event.getPlayer(), ObjectiveType.VISIT_DIMENSION, 1, dimension,
                (d, obj) -> d.equalsIgnoreCase(obj.getParameters().get("dimension")));
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        int level = event.getNewLevel();
        processAction(event.getPlayer(), ObjectiveType.REACH_LEVEL, 0, level,
                (l, obj) -> {
                    String req = obj.getParameters().get("level");
                    return req != null && l >= Integer.parseInt(req);
                });
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().toString();
        processAction(event.getPlayer(), ObjectiveType.UNLOCK_ADVANCEMENT, 1, key,
                (k, obj) -> k.equals(obj.getParameters().get("advancement_id")));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        if (!eventsByObjectiveType.getOrDefault(ObjectiveType.VISIT_BIOME, Set.of()).isEmpty()) {
            String biomeId = event.getTo().getWorld()
                    .getBiome(
                            event.getTo().getBlockX(),
                            event.getTo().getBlockY(),
                            event.getTo().getBlockZ()
                    ).getKey().toString();

            processAction(event.getPlayer(), ObjectiveType.VISIT_BIOME, 1, biomeId,
                    (b, obj) -> b.equalsIgnoreCase(obj.getParameters().get("biome_id")));
        }

        if (!eventsByObjectiveType.getOrDefault(ObjectiveType.VISIT_STRUCTURE, Set.of()).isEmpty()) {
            checkStructureAt(event.getPlayer(), event.getTo());
        }
    }


    private void checkStructureAt(Player player, org.bukkit.Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        java.util.Collection<GeneratedStructure> structures =
                location.getWorld().getStructures(chunkX, chunkZ);

        for (GeneratedStructure generated : structures) {
            String structureKey = generated.getStructure().getKey().toString();
            processAction(player, ObjectiveType.VISIT_STRUCTURE, 1, structureKey,
                    (s, obj) -> s.equalsIgnoreCase(obj.getParameters().get("structure_id")));
        }
    }






    private boolean tryAutoStart(Player player, EventDefinitionImpl eventDef, UUID playerId) {
        var progressOpt = plugin.getStorage().getProgress(playerId, eventDef.getId());

        if (progressOpt.isPresent()) {
            return progressOpt.get().getState() == EventState.IN_PROGRESS;
        }

        boolean globalAlwaysActive = plugin.getUIConfigManager().isEventsAlwaysActive();
        Boolean eventOverride = eventDef.isAlwaysActive();
        boolean shouldAutoStart = eventOverride != null ? eventOverride : globalAlwaysActive;

        if (!shouldAutoStart) return false;

        EventProgressImpl newProgress = plugin.getStorage().getOrCreateProgress(playerId, eventDef.getId());
        newProgress.start();
        registerActiveEvent(playerId, eventDef.getId());

        plugin.getMessenger().sendEventStarted(player, eventDef.getDisplayName());
        plugin.getEventBridge().notifyStateChange(playerId, eventDef.getId(), EventState.IN_PROGRESS);
        LOGGER.info("Auto-started '" + eventDef.getId() + "' for " + player.getName());

        return true;
    }

    public void buildObjectiveTypeIndex() {
        eventsByObjectiveType.clear();

        plugin.getStorage().getAllEventDefinitions().values().forEach(eventDef ->
                eventDef.getObjectives().forEach(objective -> eventsByObjectiveType
                        .computeIfAbsent(objective.getType(), k -> ConcurrentHashMap.newKeySet())
                        .add(eventDef.getId()))
        );

        LOGGER.fine("Built objective type index: " + eventsByObjectiveType.size() + " types indexed");

        eventsByObjectiveType.forEach((type, eventIds) -> {
            String status;
            if (UNIMPLEMENTED_TYPES.contains(type)) {
                status = "⚠ SIN HANDLER — estos eventos NUNCA completarán este objetivo";
            } else if (PARTIAL_TYPES.contains(type)) {
                status = "⚠ PARCIAL — puede no funcionar en todos los casos";
            } else {
                return;
            }
            LOGGER.warning("Tipo " + type.name() + " (" + status + "): " +
                    String.join(", ", eventIds));
        });
    }

    private static final Set<ObjectiveType> UNIMPLEMENTED_TYPES = Set.of(
            ObjectiveType.BREW_POTION,
            ObjectiveType.CUSTOM
    );
    private static final Set<ObjectiveType> PARTIAL_TYPES = Set.of(
            ObjectiveType.COLLECT_ITEM,
            ObjectiveType.REACH_LOCATION
    );

    public void initializeActiveEventsIndex() {
        activeEventsByPlayer.clear();

        plugin.getStorage().getAllProgress().forEach((playerId, eventMap) ->
                eventMap.forEach((eventId, progress) -> {
                    if (progress.getState() == com.eventui.api.event.EventState.IN_PROGRESS) {
                        registerActiveEvent(playerId, eventId);
                    }
                })
        );

        LOGGER.fine("Initialized active events index: " +
                activeEventsByPlayer.size() + " players with active events");
    }

    public void checkCollectObjectives(Player player) {
        processAction(player, ObjectiveType.COLLECT_ITEM, 0, player,
                (p, obj) -> {
                    String requiredItem = obj.getParameters().get("item_id");
                    if (requiredItem == null) return false;
                    var objProgress = plugin.getStorage()
                            .getProgress(p.getUniqueId(),
                                    obj.getId())
                            .orElse(null);
                    return objProgress != null;
                });
    }

    public void checkReachLocationObjectives(Player player) {
        processAction(player, ObjectiveType.REACH_LOCATION, 1, player.getLocation(),
                (loc, obj) -> {
                    try {
                        double tx = Double.parseDouble(obj.getParameters().getOrDefault("x", "0"));
                        double ty = Double.parseDouble(obj.getParameters().getOrDefault("y", "0"));
                        double tz = Double.parseDouble(obj.getParameters().getOrDefault("z", "0"));
                        double radius = Double.parseDouble(obj.getParameters().getOrDefault("radius", "3"));
                        String world = obj.getParameters().get("world");

                        if (world != null && !loc.getWorld().getName().equals(world)) return false;

                        double distance = Math.sqrt(
                                Math.pow(loc.getX() - tx, 2) +
                                        Math.pow(loc.getY() - ty, 2) +
                                        Math.pow(loc.getZ() - tz, 2)
                        );
                        return distance <= radius;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
    }


    public void registerActiveEvent(UUID playerId, String eventId) {
        activeEventsByPlayer.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(eventId);
    }

    public void unregisterActiveEvent(UUID playerId, String eventId) {
        Set<String> events = activeEventsByPlayer.get(playerId);
        if (events != null) events.remove(eventId);
    }

    public Set<String> getRelevantActiveEvents(UUID playerId, ObjectiveType type) {
        Set<String> playerEvents = activeEventsByPlayer.getOrDefault(playerId, Set.of());
        Set<String> typeEvents   = eventsByObjectiveType.getOrDefault(type, Set.of());
        Set<String> result = new HashSet<>(playerEvents);
        result.retainAll(typeEvents);
        return result;
    }

    private void checkEventCompletion(Player player, EventDefinitionImpl eventDef, EventProgressImpl progress) {
        boolean allCompleted = eventDef.getObjectives().stream()
                .allMatch(obj -> {
                    ObjectiveProgressImpl p = progress.getObjectiveProgress(obj.getId());
                    return p != null && p.isCompleted();
                });

        if (allCompleted) {
            progress.complete();
            unregisterActiveEvent(player.getUniqueId(), eventDef.getId());
            plugin.getMessenger().sendEventCompleted(player, eventDef.getDisplayName());
            plugin.getRewardManager().giveRewards(player, eventDef);
            notifyStateChange(player.getUniqueId(), eventDef.getId());
        }
    }



    private void notifyStateChange(UUID playerId, String eventId) {
        plugin.getEventBridge().sendMessage(
                new com.eventui.core.bridge.PluginBridgeMessage(
                        com.eventui.api.bridge.MessageType.EVENT_STATE_CHANGED,
                        Map.of("event_id", eventId, "new_state", EventState.COMPLETED.name()),
                        playerId
                )
        );
    }

    private String getDimensionName(org.bukkit.World.Environment env) {
        return switch (env) {
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
    }


}
