package com.gregtechceu.gtceu.integration.xei.handlers.item;

import com.gregtechceu.gtceu.integration.xei.entry.item.ItemStackList;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.ListEmiIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ListEmiIngredientHandler implements IItemHandlerModifiable {
    private final List<ListEmiIngredient> stacks;

    public ListEmiIngredientHandler(List<ListEmiIngredient> stacks) {
        this.stacks = stacks;
    }

    @Override
    public void setStackInSlot(int index, @NotNull ItemStack itemStack) {
        if (index >= 0 && index < stacks.size()) {
            Ingredient ingredient = Ingredient.of(itemStack);
            EmiIngredient emiIngredient = EmiIngredient.of(ingredient);
            List<EmiIngredient> emiIngredientList = new ArrayList<>();
            emiIngredientList.add(emiIngredient);
            stacks.set(index, new ListEmiIngredient(emiIngredientList,1));
        }
    }

    @Override
    public int getSlots() {
        return stacks.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int i) {
        return null;
    }

    @Override
    public @NotNull ItemStack insertItem(int i, @NotNull ItemStack itemStack, boolean b) {
        return null;
    }

    @Override
    public @NotNull ItemStack extractItem(int i, int i1, boolean b) {
        return null;
    }

    @Override
    public int getSlotLimit(int i) {
        return 0;
    }

    @Override
    public boolean isItemValid(int i, @NotNull ItemStack itemStack) {
        return false;
    }
}
