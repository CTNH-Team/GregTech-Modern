package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.trait.multiblock.CoilMachineTrait;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CoilWorkableElectricMultiblockMachine extends RecipeElectricMultiblockMachine {

    public CoilWorkableElectricMultiblockMachine(IMachineBlockEntity holder) {
        super(holder);
        attachTrait(new CoilMachineTrait(this));
    }
}
