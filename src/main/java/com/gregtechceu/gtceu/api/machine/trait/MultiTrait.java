package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

/**
 * Base class for traits that participate in the multiblock lifecycle.
 * <p>
 * Unlike ordinary {@link MachineTrait}s, a MultiTrait receives callbacks when the
 * multiblock structure forms or becomes invalid.
 * </p>
 */
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
