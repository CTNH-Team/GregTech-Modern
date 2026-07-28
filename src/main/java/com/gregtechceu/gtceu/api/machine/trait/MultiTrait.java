package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

public abstract class MultiTrait extends MachineTrait {

    public MultiTrait(MetaMachine machine) {
        super(machine);
    }

    public IMultiController getController() {
        return (IMultiController) getMachine();
    }

    public void onStructureFormed() {}

    public void onStructureInvalid() {}
}
