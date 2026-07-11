package com.eventui.core.skill;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

public class SkillSourcesConfig {

    private static final Logger LOGGER = Logger.getLogger("EventUI");

    private final boolean xpConversionEnabled;
    private final int levelsPerPoint;
    private final String xpPointType;
    private final boolean onlyOnLevelUp;

    public SkillSourcesConfig(FileConfiguration config) {
        this.xpConversionEnabled = config.getBoolean("skills.point_sources.xp_conversion.enabled", true);
        this.levelsPerPoint = Math.max(1, config.getInt("skills.point_sources.xp_conversion.levels_per_point", 1));
        this.xpPointType = config.getString("skills.point_sources.xp_conversion.point_type", "combat_points");
        this.onlyOnLevelUp = config.getBoolean("skills.point_sources.xp_conversion.only_on_level_up", true);
    }

    public boolean isXpConversionEnabled() {
        return xpConversionEnabled;
    }

    public int getLevelsPerPoint() {
        return levelsPerPoint;
    }

    public String getXpPointType() {
        return xpPointType;
    }

    public boolean isOnlyOnLevelUp() {
        return onlyOnLevelUp;
    }
}
