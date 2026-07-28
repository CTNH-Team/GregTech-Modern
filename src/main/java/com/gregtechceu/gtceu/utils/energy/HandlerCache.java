package com.gregtechceu.gtceu.utils.energy;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyRoutePath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/**
 * Multi-tick cache of endpoint -> resolved {@link IEnergyContainer} handler plus static sink properties.
 *
 * Goal: avoid repeated capability resolution (Level#getBlockEntity, getCapability, LazyOptional.resolve)
 * and avoid repeatedly calling virtual getters on sink handlers in the hot path.
 *
 * Keyed by endpoint position + insert side.
 *
 * Correctness:
 * - Validity is guarded by BlockEntity identity. If the BE instance at the endpoint changes, we refresh.
 * - The cache is cleared (or locally invalidated) on net-dirty signals (neighbor updates / BE change tracker).
 */
public final class HandlerCache {

    private static final Reference2ObjectOpenHashMap<EnergyNet, PerNet> PER_NET = new Reference2ObjectOpenHashMap<>();

    private static EnergyNet LAST_NET;
    private static PerNet LAST_PER_NET;

    /**
     * Cached entry for a single endpoint (pos+side).
     *
     * 'inputsEnergy', 'inputAmps', 'inputVoltage' are treated as static per BE instance for performance.
     * If an implementation can change those dynamically without BE replacement, it will still work,
     * but may be slightly stale until the next invalidation; this matches the original hotfix intent.
     */
    public static final class Entry {

        public final BlockEntity be;
        public final IEnergyContainer handler; // may be null
        public final boolean inputsEnergy;
        public final long inputAmps;
        public final long inputVoltage;

        private Entry(BlockEntity be, IEnergyContainer handler, Direction insertSide) {
            this.be = be;
            this.handler = handler;

            boolean inputs = false;
            long amps = 0;
            long inV = 0;

            if (handler != null) {
                try {
                    inputs = handler.inputsEnergy(insertSide);
                    if (inputs) {
                        amps = handler.getInputAmperage();
                        inV = handler.getInputVoltage();
                    }
                } catch (Throwable t) {
                    // Treat as non-input if a handler misbehaves.
                    inputs = false;
                    amps = 0;
                    inV = 0;
                }
            }

            this.inputsEnergy = inputs;
            this.inputAmps = amps;
            this.inputVoltage = inV;
        }
    }

    private static final class PerNet {

        final Long2ObjectOpenHashMap<Entry> map = new Long2ObjectOpenHashMap<>();
    }

    private static PerNet perNet(EnergyNet net) {
        if (net == LAST_NET && LAST_PER_NET != null) {
            return LAST_PER_NET;
        }
        PerNet pn = PER_NET.get(net);
        if (pn == null) {
            pn = new PerNet();
            PER_NET.put(net, pn);
        }
        LAST_NET = net;
        LAST_PER_NET = pn;
        return pn;
    }

    /**
     * Get a cached handler entry for an endpoint. Refreshes if the endpoint BlockEntity instance changed.
     */
    public static Entry get(EnergyNet net, Level level, BlockPos endpointPos, Direction insertSide,
                            EnergyRoutePath path) {
        long key = KeyUtil.packPosSide(endpointPos, insertSide);
        PerNet pn = perNet(net);

        Entry cached = pn.map.get(key);

        BlockEntity currentBe = level.getBlockEntity(endpointPos);
        if (cached != null && cached.be == currentBe) {
            return cached;
        }

        // Refresh: resolve via route path (internally does the capability lookup).
        IEnergyContainer handler = path.getHandler(level);

        Entry fresh = new Entry(currentBe, handler, insertSide);
        pn.map.put(key, fresh);
        return fresh;
    }

    /**
     * Local invalidation around a position (pos itself + 6 neighbors; all sides).
     *
     * This is used when a block update occurs that could change endpoint availability without necessarily
     * changing cable connections.
     */
    public static void invalidateAround(EnergyNet net, BlockPos pos) {
        if (net == null || pos == null) return;
        PerNet pn = perNet(net);

        long base = KeyUtil.packPos(pos);
        for (int s = 0; s < 6; s++) {
            pn.map.remove(base | (long) s);
        }

        // Neighbors
        for (Direction dir : Direction.values()) {
            BlockPos p = pos.relative(dir);
            long b = KeyUtil.packPos(p);
            for (int s = 0; s < 6; s++) {
                pn.map.remove(b | (long) s);
            }
        }
    }

    /** Full clear for a given net (optional utility). */
    public static void clear(EnergyNet net) {
        if (net == null) return;
        PER_NET.remove(net);
        if (net == LAST_NET) {
            LAST_NET = null;
            LAST_PER_NET = null;
        }
    }
}
