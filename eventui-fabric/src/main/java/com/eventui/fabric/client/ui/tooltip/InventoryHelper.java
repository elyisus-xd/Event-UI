package com.eventui.fabric.client.ui.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper para comprobar si el jugador tiene ingredientes en su inventario.
 * No cachea el inventario — lee en tiempo real cada llamada, ya que el
 * inventario puede cambiar en cualquier momento.
 *
 * El rendimiento es aceptable porque solo se consulta para los ingredientes
 * visibles en un tooltip abierto (típicamente 1–9 ingredientes).
 */
public class InventoryHelper {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryHelper.class);

    /**
     * @return true si el jugador tiene al menos uno de los items del ingrediente.
     *         true también si no hay jugador (asumimos que lo tiene para no oscurecer).
     */
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
