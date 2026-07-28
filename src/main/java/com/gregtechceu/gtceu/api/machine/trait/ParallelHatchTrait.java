package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * MultiTrait that discovers a parallel-control hatch from the multiblock's parts
 * during structure formation.
 * <p>
 * Attach this trait to any multiblock that should support parallel recipe processing.
 * The controller's parallel-hatch lookup can then delegate to this trait instead of
 * tracking the hatch manually.
 * </p>
 */
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

    public int getCurrentParallel() {
        return parallelHatch != null ? parallelHatch.getCurrentParallel() : 1;
    }
}
