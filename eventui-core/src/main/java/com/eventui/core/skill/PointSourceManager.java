package com.eventui.core.skill;

import com.eventui.core.EventUIPlugin;
import com.eventui.core.storage.SkillProgressStorage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PointSourceManager {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final SkillProgressStorage storage;
    private final SkillSourcesConfig config;
    private final EventUIPlugin plugin;

    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> dailyPlaytimePoints = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastPlaytimeDate = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> playtimeTicks = new ConcurrentHashMap<>();

    public PointSourceManager(SkillProgressStorage storage, SkillSourcesConfig config, EventUIPlugin plugin) {
        this.storage = storage;
        this.config = config;
        this.plugin = plugin;
    }

    public void handleXpGain(Player player, int xpAmount, boolean isLevelUp) {
        if (!config.isXpConversionEnabled()) return;

        if (config.isOnlyOnLevelUp() && !isLevelUp) return;

        int levelsGained = xpAmount / config.getLevelsPerPoint();
        if (levelsGained <= 0) return;

        Map<String, Double> distribution = config.getXpPointDistribution();
        if (distribution != null && !distribution.isEmpty()) {
            for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                double points = entry.getValue() * levelsGained;
                addPoints(player, entry.getKey(), points);
            }
        } else {
            
            addPoints(player, config.getXpPointTypes().getFirst(), levelsGained);
        }
    }

    public void handleMobKill(EntityDeathEvent event) {
        if (!config.isMobKillEnabled()) return;

        LivingEntity entity = event.getEntity();
        if (!(entity.getKiller() instanceof Player killer)) return;

        if (config.isMobKillNaturalOnly() && !isNaturalSpawn(entity)) {
            return;
        }

        String entityId = getEntityId(entity);

        List<String> allowedMobs = config.getMobKillAllowedMobs();
        if (allowedMobs != null && !allowedMobs.isEmpty()) {
            boolean isAllowed = false;
            for (String allowed : allowedMobs) {
                
                if (entityId.equalsIgnoreCase(allowed)) {
                    isAllowed = true;
                    break;
                }
                
                if (allowed.startsWith("#") && isInTag(entityId, allowed)) {
                    isAllowed = true;
                    break;
                }
            }
            if (!isAllowed) {
                return;
            }
        }

        int basePoints = config.getMobKillBasePoints();

        int points = applyMultipliers(config.getMobKillMultipliers(), entityId, basePoints);

        String cooldownKey = killer.getUniqueId() + ":" + entityId;
        int cooldownSeconds = config.getMobKillCooldowns().getOrDefault(entityId, 0);
        if (isOnCooldown(cooldownKey, cooldownSeconds)) {
            long remaining = getRemainingCooldown(cooldownKey, cooldownSeconds);
            plugin.getMessenger().sendPointSourceCooldown(killer, "Mob Kill (" + entityId + ")", remaining);
            return;
        }

        if (points > 0) {
            
            Map<String, Double> distribution = config.getMobKillPointDistribution();
            if (distribution != null && !distribution.isEmpty()) {
                for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                    double finalPoints = entry.getValue() * points;
                    addPoints(killer, entry.getKey(), finalPoints);
                }
            } else {
                
                addPoints(killer, config.getMobKillPointTypes().getFirst(), points);
            }
            cooldowns.put(cooldownKey, System.currentTimeMillis());
        }
    }

    public void handlePlayerKill(Player killer, Player victim) {
        if (!config.isPlayerKillEnabled()) return;

        String cooldownKey = killer.getUniqueId() + ":" + victim.getUniqueId();
        int cooldownSeconds = config.getPlayerKillCooldownPerVictim();
        if (isOnCooldown(cooldownKey, cooldownSeconds)) {
            long remaining = getRemainingCooldown(cooldownKey, cooldownSeconds);
            plugin.getMessenger().sendPointSourceCooldown(killer, "Player Kill (" + victim.getName() + ")", remaining);
            return;
        }

        Map<String, Double> distribution = config.getPlayerKillPointDistribution();
        if (distribution != null && !distribution.isEmpty()) {
            for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                addPoints(killer, entry.getKey(), entry.getValue());
            }
        } else {
            
            addPoints(killer, config.getPlayerKillPointTypes().getFirst(), config.getPlayerKillPoints());
        }
        cooldowns.put(killer.getUniqueId() + ":" + victim.getUniqueId(), System.currentTimeMillis());
    }

    public void handleBlockMine(Player player, String blockId) {
        if (!config.isBlockMineEnabled()) return;

        List<String> allowedBlocks = config.getBlockMineAllowedBlocks();
        if (allowedBlocks != null && !allowedBlocks.isEmpty()) {
            boolean isAllowed = false;
            for (String allowed : allowedBlocks) {
                
                if (blockId.equalsIgnoreCase(allowed)) {
                    isAllowed = true;
                    break;
                }
                
                if (allowed.startsWith("#") && isInTag(blockId, allowed)) {
                    isAllowed = true;
                    break;
                }
            }
            if (!isAllowed) {
                return;
            }
        }

        int basePoints = config.getBlockMineBasePoints();

        int points = applyMultipliers(config.getBlockMineMultipliers(), blockId, basePoints);

        String cooldownKey = player.getUniqueId() + ":" + blockId;
        int cooldownSeconds = config.getBlockMineCooldownPerBlock();
        if (isOnCooldown(cooldownKey, cooldownSeconds)) {
            long remaining = getRemainingCooldown(cooldownKey, cooldownSeconds);
            plugin.getMessenger().sendPointSourceCooldown(player, "Block Mine (" + blockId + ")", remaining);
            return;
        }

        if (points > 0) {
            
            Map<String, Double> distribution = config.getBlockMinePointDistribution();
            if (distribution != null && !distribution.isEmpty()) {
                for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                    double finalPoints = entry.getValue() * points;
                    addPoints(player, entry.getKey(), finalPoints);
                }
            } else {
                
                addPoints(player, config.getBlockMinePointTypes().getFirst(), points);
            }
            cooldowns.put(cooldownKey, System.currentTimeMillis());
        }
    }

    public void handleFishing(PlayerFishEvent event) {
        if (!config.isFishingEnabled()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        Entity caught = event.getCaught();

        if (caught == null) return;

        String itemId;
        if (caught instanceof org.bukkit.entity.Item item) {
            itemId = item.getItemStack().getType().getKey().toString();
        } else {
            itemId = caught.getType().getKey().toString();
        }

        int basePoints = config.getFishingBasePoints();

        int points = applyMultipliers(config.getFishingMultipliers(), itemId, basePoints);

        if (config.isFishingLuckBonus()) {
            double luck = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_LUCK).getValue();
            if (luck > 0) {
                
                points = (int) (points * (1.0 + (luck * 0.1)));
            }
        }

        if (points > 0) {
            
            Map<String, Double> distribution = config.getFishingPointDistribution();
            if (distribution != null && !distribution.isEmpty()) {
                for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                    double finalPoints = entry.getValue() * points;
                    addPoints(player, entry.getKey(), finalPoints);
                }
            } else {
                
                addPoints(player, config.getFishingPointTypes().getFirst(), points);
            }
        }
    }

    public void handleCropHarvest(Player player, String cropId, boolean isMature, boolean isManual) {
        if (!config.isCropHarvestEnabled()) return;

        if (config.isCropHarvestRequireMature() && !isMature) return;
        if (config.isCropHarvestManualOnly() && !isManual) return;

        int basePoints = config.getCropHarvestBasePoints();

        int points = applyMultipliers(config.getCropHarvestMultipliers(), cropId, basePoints);

        if (points > 0) {
            
            Map<String, Double> distribution = config.getCropHarvestPointDistribution();
            if (distribution != null && !distribution.isEmpty()) {
                for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                    double finalPoints = entry.getValue() * points;
                    addPoints(player, entry.getKey(), finalPoints);
                }
            } else {
                
                addPoints(player, config.getCropHarvestPointTypes().getFirst(), points);
            }
        }
    }

    public void handleAnimalBreed(Player player, String entityId) {
        if (!config.isAnimalBreedEnabled()) return;

        int basePoints = config.getAnimalBreedBasePoints();

        int points = applyMultipliers(config.getAnimalBreedMultipliers(), entityId, basePoints);

        String cooldownKey = player.getUniqueId() + ":" + entityId;
        int cooldownSeconds = config.getAnimalBreedCooldownPerEntity();
        if (isOnCooldown(cooldownKey, cooldownSeconds)) {
            long remaining = getRemainingCooldown(cooldownKey, cooldownSeconds);
            plugin.getMessenger().sendPointSourceCooldown(player, "Animal Breed (" + entityId + ")", remaining);
            return;
        }

        if (points > 0) {
            
            Map<String, Double> distribution = config.getAnimalBreedPointDistribution();
            if (distribution != null && !distribution.isEmpty()) {
                for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                    double finalPoints = entry.getValue() * points;
                    addPoints(player, entry.getKey(), finalPoints);
                }
            } else {
                
                addPoints(player, config.getAnimalBreedPointTypes().getFirst(), points);
            }
            cooldowns.put(cooldownKey, System.currentTimeMillis());
        }
    }

    @SuppressWarnings("unused")
    public void handleEventComplete(Player player, String eventId, String difficulty) {
        if (!config.isEventCompleteEnabled()) return;

        Map<String, Double> distribution = config.getEventCompletePointDistribution();
        if (distribution != null && !distribution.isEmpty()) {
            int basePoints = config.getEventCompleteBasePoints();
            int points = basePoints;

            if (difficulty != null) {
                Integer multiplier = config.getEventCompleteDifficultyMultipliers().get(difficulty.toLowerCase());
                if (multiplier != null) {
                    points *= multiplier;
                }
            }

            boolean isDefaultDistribution = distribution.size() == 1 &&
                    distribution.values().iterator().next() == basePoints;

            for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                double finalPoints = isDefaultDistribution ? points : entry.getValue() * points;
                addPoints(player, entry.getKey(), finalPoints);
            }
        } else {
            
            int points = config.getEventCompleteBasePoints();
            if (difficulty != null) {
                Integer multiplier = config.getEventCompleteDifficultyMultipliers().get(difficulty.toLowerCase());
                if (multiplier != null) {
                    points *= multiplier;
                }
            }
            addPoints(player, config.getEventCompletePointTypes().getFirst(), points);
        }
    }

    @SuppressWarnings("unused")
    public void handleObjectiveComplete(Player player, String eventId, boolean eventCompleted) {
        if (!config.isObjectiveCompleteEnabled()) return;

        if (config.isObjectiveCompleteRequireEventCompletion() && !eventCompleted) return;

        Map<String, Double> distribution = config.getObjectiveCompletePointDistribution();
        if (distribution != null && !distribution.isEmpty()) {
            for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                addPoints(player, entry.getKey(), entry.getValue());
            }
        } else {
            
            addPoints(player, config.getObjectiveCompletePointTypes().getFirst(), config.getObjectiveCompleteBasePoints());
        }
    }

    public void handlePlaytimeTick(Player player) {
        if (!config.isPlaytimeEnabled()) return;

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(now));
        if (!today.equals(lastPlaytimeDate.getOrDefault(playerId, ""))) {
            dailyPlaytimePoints.put(playerId, 0);
            lastPlaytimeDate.put(playerId, today);
            playtimeTicks.put(playerId, 0);
            LOGGER.info("[Playtime] Reset daily cap for " + player.getName());
        }

        int earnedToday = dailyPlaytimePoints.getOrDefault(playerId, 0);
        if (earnedToday >= config.getPlaytimeDailyCap()) {
            LOGGER.info("[Playtime] " + player.getName() + " reached daily cap: " + earnedToday);
            return;
        }

        if (config.isPlaytimeRequireActivity()) {
            long lastActive = lastActivity.getOrDefault(playerId, 0L);

            if (lastActive != 0L && (now - lastActive) / 1000 > config.getPlaytimeActivityThreshold()) {
                return;
            }
        }

        int ticks = playtimeTicks.getOrDefault(playerId, 0) + 1;
        playtimeTicks.put(playerId, ticks);

        int ticksPerMinute = 1200;
        if (ticks >= ticksPerMinute) {
            int pointsPerMinute = config.getPlaytimePointsPerMinutes();

            LOGGER.info("[Playtime] Awarding " + pointsPerMinute + " points to " + player.getName() + " for 1 minute");

            Map<String, Double> distribution = config.getPlaytimePointDistribution();
            if (distribution != null && !distribution.isEmpty()) {
                for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                    addPoints(player, entry.getKey(), entry.getValue() * pointsPerMinute);
                }
            } else {
                addPoints(player, config.getPlaytimePointTypes().getFirst(), pointsPerMinute);
            }

            dailyPlaytimePoints.put(playerId, earnedToday + pointsPerMinute);
            playtimeTicks.put(playerId, ticks % ticksPerMinute);
        }
    }

    public void updateActivity(UUID playerId) {
        lastActivity.put(playerId, System.currentTimeMillis());
    }

    private void addPoints(Player player, String pointType, double amount) {
        if (amount <= 0) return;

        int pointsToAdd = (int) Math.floor(amount);
        if (pointsToAdd <= 0) return;

        String resolvedPointType = config.getPointTypeResolver().resolve(pointType);
        String displayName = config.getPointTypeResolver().getDisplayName(resolvedPointType);

        PlayerSkillProgressImpl progress = storage.getOrCreateProgress(player.getUniqueId());
        progress.addEarnedPoints(resolvedPointType, pointsToAdd);

        LOGGER.info("Added " + pointsToAdd + " " + displayName + " to " + player.getName());
        plugin.getMessenger().sendPointsGranted(player, pointsToAdd, displayName);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getEventBridge().sendSkillDataToPlayer(player);
            }
        });
    }

    private boolean isOnCooldown(String key, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;

        Long lastTime = cooldowns.get(key);
        if (lastTime == null) return false;

        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        return elapsed < cooldownSeconds;
    }

    private long getRemainingCooldown(String key, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;

        Long lastTime = cooldowns.get(key);
        if (lastTime == null) return 0;

        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        long remaining = cooldownSeconds - elapsed;
        return Math.max(0, remaining);
    }

    private String getEntityId(Entity entity) {
        return entity.getType().getKey().toString();
    }

    private boolean isNaturalSpawn(LivingEntity entity) {
        try {
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason =
                entity.getEntitySpawnReason();
            return switch (reason) {
                case NATURAL, CHUNK_GEN, DEFAULT -> true;
                case SPAWNER, SPAWNER_EGG, BREEDING, CURED, DISPENSE_EGG,
                     DUPLICATION, JOCKEY, MOUNT, NETHER_PORTAL, RAID,
                     REINFORCEMENTS, SHEARED, SILVERFISH_BLOCK, SLIME_SPLIT,
                     SPELL, VILLAGE_DEFENSE, VILLAGE_INVASION -> false;
                default -> true;
            };
        } catch (Exception e) {
            return true; 
        }
    }

    private int applyMultipliers(Map<String, Integer> multipliers, String resourceId, int basePoints) {
        
        Integer multiplier = multipliers.get(resourceId);
        if (multiplier != null) {
            return basePoints * multiplier;
        }

        for (Map.Entry<String, Integer> entry : multipliers.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("#")) {
                if (isInTag(resourceId, key)) {
                    return basePoints * entry.getValue();
                }
            }
        }

        return basePoints;
    }

    private boolean isInTag(String resourceId, String tagId) {
        try {
            String tagKey = tagId.substring(1); 
            NamespacedKey namespacedKey = NamespacedKey.fromString(tagKey);

            Tag<org.bukkit.entity.EntityType> entityTag = Bukkit.getTag(org.bukkit.Tag.REGISTRY_ENTITY_TYPES, namespacedKey, org.bukkit.entity.EntityType.class);
            if (entityTag != null) {
                try {
                    org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(resourceId.toUpperCase());
                    return entityTag.isTagged(entityType);
                } catch (IllegalArgumentException ignored) {}
            }

            Tag<org.bukkit.Material> blockTag = Bukkit.getTag(org.bukkit.Tag.REGISTRY_BLOCKS, namespacedKey, org.bukkit.Material.class);
            if (blockTag != null) {
                try {
                    org.bukkit.Material material = org.bukkit.Material.matchMaterial(resourceId);
                    if (material != null && material.isBlock()) {
                        return blockTag.isTagged(material);
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            Tag<org.bukkit.Material> itemTag = Bukkit.getTag(org.bukkit.Tag.REGISTRY_ITEMS, namespacedKey, org.bukkit.Material.class);
            if (itemTag != null) {
                try {
                    org.bukkit.Material material = org.bukkit.Material.matchMaterial(resourceId);
                    if (material != null) {
                        return itemTag.isTagged(material);
                    }
                } catch (IllegalArgumentException ignored) {}
            }

        } catch (Exception e) {
            LOGGER.warning("Failed to check tag " + tagId + " for resource " + resourceId + ": " + e.getMessage());
        }
        return false;
    }

    public void cleanup() {
        cooldowns.clear();
        lastActivity.clear();
        dailyPlaytimePoints.clear();
        lastPlaytimeDate.clear();
        playtimeTicks.clear();
    }
}
