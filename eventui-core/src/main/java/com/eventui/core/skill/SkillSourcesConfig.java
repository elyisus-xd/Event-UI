package com.eventui.core.skill;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;
import java.util.logging.Logger;

public class SkillSourcesConfig {

    private static final Logger LOGGER = Logger.getLogger("EventUI");
    private final PointTypeResolver pointTypeResolver;

    private final boolean xpConversionEnabled;
    private final int levelsPerPoint;
    private final List<String> xpPointTypes;
    private final Map<String, Double> xpPointDistribution;
    private final boolean onlyOnLevelUp;

    private final boolean mobKillEnabled;
    private final List<String> mobKillPointTypes;
    private final int mobKillBasePoints;
    private final Map<String, Double> mobKillPointDistribution;
    private final Map<String, Integer> mobKillMultipliers;
    private final boolean mobKillNaturalOnly;
    private final Map<String, Integer> mobKillCooldowns;
    private final List<String> mobKillAllowedMobs;

    private final boolean playerKillEnabled;
    private final List<String> playerKillPointTypes;
    private final int playerKillPoints;
    private final Map<String, Double> playerKillPointDistribution;
    private final int playerKillCooldownPerVictim;

    private final boolean blockMineEnabled;
    private final List<String> blockMinePointTypes;
    private final int blockMineBasePoints;
    private final Map<String, Double> blockMinePointDistribution;
    private final Map<String, Integer> blockMineMultipliers;
    private final boolean blockMineRequireCorrectTool;
    private final int blockMineCooldownPerBlock;
    private final List<String> blockMineAllowedBlocks;

    private final boolean fishingEnabled;
    private final List<String> fishingPointTypes;
    private final int fishingBasePoints;
    private final Map<String, Double> fishingPointDistribution;
    private final Map<String, Integer> fishingMultipliers;
    private final boolean fishingLuckBonus;

    private final boolean cropHarvestEnabled;
    private final List<String> cropHarvestPointTypes;
    private final int cropHarvestBasePoints;
    private final Map<String, Double> cropHarvestPointDistribution;
    private final Map<String, Integer> cropHarvestMultipliers;
    private final boolean cropHarvestRequireMature;
    private final boolean cropHarvestManualOnly;

    private final boolean animalBreedEnabled;
    private final List<String> animalBreedPointTypes;
    private final int animalBreedBasePoints;
    private final Map<String, Double> animalBreedPointDistribution;
    private final Map<String, Integer> animalBreedMultipliers;
    private final int animalBreedCooldownPerEntity;

    private final boolean eventCompleteEnabled;
    private final List<String> eventCompletePointTypes;
    private final int eventCompleteBasePoints;
    private final Map<String, Double> eventCompletePointDistribution;
    private final Map<String, Integer> eventCompleteDifficultyMultipliers;
    private final boolean eventCompleteRequireCompletion;

    private final boolean objectiveCompleteEnabled;
    private final List<String> objectiveCompletePointTypes;
    private final int objectiveCompleteBasePoints;
    private final Map<String, Double> objectiveCompletePointDistribution;
    private final boolean objectiveCompleteRequireEventCompletion;

    private final boolean playtimeEnabled;
    private final List<String> playtimePointTypes;
    private final int playtimePointsPerMinutes;
    private final Map<String, Double> playtimePointDistribution;
    private final int playtimeDailyCap;
    private final boolean playtimeRequireActivity;
    private final int playtimeActivityThreshold;

