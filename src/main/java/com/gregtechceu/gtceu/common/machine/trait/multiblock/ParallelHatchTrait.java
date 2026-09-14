package com.gregtechceu.gtceu.common.machine.trait.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IMultiblockMachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IParallelTrait;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class ParallelHatchTrait extends MachineTrait implements IMultiblockMachineTrait, IParallelTrait {

    @Getter
    private @Nullable IParallelHatch parallelHatch = null;

    public ParallelHatchTrait(MetaMachine machine) {
        super(machine);
    }

    @Override
    public int getCurrentParallel() {
        return parallelHatch == null ? 0 : parallelHatch.getCurrentParallel();
    }

    @Override
    public int jadePriority() {
        return 600;
    }

    @Override
    public void writeJadeData(CompoundTag data, BlockAccessor accessor) {
        if (getCurrentParallel() > 1) data.putInt("parallel", getCurrentParallel());
    }

    @Override
    public void appendJadeTooltip(CompoundTag data, ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (data.contains("parallel")) {
            tooltip.add(Component.translatable("gtceu.multiblock.parallel",
                    Component.literal(Integer.toString(data.getInt("parallel")))
                            .withStyle(ChatFormatting.DARK_PURPLE)));
        }
    }

    @Override
    public void onStructureFormed() {
        IMultiblockMachineTrait.super.onStructureFormed();
        for (var part : getMultiMachine().getParts()) {
            if (part instanceof IParallelHatch pHatch) {
                parallelHatch = pHatch;
                break;
            }
        }
    }

    @Override
    public void onStructureInvalid() {
        IMultiblockMachineTrait.super.onStructureInvalid();
        parallelHatch = null;
    }
}
