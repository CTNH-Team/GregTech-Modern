package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * MultiTrait that captures the multiblock's heating coil type during structure formation.
 * <p>
 * Attach this trait to any multiblock whose recipe modifiers depend on coil tier,
 * temperature, level, or energy discount. The trait reads the {@code "CoilType"} entry
 * from the match context and exposes it through {@link #getCoilType()} and
 * {@link #getCoilTier()}.
 * </p>
 */
public class CoilTrait extends MultiTrait {

    @Getter
    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;

    public CoilTrait(@NotNull MetaMachine machine) {
        super(machine);
    }

    @Override
    public void onStructureFormed() {
        var ctx = getController().getMultiblockState().getMatchContext();
        var type = ctx.get("CoilType");
        if (type instanceof ICoilType coil) {
            this.coilType = coil;
        }
    }

    @Override
    public void onStructureInvalid() {
        this.coilType = CoilBlock.CoilType.CUPRONICKEL;
    }

    public int getCoilTier() {
        return coilType.getTier();
    }

    public int getCoilTemperature() {
        return coilType.getCoilTemperature();
    }

    public int getCoilLevel() {
        return coilType.getLevel();
    }

    public int getCoilEnergyDiscount() {
        return coilType.getEnergyDiscount();
    }
}
