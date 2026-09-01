package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

/** Runtime binding between a recipe machine and the cleanroom currently containing it. */
public class CleanroomReceiverTrait extends MachineTrait implements ICleanroomReceiver {

    @Getter
    @Setter
    private @Nullable ICleanroomProvider cleanroom;

    public CleanroomReceiverTrait(MetaMachine machine) {
        super(machine);
    }

    public boolean hasActiveCleanroom(CleanroomType type) {
        return cleanroom != null && cleanroom.isClean() && cleanroom.getTypes().contains(type);
    }
}
