package com.gregtechceu.gtceu.api.machine.trait.feature;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;

public interface IMultiblockMachineTrait {

    default IMultiController getMultiMachine() {
        return (IMultiController) ((MachineTrait) this).getMachine();
    }

    default void onStructureFormed() {}

    default void onStructureInvalid() {}

    default void onPartUnload() {}
}
