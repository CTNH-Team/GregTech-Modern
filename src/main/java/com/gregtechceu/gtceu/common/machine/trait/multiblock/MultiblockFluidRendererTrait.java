package com.gregtechceu.gtceu.common.machine.trait.multiblock;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IMultiblockMachineTrait;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Synchronizes the fluid-rendering area derived from a multiblock's formed structure. */
public class MultiblockFluidRendererTrait extends MachineTrait implements IMultiblockMachineTrait {

    private final Supplier<Set<BlockPos>> offsetSupplier;
    @DescSynced
    @RequireRerender
    private Set<BlockPos> fluidOffsets = new HashSet<>();

    public MultiblockFluidRendererTrait(MultiblockControllerMachine machine, Supplier<Set<BlockPos>> offsetSupplier) {
        super(machine);
        this.offsetSupplier = offsetSupplier;
    }

    public @NotNull Set<BlockPos> getFluidOffsets() {
        return fluidOffsets;
    }

    @Override
    public void onStructureFormed() {
        fluidOffsets = new HashSet<>(offsetSupplier.get());
    }

    @Override
    public void onStructureInvalid() {
        fluidOffsets.clear();
    }

    @Override
    public void onPartUnload() {
        fluidOffsets.clear();
    }
}
