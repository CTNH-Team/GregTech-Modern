package com.gregtechceu.gtceu.api.capability.compat;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Field;

public class EUToFEProvider extends CapabilityCompatProvider {

    /**
     * Internally used FE Buffer so that a very large packet of EU is not partially destroyed
     * on the conversion to FE. This is hidden from the player, but ensures that no energy
     * is ever lost on conversion, no matter the voltage tier or FE storage abilities.
     */
    private long feBuffer;

    public EUToFEProvider(BlockEntity tileEntity) {
        super(tileEntity);
    }

    @Unique
    private static final Field GTCEU_HOTFIX$ENERGY_STORAGE_FIELD = gtceuHotfix$findEnergyStorageField();

    @Unique
    private static Field gtceuHotfix$findEnergyStorageField() {
        try {
            Field f = com.gregtechceu.gtceu.api.capability.compat.EUToFEProvider.GTEnergyWrapper.class
                    .getDeclaredField("energyStorage");
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    @Unique
    private IEnergyStorage gtceuHotfix$getStorage() {
        if (GTCEU_HOTFIX$ENERGY_STORAGE_FIELD == null) return null;
        try {
            return (IEnergyStorage) GTCEU_HOTFIX$ENERGY_STORAGE_FIELD.get(this);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Internal FE remainder buffer (in FE units).
     * This is the same idea as GTCEu's original EUToFEProvider: if a sink can only accept part of a GT packet
     * worth of FE, we buffer the remainder so no energy is lost, and amperage consumption remains consistent.
     */
    @Unique
    private long gtceuHotfix$feBuffer;

    @Unique
    private static int gtceuHotfix$satCast(long v) {
        if (v <= 0) return 0;
        if (v >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) v;
    }

    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, Direction facing) {
        if (!ConfigHolder.INSTANCE.compat.energy.nativeEUToFE ||
                capability != GTCapability.CAPABILITY_ENERGY_CONTAINER)
            return LazyOptional.empty();

        LazyOptional<IEnergyStorage> energyStorage = getUpvalueCapability(ForgeCapabilities.ENERGY, facing);
        return energyStorage.isPresent() ?
                GTCapability.CAPABILITY_ENERGY_CONTAINER.orEmpty(capability,
                        LazyOptional.of(() -> new GTEnergyWrapper(energyStorage.resolve().get()))) :
                LazyOptional.empty();
    }

    public class GTEnergyWrapper implements IEnergyContainer {

        private final IEnergyStorage energyStorage;

        public GTEnergyWrapper(IEnergyStorage energyStorage) {
            this.energyStorage = energyStorage;
        }

        @Override
        public long acceptEnergyFromNetwork(Direction facing, long voltage, long amperage) {
            IEnergyStorage s = gtceuHotfix$getStorage();
            if (s == null || !s.canReceive()) return 0;

            final int ratio = FeCompat.ratio(false);
            final long maxPacketFe = FeCompat.toFeLong(voltage, ratio);
            if (maxPacketFe <= 0 || amperage <= 0 || voltage <= 0) return 0;

            final long maximalValue = maxPacketFe * amperage;

            int receiveFromBuffer = 0;

            // Try to use the internal buffer before consuming a new packet
            if (gtceuHotfix$feBuffer > 0) {
                int can = s.receiveEnergy(gtceuHotfix$satCast(gtceuHotfix$feBuffer), true);
                if (can == 0) return 0;

                // Buffer could provide only part of what the sink can accept this tick; consume part of buffer only
                if (gtceuHotfix$feBuffer > can) {
                    int inserted = s.receiveEnergy(can, false);
                    gtceuHotfix$feBuffer -= inserted;
                    return 0;
                } else {
                    // Buffer can be fully consumed; include it in the combined insertion below
                    receiveFromBuffer = gtceuHotfix$satCast(gtceuHotfix$feBuffer);
                }
            }

            // Consume buffer remainder + new packet energy in a single insertion
            if (receiveFromBuffer != 0) {
                int consumable = s.receiveEnergy(gtceuHotfix$satCast(maximalValue + (long) receiveFromBuffer), true);
                if (consumable == 0) return 0;

                consumable = s.receiveEnergy(consumable, false);

                // Only able to consume less than our buffered amount
                if ((long) consumable <= (long) receiveFromBuffer) {
                    gtceuHotfix$feBuffer = (long) receiveFromBuffer - (long) consumable;
                    return 0;
                }

                long newPower = (long) consumable - (long) receiveFromBuffer;

                // Able to consume buffered amount plus an even amount of packets
                if (newPower % maxPacketFe == 0) {
                    gtceuHotfix$feBuffer = 0;
                    return newPower / maxPacketFe;
                }

                // Able to consume buffered amount plus some remainder inside the last packet
                long ampsToConsume = (newPower / maxPacketFe) + 1;
                gtceuHotfix$feBuffer = (maxPacketFe * ampsToConsume) - newPower;
                return ampsToConsume;
            }

            // No buffer: try to draw up to amperage packets worth of FE
            int consumable = s.receiveEnergy(gtceuHotfix$satCast(maximalValue), true);
            if (consumable == 0) return 0;

            consumable = s.receiveEnergy(consumable, false);

            if ((long) consumable % maxPacketFe == 0) {
                gtceuHotfix$feBuffer = 0;
                return (long) consumable / maxPacketFe;
            }

            long ampsToConsume = ((long) consumable / maxPacketFe) + 1;
            gtceuHotfix$feBuffer = (maxPacketFe * ampsToConsume) - (long) consumable;
            return ampsToConsume;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            // Keep original behaviour: this wrapper is a sink adapter; direct delta changes are unsupported here.
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            IEnergyStorage s = gtceuHotfix$getStorage();
            if (s == null) return 0;
            return FeCompat.toEu(s.getMaxEnergyStored(), FeCompat.ratio(false));
        }

        @Override
        public long getEnergyStored() {
            IEnergyStorage s = gtceuHotfix$getStorage();
            if (s == null) return 0;
            return FeCompat.toEu(s.getEnergyStored(), FeCompat.ratio(false));
        }

        /**
         * Most RF/FE cables blindly try to insert energy without checking if there is space, since the receiving
         * IEnergyStorage should handle it.
         * This simulates that behavior in most places by allowing our "is there space" checks to pass and letting the
         * cable attempt to insert energy.
         * If the wrapped TE actually cannot accept any more energy, the energy transfer will return 0 before any
         * changes to our internal rf buffer.
         */
        @Override
        public long getEnergyCanBeInserted() {
            IEnergyStorage s = gtceuHotfix$getStorage();
            if (s == null) return 0;
            if (!s.canReceive()) return 0;

            // "Very large" so sink validation (euSpace >= voltage) won't filter out the endpoint.
            // Actual throughput + amperage drain is still governed by acceptEnergyFromNetwork(),
            // which is based on the real amount inserted into FE this tick.
            return Long.MAX_VALUE / 4;
        }

        @Override
        public long getInputAmperage() {
            IEnergyStorage s = gtceuHotfix$getStorage();
            if (s == null || !s.canReceive()) return 0;
            return Long.MAX_VALUE;
        }

        @Override
        public long getInputVoltage() {
            IEnergyStorage s = gtceuHotfix$getStorage();
            if (s == null || !s.canReceive()) return 0;
            return Long.MAX_VALUE;
        }

        @Override
        public boolean inputsEnergy(Direction facing) {
            IEnergyStorage s = gtceuHotfix$getStorage();
            return s != null && s.canReceive();
        }

        /**
         * Wrapped FE-consumers should not be able to output EU.
         */
        @Override
        public boolean outputsEnergy(Direction facing) {
            return false;
        }

        /**
         * Hide this BlockEntity EU-capability in TOP. Allows FE-machines to
         * "silently" accept EU without showing their charge in EU in TOP.
         * Let the machine display it in FE instead, however it chooses to.
         */
        @Override
        public boolean isOneProbeHidden() {
            return true;
        }
    }
}
