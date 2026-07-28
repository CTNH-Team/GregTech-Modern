package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

/**
 * Base class for traits that participate in the multiblock lifecycle.
 * <p>
 * Unlike ordinary {@link MachineTrait}s, a MultiTrait receives callbacks when the
 * multiblock structure forms or becomes invalid. Subclasses override
 * {@link #onStructureFormed()} and {@link #onStructureInvalid()} to react
 * to those events.
 * </p>
 *
 * <p>MultiTraits must be attached before the machine's first structure formation,
 * i.e. in the machine constructor, just like ordinary traits.</p>
 */
public abstract class MultiTrait extends MachineTrait {

    public MultiTrait(MetaMachine machine) {
        super(machine);
    }

    /**
     * @return the multiblock controller this trait is attached to.
     */
    public IMultiController getController() {
        return (IMultiController) getMachine();
    }

    /**
     * Called by the controller when the multiblock structure is formed.
     * Subclasses should read match-context data and update their state here.
     */
    public void onStructureFormed() {}

    /**
     * Called by the controller when the multiblock structure becomes invalid.
     * Subclasses should reset their state here.
     */
    public void onStructureInvalid() {}
}
