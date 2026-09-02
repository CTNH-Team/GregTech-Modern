package com.gregtechceu.gtceu.common.machine.trait.multiblock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.multiblock.RecipeElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IMultiblockMachineTrait;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import lombok.Getter;

import java.util.List;

/** Exposes the heating-coil properties captured from a formed electric multiblock. */
public class CoilMachineTrait extends MachineTrait implements IMultiblockMachineTrait {

    @Getter
    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;

    public CoilMachineTrait(RecipeElectricMultiblockMachine machine) {
        super(machine);
    }

    @Override
    public RecipeElectricMultiblockMachine getMachine() {
        return (RecipeElectricMultiblockMachine) super.getMachine();
    }

    @Override
    protected List<Class<?>> validMachineClasses() {
        return List.of(RecipeElectricMultiblockMachine.class);
    }

    @Override
    public void onStructureFormed() {
        var type = getMachine().getMultiblockState().getMatchContext().get("CoilType");
        if (type instanceof ICoilType coil) {
            coilType = coil;
        }
    }

    @Override
    public void onStructureInvalid() {
        coilType = CoilBlock.CoilType.CUPRONICKEL;
    }

    @Override
    public void onPartUnload() {
        coilType = CoilBlock.CoilType.CUPRONICKEL;
    }

    public int getCoilTier() {
        return coilType.getTier();
    }

    public int getWorkingTemperature() {
        return coilType.getCoilTemperature() + 100 * Math.max(0, getMachine().getTier() - GTValues.MV);
    }

    public long getOverclockVoltage() {
        return getMachine().getOverclockVoltage();
    }
}
