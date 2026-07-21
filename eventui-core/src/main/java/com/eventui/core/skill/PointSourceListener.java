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

    public PointSourceListener(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    private PointSourceManager getPointSourceManager() {
        return plugin.getPointSourceManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() > event.getOldLevel()) {
            getPointSourceManager().handleXpGain(event.getPlayer(), event.getNewLevel() - event.getOldLevel(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() instanceof Player) {
            getPointSourceManager().handleMobKill(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getPlayer().getKiller();
        if (killer != null) {
            getPointSourceManager().handlePlayerKill(killer, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String blockId = block.getType().getKey().toString();

        SkillSourcesConfig config = plugin.getSkillSourcesConfig();
        if (config != null && config.isBlockMineRequireCorrectTool()) {
            ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
            if (!isCorrectTool(block, tool)) {
                return;
            }
        }

        getPointSourceManager().handleBlockMine(event.getPlayer(), blockId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        getPointSourceManager().handleFishing(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakCrop(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isCrop(block)) return;

        boolean isMature = false;
        if (block.getBlockData() instanceof Ageable ageable) {
            isMature = ageable.getAge() == ageable.getMaximumAge();
        }

        String cropId = block.getType().getKey().toString();
        boolean isManual = true; 

        getPointSourceManager().handleCropHarvest(event.getPlayer(), cropId, isMature, isManual);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;

        Entity child = event.getEntity();
        String entityId = child.getType().getKey().toString();

        getPointSourceManager().handleAnimalBreed(player, entityId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        getPointSourceManager().updateActivity(event.getPlayer().getUniqueId());
        getPointSourceManager().handlePlaytimeTick(event.getPlayer());
    }

    private boolean isCorrectTool(Block block, ItemStack tool) {
        Material blockType = block.getType();
        Material toolType = tool.getType();

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
