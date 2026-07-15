package com.eventui.core.skill;

import com.eventui.core.EventUIPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class PointSourceListener implements Listener {

    private final EventUIPlugin plugin;
    private final PointSourceManager pointSourceManager;

    public PointSourceListener(EventUIPlugin plugin, PointSourceManager pointSourceManager) {
        this.plugin = plugin;
        this.pointSourceManager = pointSourceManager;
    }

    // ── XP Conversion ──────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() > event.getOldLevel()) {
            pointSourceManager.handleXpGain(event.getPlayer(), event.getNewLevel() - event.getOldLevel(), true);
        }
    }

    // ── Mob Kill ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() instanceof Player) {
            pointSourceManager.handleMobKill(event);
        }
    }

    // ── Player Kill ───────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getPlayer().getKiller();
        if (killer != null) {
            pointSourceManager.handlePlayerKill(killer, event.getPlayer());
        }
    }

    // ── Block Mine ───────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getPlayer().hasPermission("eventui.skills.points")) return;

        Block block = event.getBlock();
        String blockId = block.getType().getKey().toString();

        // Check correct tool requirement
        SkillSourcesConfig config = plugin.getSkillSourcesConfig();
        if (config != null && config.isBlockMineRequireCorrectTool()) {
            ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
            if (!isCorrectTool(block, tool)) {
                return;
            }
        }

        pointSourceManager.handleBlockMine(event.getPlayer(), blockId);
    }

    // ── Fishing ───────────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        pointSourceManager.handleFishing(event);
    }

    // ── Crop Harvest ─────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakCrop(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isCrop(block)) return;

        boolean isMature = false;
        if (block.getBlockData() instanceof Ageable ageable) {
            isMature = ageable.getAge() == ageable.getMaximumAge();
        }

        String cropId = block.getType().getKey().toString();
        boolean isManual = true; // Simplified - could check for dispenser

        pointSourceManager.handleCropHarvest(event.getPlayer(), cropId, isMature, isManual);
    }

    // ── Animal Breed ─────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;

        Entity child = event.getEntity();
        String entityId = child.getType().getKey().toString();

        pointSourceManager.handleAnimalBreed(player, entityId);
    }

    // ── Playtime Activity Tracking ─────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only track significant movement
        if (event.getFrom().getX() == event.getTo().getX() &&
            event.getFrom().getY() == event.getTo().getY() &&
            event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }

        pointSourceManager.handlePlaytimeTick(event.getPlayer());
    }

    // ── Helper Methods ────────────────────────────────────────

    private boolean isCorrectTool(Block block, ItemStack tool) {
        Material blockType = block.getType();
        Material toolType = tool.getType();

        // Simplified tool check - could be more comprehensive
        if (blockType.name().endsWith("_ORE") || blockType.name().endsWith("_LOG")) {
            return toolType.name().endsWith("_PICKAXE") || toolType.name().endsWith("_AXE");
        }

        return true;
    }

    private boolean isCrop(Block block) {
        return switch (block.getType()) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART,
                 COCOA, SWEET_BERRY_BUSH, PITCHER_CROP -> true;
            default -> false;
        };
    }
}
