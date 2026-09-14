package com.gregtechceu.gtceu.api.machine.trait.feature;

import net.minecraft.core.Direction;

/** A machine trait that constrains the machine's front-facing direction. */
public interface IFrontFacingTrait {

    default boolean isValidFrontFace(Direction direction) {
        return true;
    }
}
