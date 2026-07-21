package com.eventui.fabric.client.ui.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryHelper {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryHelper.class);

    public static boolean playerHasIngredient(Ingredient ingredient) {
        var client = Minecraft.getInstance();
        if (client.player == null) return true;

        var inventory = client.player.getInventory();
        ItemStack[] acceptedItems = ingredient.getItems();
        for (ItemStack needed : acceptedItems) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && stack.getItem() == needed.getItem()) {
                    return true;
                }
            }
        }
        return false;
    }
}
