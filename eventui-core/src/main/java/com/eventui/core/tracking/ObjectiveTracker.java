package com.eventui.core.tracking;

import com.eventui.api.event.EventState;
import com.eventui.api.event.EventDefinition;
import com.eventui.api.objective.CustomObjectiveHandler;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private final Map<String, CustomObjectiveHandler> customHandlers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> playerItemTracking = new ConcurrentHashMap<>();

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
                    plugin.getPlayerDataManager().requestSave(playerId, "objective completed: " + objective.getId());
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

    @EventHandler
    public void onBrew(org.bukkit.event.inventory.BrewEvent event) {
        org.bukkit.Location standLocation = event.getBlock().getLocation();
        LOGGER.info("[BREW_DEBUG] BrewEvent fired: block="
                + event.getBlock().getType().getKey()
                + ", location=" + formatLocation(standLocation)
                + ", cancelled=" + event.isCancelled());

        if (standLocation.getWorld() == null) {
            LOGGER.info("[BREW_DEBUG] BrewEvent ignored: stand world is null.");
            return;
        }

        org.bukkit.inventory.BrewerInventory contents = event.getContents();
        LOGGER.info("[BREW_DEBUG] BrewerInventory holder=" + contents.getHolder()
                + ", ingredient=" + describeItemStack(contents.getIngredient())
                + ", fuel=" + describeItemStack(contents.getFuel()));

        for (int slot = 0; slot < contents.getSize(); slot++) {
            LOGGER.info("[BREW_DEBUG] Slot " + slot + " contains " + describeItemStack(contents.getItem(slot)));
        }

        List<ItemStack> results = event.getResults();
        for (int slot = 0; slot < results.size(); slot++) {
            LOGGER.info("[BREW_DEBUG] Result " + slot + " contains " + describeItemStack(results.get(slot)));
        }

        double radiusSquared = 100.0;
        LOGGER.info("[BREW_DEBUG] Players in world '" + standLocation.getWorld().getName()
                + "': " + standLocation.getWorld().getPlayers().size()
                + ", radiusSquared=" + radiusSquared);

        for (Player player : standLocation.getWorld().getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(standLocation);
            LOGGER.info("[BREW_DEBUG] Player candidate: name=" + player.getName()
                    + ", uuid=" + player.getUniqueId()
                    + ", location=" + formatLocation(player.getLocation())
                    + ", distanceSquared=" + distanceSquared
                    + ", withinRadius=" + (distanceSquared <= radiusSquared));

            if (distanceSquared > radiusSquared) continue;

            for (int slot = 0; slot < results.size(); slot++) {
                ItemStack result = results.get(slot);
                LOGGER.info("[BREW_DEBUG] Checking player=" + player.getName()
                        + ", result slot=" + slot
                        + ", item=" + describeItemStack(result));

                if (result == null || result.getType().isAir()) {
                    LOGGER.info("[BREW_DEBUG] Result " + slot + " ignored: empty/air.");
                    continue;
                }
                if (!result.getType().getKey().toString().contains("potion")) {
                    LOGGER.info("[BREW_DEBUG] Result " + slot + " ignored: material key is not potion-like: "
                            + result.getType().getKey());
                    continue;
                }

                processBrewedPotion(player, result);
            }
        }
    }

    private void processBrewedPotion(Player player, ItemStack potionStack) {
        String potionTypeKey = resolvePotionTypeKey(potionStack);
        LOGGER.info("[BREW_DEBUG] processBrewedPotion: player=" + player.getName()
                + ", item=" + describeItemStack(potionStack)
                + ", resolvedPotionTypeKey=" + potionTypeKey);

        if (potionTypeKey == null) {
            LOGGER.info("[BREW_DEBUG] processBrewedPotion stopped: resolved potion type is null.");
            return;
        }

        UUID playerId = player.getUniqueId();

        Set<String> activeEvents = getRelevantActiveEvents(playerId, ObjectiveType.BREW_POTION);
        Set<String> allTypeEvents = eventsByObjectiveType.getOrDefault(ObjectiveType.BREW_POTION, Set.of());
        Set<String> eventsToCheck = new HashSet<>(activeEvents);
        eventsToCheck.addAll(allTypeEvents);

        LOGGER.info("[BREW_DEBUG] Event candidates for player=" + player.getName()
                + ": activeEvents=" + activeEvents
                + ", allTypeEvents=" + allTypeEvents
                + ", eventsToCheck=" + eventsToCheck);

        if (eventsToCheck.isEmpty()) {
            LOGGER.info("[BREW_DEBUG] processBrewedPotion stopped: eventsToCheck is empty.");
            return;
        }

        for (String eventId : eventsToCheck) {
            var defOpt = plugin.getStorage().getEventDefinition(eventId);
            if (defOpt.isEmpty()) {
                LOGGER.info("[BREW_DEBUG] Event definition missing for eventId=" + eventId);
                continue;
            }
            EventDefinitionImpl eventDef = (EventDefinitionImpl) defOpt.get();

            boolean canProcessEvent = tryAutoStart(player, eventDef, playerId);
            LOGGER.info("[BREW_DEBUG] tryAutoStart result: eventId=" + eventId
                    + ", displayName=" + eventDef.getDisplayName()
                    + ", alwaysActiveOverride=" + eventDef.isAlwaysActive()
                    + ", canProcess=" + canProcessEvent);
            if (!canProcessEvent) continue;

            var progressOpt = plugin.getStorage().getProgress(playerId, eventId);
            if (progressOpt.isEmpty()) {
                LOGGER.info("[BREW_DEBUG] Progress missing after tryAutoStart: eventId=" + eventId
                        + ", player=" + player.getName());
                continue;
            }
            EventProgressImpl progress = (EventProgressImpl) progressOpt.get();
            LOGGER.info("[BREW_DEBUG] Progress loaded: eventId=" + eventId
                    + ", state=" + progress.getState());

            for (ObjectiveDefinition objective : eventDef.getObjectives()) {
                LOGGER.info("[BREW_DEBUG] Inspect objective: eventId=" + eventId
                        + ", objectiveId=" + objective.getId()
                        + ", type=" + objective.getType()
                        + ", parameters=" + objective.getParameters());

                if (objective.getType() != ObjectiveType.BREW_POTION) {
                    LOGGER.info("[BREW_DEBUG] Objective ignored: not BREW_POTION.");
                    continue;
                }

                String requiredPotionType = objective.getParameters().get("potion_type");
                if (requiredPotionType == null) {
                    LOGGER.warning("BREW_POTION objetivo '" + objective.getId() + "' sin 'potion_type' en parameters.");
                    continue;
                }
                boolean potionMatches = requiredPotionType.equalsIgnoreCase(potionTypeKey);
                LOGGER.info("[BREW_DEBUG] Compare potion_type: objectiveId=" + objective.getId()
                        + ", required='" + requiredPotionType + "'"
                        + ", actual='" + potionTypeKey + "'"
                        + ", equalsIgnoreCase=" + potionMatches);
                if (!potionMatches) continue;

                ObjectiveProgressImpl objProgress = progress.getObjectiveProgress(objective.getId());
                if (objProgress == null) {
                    LOGGER.info("[BREW_DEBUG] Objective progress missing; registering objectiveId="
                            + objective.getId() + ", target=" + objective.getTargetAmount());
                    progress.registerObjective(objective.getId(), objective.getTargetAmount());
                    objProgress = progress.getObjectiveProgress(objective.getId());
                }
                LOGGER.info("[BREW_DEBUG] Objective progress before increment: objectiveId=" + objective.getId()
                        + ", current=" + objProgress.getCurrentAmount()
                        + ", target=" + objProgress.getTargetAmount()
                        + ", completed=" + objProgress.isCompleted()
                        + ", incrementAmount=" + potionStack.getAmount());

                if (objProgress.isCompleted()) {
                    LOGGER.info("[BREW_DEBUG] Objective ignored: already completed.");
                    continue;
                }

                boolean completed = objProgress.increment(potionStack.getAmount());
                LOGGER.info("[BREW_DEBUG] Objective progress after increment: objectiveId=" + objective.getId()
                        + ", current=" + objProgress.getCurrentAmount()
                        + ", target=" + objProgress.getTargetAmount()
                        + ", completedNow=" + completed);

                plugin.getEventBridge().notifyProgressUpdate(
                        playerId, eventDef.getId(), objective.getId(),
                        objProgress.getCurrentAmount(), objProgress.getTargetAmount(),
                        objective.getDescription()
                );
                plugin.getMessenger().sendProgress(player, objective.getDescription(),
                        objProgress.getCurrentAmount(), objProgress.getTargetAmount());

                if (completed) {
                    plugin.getMessenger().sendObjectiveCompleted(player, objective.getDescription());
                    plugin.getPlayerDataManager().requestSave(playerId, "objective completed: " + objective.getId());
                    checkEventCompletion(player, eventDef, progress);
                }
            }
        }
    }

    private String resolvePotionTypeKey(ItemStack item) {
        LOGGER.info("[BREW_DEBUG] resolvePotionTypeKey input: " + describeItemStack(item));
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
            var basePotionType = potionMeta.getBasePotionType();
            LOGGER.info("[BREW_DEBUG] PotionMeta detected: basePotionType=" + basePotionType
                    + ", customEffects=" + potionMeta.getCustomEffects());
            if (basePotionType != null) {
                return basePotionType.getKey().toString();
            }
        } else {
            LOGGER.info("[BREW_DEBUG] ItemMeta is not PotionMeta: meta="
                    + (item == null ? null : item.getItemMeta()));
        }
        return null;
    }

    private String describeItemStack(ItemStack item) {
        if (item == null) return "null";

        StringBuilder description = new StringBuilder();
        description.append("ItemStack{type=").append(item.getType().getKey())
                .append(", amount=").append(item.getAmount())
                .append(", hasMeta=").append(item.hasItemMeta());

        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
            description.append(", potionBase=").append(potionMeta.getBasePotionType())
                    .append(", customEffects=").append(potionMeta.getCustomEffects());
        } else if (item.hasItemMeta()) {
            description.append(", metaClass=").append(item.getItemMeta().getClass().getName());
        }

        description.append('}');
        return description.toString();
    }

    private String formatLocation(org.bukkit.Location location) {
        if (location == null) return "null";
        return "Location{world=" + (location.getWorld() == null ? null : location.getWorld().getName())
                + ", x=" + location.getX()
                + ", y=" + location.getY()
                + ", z=" + location.getZ()
                + "}";
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
            String structureKey = generated.getStructure().key().asString();
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
        plugin.getPlayerDataManager().requestSave(playerId, "event auto-started: " + eventDef.getId());
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

    private static final Set<ObjectiveType> UNIMPLEMENTED_TYPES = Set.of();
    private static final Set<ObjectiveType> PARTIAL_TYPES = Set.of();

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
        UUID playerId = player.getUniqueId();
        Map<String, Integer> previousTracking = playerItemTracking.getOrDefault(playerId, new ConcurrentHashMap<>());
        Map<String, Integer> currentTracking = new ConcurrentHashMap<>();
        Set<String> relevantEvents = getRelevantActiveEvents(playerId, ObjectiveType.COLLECT_ITEM);
        
        for (String eventId : relevantEvents) {
            var eventDefOpt = plugin.getStorage().getEventDefinition(eventId);
            if (eventDefOpt.isEmpty()) continue;
            
            EventDefinitionImpl eventDef = (EventDefinitionImpl) eventDefOpt.get();
            var progressOpt = plugin.getStorage().getProgress(playerId, eventId);
            if (progressOpt.isEmpty()) continue;
            EventProgressImpl progress = (EventProgressImpl) progressOpt.get();
            for (ObjectiveDefinition objective : eventDef.getObjectives()) {
                if (objective.getType() != ObjectiveType.COLLECT_ITEM) continue;
                
                String itemId = objective.getParameters().get("item_id");
                if (itemId == null) {
                    LOGGER.warning("COLLECT_ITEM objetivo '" + objective.getId() + "' sin item_id");
                    continue;
                }
                
                int currentAmount = countItemsInInventory(player, itemId);
                currentTracking.put(itemId, currentAmount);
                
                int previousAmount = previousTracking.getOrDefault(itemId, 0);
                int delta = currentAmount - previousAmount;
                
                if (delta == 0) continue;
                
                ObjectiveProgressImpl objProgress = progress.getObjectiveProgress(objective.getId());
                
                if (objProgress == null) {
                    progress.registerObjective(objective.getId(), objective.getTargetAmount());
                    objProgress = progress.getObjectiveProgress(objective.getId());
                }
                
                if (objProgress.isCompleted()) continue;
                
                if (delta > 0) {
                    boolean completed = objProgress.increment(delta);
                    plugin.getEventBridge().notifyProgressUpdate(
                            playerId, eventId, objective.getId(),
                            objProgress.getCurrentAmount(), objProgress.getTargetAmount(),
                            objective.getDescription()
                    );
                    plugin.getMessenger().sendProgress(player, objective.getDescription(),
                            objProgress.getCurrentAmount(), objProgress.getTargetAmount());
                    
                    if (completed) {
                        plugin.getMessenger().sendObjectiveCompleted(player, objective.getDescription());
                        plugin.getPlayerDataManager().requestSave(playerId, "objective completed: " + objective.getId());
                        checkEventCompletion(player, eventDef, progress);
                    }
                } else {
                    objProgress.decrement(Math.abs(delta));
                    plugin.getEventBridge().notifyProgressUpdate(
                            playerId, eventId, objective.getId(),
                            objProgress.getCurrentAmount(), objProgress.getTargetAmount(),
                            objective.getDescription()
                    );
                    plugin.getMessenger().sendProgress(player, objective.getDescription(),
                            objProgress.getCurrentAmount(), objProgress.getTargetAmount());
                }
            }
        }
        
        playerItemTracking.put(playerId, currentTracking);
    }
    
    private int countItemsInInventory(Player player, String itemId) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir() && 
                stack.getType().getKey().toString().equals(itemId)) {
                count += stack.getAmount();
            }
        }
        return count;
    }
    
    private List<ObjectiveDefinition> getObjectivesOfType() {
        List<ObjectiveDefinition> objectives = new ArrayList<>();
        for (EventDefinition event : plugin.getStorage().getAllEventDefinitions().values()) {
            for (ObjectiveDefinition obj : event.getObjectives()) {
                if (obj.getType() == ObjectiveType.CUSTOM) {
                    objectives.add(obj);
                }
            }
        }
        return objectives;
    }

    public void checkReachLocationObjectives(Player player) {
        UUID playerId = player.getUniqueId();
        Set<String> relevantEvents = getRelevantActiveEvents(playerId, ObjectiveType.REACH_LOCATION);
        
        for (String eventId : relevantEvents) {
            var eventDefOpt = plugin.getStorage().getEventDefinition(eventId);
            if (eventDefOpt.isEmpty()) continue;
            
            EventDefinitionImpl eventDef = (EventDefinitionImpl) eventDefOpt.get();
            var progressOpt = plugin.getStorage().getProgress(playerId, eventId);
            if (progressOpt.isEmpty()) continue;
            
            EventProgressImpl progress = (EventProgressImpl) progressOpt.get();

            for (ObjectiveDefinition objective : eventDef.getObjectives()) {
                if (objective.getType() != ObjectiveType.REACH_LOCATION) continue;
                
                try {
                    String xStr = objective.getParameters().get("x");
                    String yStr = objective.getParameters().get("y");
                    String zStr = objective.getParameters().get("z");
                    
                    if (xStr == null || yStr == null || zStr == null) {
                        LOGGER.warning("REACH_LOCATION objetivo '" + objective.getId() + 
                            "' falta parámetros: x=" + xStr + ", y=" + yStr + ", z=" + zStr);
                        continue;
                    }
                    
                    double targetX = Double.parseDouble(xStr);
                    double targetY = Double.parseDouble(yStr);
                    double targetZ = Double.parseDouble(zStr);
                    double radius = Double.parseDouble(objective.getParameters().getOrDefault("radius", "3"));
                    String world = objective.getParameters().get("world");
                    
                    org.bukkit.Location playerLoc = player.getLocation();
                    if (world != null && !playerLoc.getWorld().getName().equals(world)) continue;
                    
                    double distance = Math.sqrt(
                        Math.pow(playerLoc.getX() - targetX, 2) +
                        Math.pow(playerLoc.getY() - targetY, 2) +
                        Math.pow(playerLoc.getZ() - targetZ, 2)
                    );
                    
                    boolean withinRadius = distance <= radius;
                    
                    ObjectiveProgressImpl objProgress = progress.getObjectiveProgress(objective.getId());
                    
                    if (objProgress == null) {
                        progress.registerObjective(objective.getId(), objective.getTargetAmount());
                        objProgress = progress.getObjectiveProgress(objective.getId());
                    }
                    
                    if (objProgress.isCompleted()) continue;
                    
                    if (withinRadius && objProgress.getCurrentAmount() < objective.getTargetAmount()) {
                        boolean completed = objProgress.increment(1);
                        plugin.getEventBridge().notifyProgressUpdate(
                            playerId, eventId, objective.getId(),
                            objProgress.getCurrentAmount(), objProgress.getTargetAmount(),
                            objective.getDescription()
                        );
                        plugin.getMessenger().sendProgress(player, objective.getDescription(),
                            objProgress.getCurrentAmount(), objProgress.getTargetAmount());
                        
                        if (completed) {
                            plugin.getMessenger().sendObjectiveCompleted(player, objective.getDescription());
                            plugin.getPlayerDataManager().requestSave(playerId, "objective completed: " + objective.getId());
                            checkEventCompletion(player, eventDef, progress);
                        }
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("REACH_LOCATION objetivo '" + objective.getId() + 
                        "' tiene parámetros numéricos inválidos: " + e.getMessage());
                }
            }
        }
    }

    public void registerCustomHandler(CustomObjectiveHandler handler) {
        String customId = handler.getCustomId();
        if (customHandlers.containsKey(customId)) {
            LOGGER.warning("Custom handler '" + customId + "' ya está registrado");
            return;
        }
        customHandlers.put(customId, handler);
        handler.onRegister();
        LOGGER.info("✓ Custom objective handler registrado: " + customId);
    }
    
    public void unregisterCustomHandler(String customId) {
        CustomObjectiveHandler handler = customHandlers.remove(customId);
        if (handler != null) {
            handler.onUnregister();
            LOGGER.info("✓ Custom objective handler deregistrado: " + customId);
        }
    }
    
    public void processCustomObjective(Player player, String customId, Object context) {
        CustomObjectiveHandler handler = customHandlers.get(customId);
        if (handler == null) {
            LOGGER.warning("Custom handler '" + customId + "' no encontrado");
            return;
        }
        
        for (ObjectiveDefinition objective : getObjectivesOfType()) {
            if (!customId.equals(objective.getParameters().get("custom_id"))) continue;
            
            if (!handler.onAction(player, context, objective)) continue;
            
            var progressOpt = plugin.getStorage().getProgress(player.getUniqueId(), objective.getId());
            if (progressOpt.isEmpty()) continue;
            
            EventProgressImpl progress = (EventProgressImpl) progressOpt.get();
            ObjectiveProgressImpl objProgress = progress.getObjectiveProgress(objective.getId());
            
            if (objProgress == null) {
                progress.registerObjective(objective.getId(), objective.getTargetAmount());
                objProgress = progress.getObjectiveProgress(objective.getId());
            }
            
            if (objProgress.isCompleted()) continue;
            boolean completed = objProgress.increment(1);
            
            plugin.getEventBridge().notifyProgressUpdate(
                    player.getUniqueId(), objective.getId(), objective.getId(),
                    objProgress.getCurrentAmount(), objProgress.getTargetAmount(),
                    objective.getDescription()
            );
            
            plugin.getMessenger().sendProgress(player, objective.getDescription(),
                    objProgress.getCurrentAmount(), objProgress.getTargetAmount());
            
            if (completed) {
                plugin.getMessenger().sendObjectiveCompleted(player, objective.getDescription());
                plugin.getPlayerDataManager().requestSave(player.getUniqueId(), "objective completed: " + objective.getId());
            }
        }
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
            plugin.getPlayerDataManager().requestSave(player.getUniqueId(), "event completed: " + eventDef.getId());

            if (plugin.getPointSourceManager() != null) {
                String difficulty = eventDef.getMetadata().getOrDefault("difficulty", "medium");
                plugin.getPointSourceManager().handleEventComplete(player, eventDef.getId(), difficulty);
            }
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