    public SkillSourcesConfig(FileConfiguration config) {
        this.pointTypeResolver = new PointTypeResolver(config);

        ConfigurationSection pointSources = config.getConfigurationSection("skills.point_sources");
        if (pointSources == null) {
            LOGGER.warning("skills.point_sources section not found in config, using defaults");
            pointSources = config.createSection("skills.point_sources");
        }

        ConfigurationSection xpSection = pointSources.getConfigurationSection("xp_conversion");
        this.xpConversionEnabled = xpSection != null && xpSection.getBoolean("enabled", true);
        this.levelsPerPoint = Math.max(1, xpSection != null ? xpSection.getInt("levels_per_point", 1) : 1);
        this.xpPointTypes = loadPointTypes(xpSection, "skill_points");
        this.xpPointDistribution = loadPointDistribution(xpSection, this.xpPointTypes, this.levelsPerPoint);
        this.onlyOnLevelUp = xpSection != null && xpSection.getBoolean("only_on_level_up", true);

        ConfigurationSection mobKillSection = pointSources.getConfigurationSection("mob_kill");
        this.mobKillEnabled = mobKillSection != null && mobKillSection.getBoolean("enabled", true);
        this.mobKillPointTypes = loadPointTypes(mobKillSection, "combat_points");
        this.mobKillBasePoints = mobKillSection != null ? mobKillSection.getInt("base_points", 1) : 1;
        this.mobKillPointDistribution = loadPointDistribution(mobKillSection, this.mobKillPointTypes, this.mobKillBasePoints);
        this.mobKillMultipliers = loadMultipliers(mobKillSection, "multipliers");
        this.mobKillNaturalOnly = mobKillSection != null && mobKillSection.getBoolean("natural_only", true);
        this.mobKillCooldowns = loadMultipliers(mobKillSection, "cooldowns_per_mob");
        this.mobKillAllowedMobs = mobKillSection != null && mobKillSection.isList("allowed_mobs")
                ? mobKillSection.getStringList("allowed_mobs")
                : null;

        ConfigurationSection playerKillSection = pointSources.getConfigurationSection("player_kill");
        this.playerKillEnabled = playerKillSection != null && playerKillSection.getBoolean("enabled", true);
        this.playerKillPointTypes = loadPointTypes(playerKillSection, "combat_points");
        this.playerKillPoints = playerKillSection != null ? playerKillSection.getInt("points_per_kill", 5) : 5;
        this.playerKillPointDistribution = loadPointDistribution(playerKillSection, this.playerKillPointTypes, this.playerKillPoints);
        this.playerKillCooldownPerVictim = playerKillSection != null ? playerKillSection.getInt("cooldown_per_victim_seconds", 300) : 300;

        ConfigurationSection blockMineSection = pointSources.getConfigurationSection("block_mine");
        this.blockMineEnabled = blockMineSection != null && blockMineSection.getBoolean("enabled", true);
        this.blockMinePointTypes = loadPointTypes(blockMineSection, "gathering_points");
        this.blockMineBasePoints = blockMineSection != null ? blockMineSection.getInt("base_points", 1) : 1;
        this.blockMinePointDistribution = loadPointDistribution(blockMineSection, this.blockMinePointTypes, this.blockMineBasePoints);
        this.blockMineMultipliers = loadMultipliers(blockMineSection, "multipliers");
        this.blockMineRequireCorrectTool = blockMineSection != null && blockMineSection.getBoolean("require_correct_tool", true);
        this.blockMineCooldownPerBlock = blockMineSection != null ? blockMineSection.getInt("cooldown_per_block_seconds", 0) : 0;
        this.blockMineAllowedBlocks = blockMineSection != null && blockMineSection.isList("allowed_blocks")
                ? blockMineSection.getStringList("allowed_blocks")
                : null;

        ConfigurationSection fishingSection = pointSources.getConfigurationSection("fishing");
        this.fishingEnabled = fishingSection != null && fishingSection.getBoolean("enabled", true);
        this.fishingPointTypes = loadPointTypes(fishingSection, "gathering_points");
        this.fishingBasePoints = fishingSection != null ? fishingSection.getInt("base_points", 1) : 1;
        this.fishingPointDistribution = loadPointDistribution(fishingSection, this.fishingPointTypes, this.fishingBasePoints);
        this.fishingMultipliers = loadMultipliers(fishingSection, "multipliers");
        this.fishingLuckBonus = fishingSection != null && fishingSection.getBoolean("luck_bonus", true);

        ConfigurationSection cropHarvestSection = pointSources.getConfigurationSection("crop_harvest");
        this.cropHarvestEnabled = cropHarvestSection != null && cropHarvestSection.getBoolean("enabled", true);
        this.cropHarvestPointTypes = loadPointTypes(cropHarvestSection, "gathering_points");
        this.cropHarvestBasePoints = cropHarvestSection != null ? cropHarvestSection.getInt("base_points", 1) : 1;
        this.cropHarvestPointDistribution = loadPointDistribution(cropHarvestSection, this.cropHarvestPointTypes, this.cropHarvestBasePoints);
        this.cropHarvestMultipliers = loadMultipliers(cropHarvestSection, "multipliers");
        this.cropHarvestRequireMature = cropHarvestSection != null && cropHarvestSection.getBoolean("require_mature", true);
        this.cropHarvestManualOnly = cropHarvestSection != null && cropHarvestSection.getBoolean("manual_only", false);

        ConfigurationSection animalBreedSection = pointSources.getConfigurationSection("animal_breed");
        this.animalBreedEnabled = animalBreedSection != null && animalBreedSection.getBoolean("enabled", true);
        this.animalBreedPointTypes = loadPointTypes(animalBreedSection, "gathering_points");
        this.animalBreedBasePoints = animalBreedSection != null ? animalBreedSection.getInt("base_points", 2) : 2;
        this.animalBreedPointDistribution = loadPointDistribution(animalBreedSection, this.animalBreedPointTypes, this.animalBreedBasePoints);
        this.animalBreedMultipliers = loadMultipliers(animalBreedSection, "multipliers");
        this.animalBreedCooldownPerEntity = animalBreedSection != null ? animalBreedSection.getInt("cooldown_per_entity_seconds", 0) : 0;

        ConfigurationSection eventCompleteSection = pointSources.getConfigurationSection("event_complete");
        this.eventCompleteEnabled = eventCompleteSection != null && eventCompleteSection.getBoolean("enabled", true);
        this.eventCompletePointTypes = loadPointTypes(eventCompleteSection, "skill_points");
        this.eventCompleteBasePoints = eventCompleteSection != null ? eventCompleteSection.getInt("base_points", 10) : 10;
        this.eventCompletePointDistribution = loadPointDistribution(eventCompleteSection, this.eventCompletePointTypes, this.eventCompleteBasePoints);
        this.eventCompleteDifficultyMultipliers = loadMultipliers(eventCompleteSection, "difficulty_multipliers");
        this.eventCompleteRequireCompletion = eventCompleteSection != null && eventCompleteSection.getBoolean("require_completion", true);

        ConfigurationSection objectiveCompleteSection = pointSources.getConfigurationSection("objective_complete");
        this.objectiveCompleteEnabled = objectiveCompleteSection != null && objectiveCompleteSection.getBoolean("enabled", true);
        this.objectiveCompletePointTypes = loadPointTypes(objectiveCompleteSection, "skill_points");
        this.objectiveCompleteBasePoints = objectiveCompleteSection != null ? objectiveCompleteSection.getInt("base_points", 2) : 2;
        this.objectiveCompletePointDistribution = loadPointDistribution(objectiveCompleteSection, this.objectiveCompletePointTypes, this.objectiveCompleteBasePoints);
        this.objectiveCompleteRequireEventCompletion = objectiveCompleteSection != null && objectiveCompleteSection.getBoolean("require_event_completion", false);

        ConfigurationSection playtimeSection = pointSources.getConfigurationSection("playtime");
        this.playtimeEnabled = playtimeSection != null && playtimeSection.getBoolean("enabled", false);
        this.playtimePointTypes = loadPointTypes(playtimeSection, "skill_points");
        this.playtimePointsPerMinutes = playtimeSection != null ? playtimeSection.getInt("points_per_minutes", 30) : 30;
        this.playtimePointDistribution = loadPointDistribution(playtimeSection, this.playtimePointTypes, this.playtimePointsPerMinutes);
        this.playtimeDailyCap = playtimeSection != null ? playtimeSection.getInt("daily_cap", 10) : 10;
        this.playtimeRequireActivity = playtimeSection != null && playtimeSection.getBoolean("require_activity", true);
        this.playtimeActivityThreshold = playtimeSection != null ? playtimeSection.getInt("activity_threshold_seconds", 60) : 60;
    }

