package com.gregtechceu.gtceu.common.machine.trait.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IMultiblockMachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IParallelTrait;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

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
