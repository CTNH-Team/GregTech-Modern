package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ParallelHatchTrait extends MultiTrait {

    @Getter
    private @Nullable IParallelHatch parallelHatch = null;

    public ParallelHatchTrait(@NotNull MetaMachine machine) {
        super(machine);
    }

    @Override
    public void onStructureFormed() {
        for (var part : getController().getParts()) {
            if (part instanceof IParallelHatch pHatch) {
                this.parallelHatch = pHatch;
                return;
            }
        }
        this.parallelHatch = null;
    }

    @Override
    public void onStructureInvalid() {
        this.parallelHatch = null;
    }

    public Optional<IParallelHatch> getOptionalParallelHatch() {
        return Optional.ofNullable(parallelHatch);
    }
}