    private Map<String, Integer> loadMultipliers(ConfigurationSection section, String key) {
        Map<String, Integer> multipliers = new HashMap<>();
        if (section == null) return multipliers;

        ConfigurationSection multipliersSection = section.getConfigurationSection(key);
        if (multipliersSection == null) return multipliers;

        for (String multiplierKey : multipliersSection.getKeys(false)) {
            multipliers.put(multiplierKey, multipliersSection.getInt(multiplierKey, 1));
        }
        return multipliers;
    }

    private List<String> loadPointTypes(ConfigurationSection section, String defaultType) {
        if (section == null) return List.of(pointTypeResolver.resolve(defaultType));

        if (section.isList("point_types")) {
            List<String> types = section.getStringList("point_types");
            List<String> resolved = new ArrayList<>();
            for (String type : types) {
                resolved.add(pointTypeResolver.resolve(type));
            }
            return resolved;
        }

        String singleType = section.getString("point_type");
        if (singleType != null && !singleType.isEmpty()) {
            return List.of(pointTypeResolver.resolve(singleType));
        }

        return List.of(pointTypeResolver.resolve(defaultType));
    }

    private Map<String, Double> loadPointDistribution(ConfigurationSection section, List<String> pointTypes, double defaultValue) {
        Map<String, Double> distribution = new HashMap<>();
        if (section == null) {
            
            for (String type : pointTypes) {
                distribution.put(pointTypeResolver.resolve(type), defaultValue);
            }
            return distribution;
        }

        if (section.isConfigurationSection("point_distribution")) {
            ConfigurationSection distSection = section.getConfigurationSection("point_distribution");
            for (String key : distSection.getKeys(false)) {
                distribution.put(pointTypeResolver.resolve(key), distSection.getDouble(key, 0.0));
            }
            return distribution;
        }

        for (String type : pointTypes) {
            distribution.put(pointTypeResolver.resolve(type), defaultValue);
        }
        return distribution;
    }

