package com.gregtechceu.gtceu.utils.energy;

/**
 * Mixin-injected accessor for cached per-segment route data stored on GTCEu's EnergyRoutePath.
 *
 * <p>
 * These arrays are populated once when a route is created by GTCEu's {@code EnergyNetWalker}
 * (hooked via {@code EnergyNetWalkerMixin}) and are then reused by {@code EnergyNetHandler} each tick.
 * This avoids repeated {@code CableBlockEntity.getMaxVoltage()} / node-data lookups in the hot
 * energy transfer loop, while still matching vanilla loss + overvoltage semantics.
 * </p>
 */
public interface IRouteSegmentData {

    /**
     * @param posLong      packed positions (BlockPos#asLong) for each segment, aligned with
     *                     {@code EnergyRoutePath.getPath()}.
     * @param maxVoltage   cached per-segment max voltage (WireProperties#getVoltage).
     * @param lossPerBlock cached per-segment loss per block (WireProperties#getLossPerBlock).
     */
    void gtceuHotfix$setSegmentData(long[] posLong, long[] maxVoltage, int[] lossPerBlock);

    long[] gtceuHotfix$getPosLong();

    long[] gtceuHotfix$getMaxVoltage();

    int[] gtceuHotfix$getLossPerBlock();
}
