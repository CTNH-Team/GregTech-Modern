package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

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
