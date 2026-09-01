package com.gregtechceu.gtceu.api.machine.trait;

public interface IMultiblockMachineTrait {

    default void onStructureFormed() {}

    default void onStructureInvalid() {}

    default void onPartUnload() {}
}
