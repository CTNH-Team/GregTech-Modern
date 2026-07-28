package com.gregtechceu.gtceu.utils.energy;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;

import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Forge Energy -> GTCEu IEnergyContainer adapter.
 *
 * Important: amperage consumption is derived from FE actually inserted this tick (matching GTCEu's EUToFEProvider),
 * not from maxReceive. A small internal FE remainder buffer ensures that if a sink cannot accept a full GT packet
 * worth of FE in one tick, no energy is lost and high-tier networks still work.
 */
public final class FeEnergyContainerWrapper implements IEnergyContainer {

    private final IEnergyStorage energyStorage;

    /** Internal remainder buffer, in FE units. */
    private long feBuffer;

    public FeEnergyContainerWrapper(IEnergyStorage storage) {
        this.energyStorage = storage;
    }

    private static int satCast(long v) {
        if (v <= 0) return 0;
        if (v >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) v;
    }

    @Override
    public long acceptEnergyFromNetwork(Direction facing, long voltage, long amperage) {
        if (energyStorage == null || !energyStorage.canReceive()) return 0;
        final int ratio = FeCompat.ratio(false);
        final long maxPacketFe = FeCompat.toFeLong(voltage, ratio);
        if (maxPacketFe <= 0 || amperage <= 0 || voltage <= 0) return 0;

        final long maximalValue = maxPacketFe * amperage;

        int receiveFromBuffer = 0;

        // Try to use internal buffer first
        if (feBuffer > 0) {
            int can = energyStorage.receiveEnergy(satCast(feBuffer), true);
            if (can == 0) return 0;

            if (feBuffer > can) {
                int inserted = energyStorage.receiveEnergy(can, false);
                feBuffer -= inserted;
                return 0;
            } else {
                receiveFromBuffer = satCast(feBuffer);
            }
        }

        if (receiveFromBuffer != 0) {
            int consumable = energyStorage.receiveEnergy(satCast(maximalValue + (long) receiveFromBuffer), true);
            if (consumable == 0) return 0;

            consumable = energyStorage.receiveEnergy(consumable, false);

            if ((long) consumable <= (long) receiveFromBuffer) {
                feBuffer = (long) receiveFromBuffer - (long) consumable;
                return 0;
            }

            long newPower = (long) consumable - (long) receiveFromBuffer;

            if (newPower % maxPacketFe == 0) {
                feBuffer = 0;
                return newPower / maxPacketFe;
            }

            long ampsToConsume = (newPower / maxPacketFe) + 1;
            feBuffer = (maxPacketFe * ampsToConsume) - newPower;
            return ampsToConsume;
        }

        int consumable = energyStorage.receiveEnergy(satCast(maximalValue), true);
        if (consumable == 0) return 0;

        consumable = energyStorage.receiveEnergy(consumable, false);

        if ((long) consumable % maxPacketFe == 0) {
            feBuffer = 0;
            return (long) consumable / maxPacketFe;
        }

        long ampsToConsume = ((long) consumable / maxPacketFe) + 1;
        feBuffer = (maxPacketFe * ampsToConsume) - (long) consumable;
        return ampsToConsume;
    }

    @Override
    public boolean inputsEnergy(Direction facing) {
        return energyStorage != null && energyStorage.canReceive();
    }

    @Override
    public boolean outputsEnergy(Direction facing) {
        return false;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        // Not used by GTCEu EnergyNet delivery for endpoints; keep as no-op.
        return 0;
    }

    @Override
    public long getEnergyCanBeInserted() {
        if (energyStorage == null) return 0;
        int space = Math.max(0, energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());
        return FeCompat.toEu(space, FeCompat.ratio(false));
    }

    @Override
    public long getEnergyStored() {
        if (energyStorage == null) return 0;
        return FeCompat.toEu(energyStorage.getEnergyStored(), FeCompat.ratio(false));
    }

    @Override
    public long getEnergyCapacity() {
        if (energyStorage == null) return 0;
        return FeCompat.toEu(energyStorage.getMaxEnergyStored(), FeCompat.ratio(false));
    }

    @Override
    public long getInputAmperage() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getInputVoltage() {
        // Unlimited so high-tier networks attempt delivery.
        return inputsEnergy(null) ? Long.MAX_VALUE : 0;
    }
}