    public boolean isXpConversionEnabled() { return xpConversionEnabled; }
    public int getLevelsPerPoint() { return levelsPerPoint; }
    public List<String> getXpPointTypes() { return Collections.unmodifiableList(xpPointTypes); }
    public Map<String, Double> getXpPointDistribution() { return Collections.unmodifiableMap(xpPointDistribution); }
    public boolean isOnlyOnLevelUp() { return onlyOnLevelUp; }

    public boolean isMobKillEnabled() { return mobKillEnabled; }
    public List<String> getMobKillPointTypes() { return Collections.unmodifiableList(mobKillPointTypes); }
    public int getMobKillBasePoints() { return mobKillBasePoints; }
    public Map<String, Double> getMobKillPointDistribution() { return Collections.unmodifiableMap(mobKillPointDistribution); }
    public Map<String, Integer> getMobKillMultipliers() { return Collections.unmodifiableMap(mobKillMultipliers); }
    public boolean isMobKillNaturalOnly() { return mobKillNaturalOnly; }
    public Map<String, Integer> getMobKillCooldowns() { return Collections.unmodifiableMap(mobKillCooldowns); }
    public List<String> getMobKillAllowedMobs() { return mobKillAllowedMobs != null ? Collections.unmodifiableList(mobKillAllowedMobs) : null; }

    public boolean isPlayerKillEnabled() { return playerKillEnabled; }
    public List<String> getPlayerKillPointTypes() { return Collections.unmodifiableList(playerKillPointTypes); }
    public int getPlayerKillPoints() { return playerKillPoints; }
    public Map<String, Double> getPlayerKillPointDistribution() { return Collections.unmodifiableMap(playerKillPointDistribution); }
    public int getPlayerKillCooldownPerVictim() { return playerKillCooldownPerVictim; }

    public boolean isBlockMineEnabled() { return blockMineEnabled; }
    public List<String> getBlockMinePointTypes() { return Collections.unmodifiableList(blockMinePointTypes); }
    public int getBlockMineBasePoints() { return blockMineBasePoints; }
    public Map<String, Double> getBlockMinePointDistribution() { return Collections.unmodifiableMap(blockMinePointDistribution); }
    public Map<String, Integer> getBlockMineMultipliers() { return Collections.unmodifiableMap(blockMineMultipliers); }
    public boolean isBlockMineRequireCorrectTool() { return blockMineRequireCorrectTool; }
    public int getBlockMineCooldownPerBlock() { return blockMineCooldownPerBlock; }
    public List<String> getBlockMineAllowedBlocks() { return blockMineAllowedBlocks != null ? Collections.unmodifiableList(blockMineAllowedBlocks) : null; }

