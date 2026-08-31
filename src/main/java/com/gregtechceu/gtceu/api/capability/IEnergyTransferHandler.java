package com.gregtechceu.gtceu.api.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Set;

/** Optional bridge-aware network injection hook for addon energy endpoints. */
public interface IEnergyTransferHandler {

    long acceptEnergyFromNetwork(Direction side, long voltage, long amperage,
                                 Set<BlockPos> excludedDestinations);
}
