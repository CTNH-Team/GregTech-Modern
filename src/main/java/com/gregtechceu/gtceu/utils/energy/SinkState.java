package com.gregtechceu.gtceu.utils.energy;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyRoutePath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Per-tick sink state for a specific endpoint (pos + insert side) on a specific {@link EnergyNet}.
 *
 * This is intentionally keyed WITHOUT voltage. We track:
 * - remaining input amperage budget for this tick (handler.getInputAmperage())
 * - remaining EU storage space (capacity - stored) in EU units
 *
 * For a given transfer voltage V, the storage-limited amps are (remainingEuSpace / V).
 * After a successful transfer of A amps at voltage V, remainingEuSpace -= A*V.
 *
 * This avoids creating separate cache entries per voltage and dramatically reduces sink recomputation.
 */
public final class SinkState {

    public final IEnergyContainer handler;

    /** Whether this endpoint is a valid energy sink (direction + basic capability checks). */
    public final boolean valid;

    /** Whether this result should be cached for the remainder of the tick. */
    public final boolean cacheable;

    /** Remaining input amps budget for this tick (decremented after accepts). */
    public long remainingInputAmps;

    /** Remaining storage space in EU units for this tick (decremented by acceptedAmps * voltage). */
    public long remainingEuSpace;

    /** Endpoint's nominal input voltage (used for over-voltage attempt logic). */
    public final long inputVoltage;

    /** True if this handler is an FE wrapper (no GTCEu explosion semantics). */
    public final boolean isFeWrapper;

    private SinkState(
                      IEnergyContainer handler,
                      boolean valid,
                      boolean cacheable,
                      long remainingInputAmps,
                      long remainingEuSpace,
                      long inputVoltage,
                      boolean isFeWrapper) {
        this.handler = handler;
        this.valid = valid;
        this.cacheable = cacheable;
        this.remainingInputAmps = remainingInputAmps;
        this.remainingEuSpace = remainingEuSpace;
        this.inputVoltage = inputVoltage;
        this.isFeWrapper = isFeWrapper;
    }

    public static SinkState compute(
                                    EnergyNet net,
                                    EnergyRoutePath path,
                                    Level level,
                                    BlockPos endpointPos,
                                    Direction insertSide) {
        HandlerCache.Entry entry = HandlerCache.get(net, level, endpointPos, insertSide, path);
        IEnergyContainer handler = entry.handler;
        if (handler == null) {
            return new SinkState(null, false, true, 0, 0, 0, false);
        }

        if (!entry.inputsEnergy) {
            return new SinkState(handler, false, true, 0, 0, 0, isFeWrapper(handler));
        }

        long maxAmps = entry.inputAmps;
        if (maxAmps <= 0) {
            return new SinkState(handler, false, true, 0, 0, 0, isFeWrapper(handler));
        }

        // Cache input voltage once; used to decide whether to force an over-voltage attempt.
        long inV = entry.inputVoltage;

        // FE wrappers (GTCEu EUToFEProvider$GTEnergyWrapper) do not provide meaningful capacity/stored values.
        // Use getEnergyCanBeInserted() instead (patched by our EUToFEGTEnergyWrapperMixin) to estimate remaining space.
        if (isFeWrapper(handler)) {
            long canInsertEu = handler.getEnergyCanBeInserted();
            if (canInsertEu <= 0) {
                // Don't cache a "full" state; FE sinks may open space later in the tick.
                return new SinkState(handler, false, false, 0, 0, inV, true);
            }
            return new SinkState(handler, true, true, maxAmps, canInsertEu, inV, true);
        }

        // Avoid getEnergyCanBeInserted() (it often calls getEnergyStored/getEnergyCapacity anyway).
        // Compute EU space directly and store it so voltage-specific amps can be derived cheaply.
        long stored = handler.getEnergyStored();
        long cap = handler.getEnergyCapacity();
        long euSpace = cap - stored;

        if (euSpace <= 0) {
            // Storage is full *right now*; space can open later in the same tick after the machine consumes energy.
            // Do not cache a "full" negative state, or we can under-supply and cause machines to pause.
            return new SinkState(handler, false, false, 0, 0, inV, isFeWrapper(handler));
        }

        return new SinkState(handler, true, true, maxAmps, euSpace, inV, isFeWrapper(handler));
    }

    private static boolean isFeWrapper(IEnergyContainer handler) {
        // Avoid hard dependency on compat classes; string check is stable and cheap.
        return handler != null && (handler.getClass().getName().contains("EUToFEProvider$GTEnergyWrapper") ||
                handler.getClass().getName().contains("FeEnergyContainerWrapper"));
    }

    /**
     * Compute how many amps we can attempt to send at the given voltage, based on remaining input amps and EU space.
     * Includes the "force 1 attempt under over-voltage" rule to preserve GTCEu explosion semantics.
     */
    public long computeSendableAmps(long voltage) {
        if (!valid || remainingInputAmps <= 0 || voltage <= 0) return 0;

        long byStorage = remainingEuSpace / voltage;
        if (byStorage <= 0) {
            // Under over-voltage, EU endpoints must still receive at least one attempt to trigger GTCEu failure logic.
            boolean overVoltage = inputVoltage > 0 && voltage > inputVoltage;
            if (overVoltage && !isFeWrapper) {
                return 1;
            }
            return 0;
        }

        long max = remainingInputAmps;
        if (byStorage < max) max = byStorage;
        return max;
    }

    public void onAccepted(long acceptedAmps, long voltage) {
        if (acceptedAmps <= 0) return;

        remainingInputAmps -= acceptedAmps;
        if (remainingInputAmps < 0) remainingInputAmps = 0;

        if (voltage > 0 && remainingEuSpace > 0) {
            // Saturating subtract to avoid overflow edge cases.
            long delta;
            try {
                delta = Math.multiplyExact(acceptedAmps, voltage);
            } catch (ArithmeticException ex) {
                delta = Long.MAX_VALUE;
            }
            remainingEuSpace -= delta;
            if (remainingEuSpace < 0) remainingEuSpace = 0;
        }
    }
}