    public boolean isFishingEnabled() { return fishingEnabled; }
    public List<String> getFishingPointTypes() { return Collections.unmodifiableList(fishingPointTypes); }
    public int getFishingBasePoints() { return fishingBasePoints; }
    public Map<String, Double> getFishingPointDistribution() { return Collections.unmodifiableMap(fishingPointDistribution); }
    public Map<String, Integer> getFishingMultipliers() { return Collections.unmodifiableMap(fishingMultipliers); }
    public boolean isFishingLuckBonus() { return fishingLuckBonus; }

    public boolean isCropHarvestEnabled() { return cropHarvestEnabled; }
    public List<String> getCropHarvestPointTypes() { return Collections.unmodifiableList(cropHarvestPointTypes); }
    public int getCropHarvestBasePoints() { return cropHarvestBasePoints; }
    public Map<String, Double> getCropHarvestPointDistribution() { return Collections.unmodifiableMap(cropHarvestPointDistribution); }
    public Map<String, Integer> getCropHarvestMultipliers() { return Collections.unmodifiableMap(cropHarvestMultipliers); }
    public boolean isCropHarvestRequireMature() { return cropHarvestRequireMature; }
    public boolean isCropHarvestManualOnly() { return cropHarvestManualOnly; }

    public boolean isAnimalBreedEnabled() { return animalBreedEnabled; }
    public List<String> getAnimalBreedPointTypes() { return Collections.unmodifiableList(animalBreedPointTypes); }
    public int getAnimalBreedBasePoints() { return animalBreedBasePoints; }
    public Map<String, Double> getAnimalBreedPointDistribution() { return Collections.unmodifiableMap(animalBreedPointDistribution); }
    public Map<String, Integer> getAnimalBreedMultipliers() { return Collections.unmodifiableMap(animalBreedMultipliers); }
    public int getAnimalBreedCooldownPerEntity() { return animalBreedCooldownPerEntity; }

    public boolean isEventCompleteEnabled() { return eventCompleteEnabled; }
    public List<String> getEventCompletePointTypes() { return Collections.unmodifiableList(eventCompletePointTypes); }
    public int getEventCompleteBasePoints() { return eventCompleteBasePoints; }
    public Map<String, Double> getEventCompletePointDistribution() { return Collections.unmodifiableMap(eventCompletePointDistribution); }
    public Map<String, Integer> getEventCompleteDifficultyMultipliers() { return Collections.unmodifiableMap(eventCompleteDifficultyMultipliers); }
    public boolean isEventCompleteRequireCompletion() { return eventCompleteRequireCompletion; }

    public boolean isObjectiveCompleteEnabled() { return objectiveCompleteEnabled; }
    public List<String> getObjectiveCompletePointTypes() { return Collections.unmodifiableList(objectiveCompletePointTypes); }
    public int getObjectiveCompleteBasePoints() { return objectiveCompleteBasePoints; }
    public Map<String, Double> getObjectiveCompletePointDistribution() { return Collections.unmodifiableMap(objectiveCompletePointDistribution); }
    public boolean isObjectiveCompleteRequireEventCompletion() { return objectiveCompleteRequireEventCompletion; }

    public boolean isPlaytimeEnabled() { return playtimeEnabled; }
    public List<String> getPlaytimePointTypes() { return Collections.unmodifiableList(playtimePointTypes); }
    public int getPlaytimePointsPerMinutes() { return playtimePointsPerMinutes; }
    public Map<String, Double> getPlaytimePointDistribution() { return Collections.unmodifiableMap(playtimePointDistribution); }
    public int getPlaytimeDailyCap() { return playtimeDailyCap; }
    public boolean isPlaytimeRequireActivity() { return playtimeRequireActivity; }
    public int getPlaytimeActivityThreshold() { return playtimeActivityThreshold; }

    public PointTypeResolver getPointTypeResolver() { return pointTypeResolver; }
}
