package com.eventui.core.rewards;

import com.eventui.api.event.EventDefinition;
import com.eventui.core.EventUIPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class RewardManager {

    private static final Logger LOGGER = Logger.getLogger(RewardManager.class.getName());

    private final EventUIPlugin plugin;

    public RewardManager(EventUIPlugin plugin) {
        this.plugin = plugin;
    }

    public void giveRewards(Player player, EventDefinition eventDef) {
        try {
            String rewardsJson = eventDef.getMetadata().getOrDefault("rewards_data", "[]");
            if (rewardsJson.equals("[]") || rewardsJson.equals("{}")) {
                LOGGER.info("No rewards configured for event: " + eventDef.getId());
                return;
            }

            Gson gson = new Gson();
            List<Map<String, Object>> rewardsList = gson.fromJson(rewardsJson,
                    new TypeToken<List<Map<String, Object>>>(){}.getType());

            int rewardCount = 0;

            for (Map<String, Object> reward : rewardsList) {
                String type = (String) reward.get("type");
                if (type == null) continue;

                switch (type) {
                    case "xp" -> {
                        int xp = ((Number) reward.get("amount")).intValue();
                        player.giveExp(xp);
                        player.sendMessage("§a+ " + xp + " XP");
                        rewardCount++;
                    }
                    case "item" -> {
                        String itemId = (String) reward.get("id");
                        int count = reward.containsKey("count")
                                ? ((Number) reward.get("count")).intValue() : 1;
                        ItemStack item = parseItemString(itemId + " " + count);
                        if (item != null) {
                            var leftover = player.getInventory().addItem(item);
                            if (!leftover.isEmpty())
                                player.getWorld().dropItemNaturally(player.getLocation(), item);
                            player.sendMessage("§a+ " + count + "x "
                                    + item.getType().name().toLowerCase().replace("_", " "));
                            rewardCount++;
                        }
                    }
                    case "command" -> {
                        String cmd = ((String) reward.get("command")).replace("{player}", player.getName());
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
                        rewardCount++;
                    }
                }
            }

            if (rewardCount > 0) {
                player.sendMessage("§6✓ ¡Recibiste " + rewardCount + " recompensa(s)!");
                LOGGER.info("Gave " + rewardCount + " reward(s) to " + player.getName()
                        + " for event: " + eventDef.getId());
            }

        } catch (Exception e) {
            LOGGER.severe("Failed to give rewards for event " + eventDef.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }


    private ItemStack parseItemString(String itemString) {
        String[] parts = itemString.trim().split(" ");

        if (parts.length < 1) {
            return null;
        }

        String itemId = parts[0];
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

        String materialName = itemId.replace("minecraft:", "").toUpperCase();

        try {
            Material material = Material.valueOf(materialName);
            return new ItemStack(material, amount);

        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown material: " + materialName);
            return null;
        }
    }
}
