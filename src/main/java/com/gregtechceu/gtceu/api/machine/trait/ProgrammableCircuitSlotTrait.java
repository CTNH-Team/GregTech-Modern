package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.trait.feature.IAttachConfiguratorsTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.item.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Owns a machine's programmable circuit input, recipe handling, persistence, and UI integration. */
public class ProgrammableCircuitSlotTrait extends NotifiableRecipeHandlerTrait<ItemIngredient>
                                          implements IAttachConfiguratorsTrait, ICapabilityTrait,
                                          IItemHandlerModifiable {

    @Getter
    @Persisted
    @DescSynced
    private final CustomItemStackHandler storage;
    @Accessors(fluent = true)
    @Getter
    @Setter
    private boolean shouldSearchContent = true;

    public ProgrammableCircuitSlotTrait(MetaMachine machine) {
        super(machine);
        this.storage = new CustomItemStackHandler(1);
        storage.setFilter(IntCircuitBehaviour::isIntegratedCircuit);
        storage.setOnContentsChanged(this::notifyListeners);
    }

    public int getCurrentCircuit() {
        return IntCircuitBehaviour.getCircuitConfiguration(storage.getStackInSlot(0));
    }

    public void setCurrentCircuit(int circuit) {
        storage.setStackInSlot(0, IntCircuitBehaviour.stack(circuit));
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel left, ConfiguratorPanel right) {
        left.attachConfigurators(new CircuitFancyConfigurator(storage));
    }

    @Override
    public IO getHandlerIO() {
        return IO.IN;
    }

    @Override
    public IO getCapabilityIO() {
        return IO.NONE;
    }

    @Override
    public boolean handleRecipe(IO io, GTRecipe recipe, List<ItemIngredient> left, boolean simulate) {
        return NotifiableItemStackHandler.handleRecipe(io, recipe, left, simulate, IO.IN, storage);
    }

    @Override
    public @NotNull List<Object> getContents() {
        if (storage.getStackInSlot(0).isEmpty()) return List.of();
        return List.of(storage.getStackInSlot(0));
    }

    @Override
    public @NotNull List<AbstractMapIngredient> getMapIngredients() {
        return new ArrayList<>(NotifiableItemStackHandler.mapItemStack(storage.getStackInSlot(0)));
    }

    @Override
    public double getTotalContentAmount() {
        return storage.getStackInSlot(0).getCount();
    }

    @Override
    public RecipeCapability<ItemIngredient> getCapability() {
        return ItemRecipeCapability.CAP;
    }

    @Override
    public void setStackInSlot(int i, @NotNull ItemStack itemStack) {}

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int i) {
        return storage.getStackInSlot(0);
    }

    @Override
    public @NotNull ItemStack insertItem(int i, @NotNull ItemStack itemStack, boolean b) {
        return itemStack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
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
