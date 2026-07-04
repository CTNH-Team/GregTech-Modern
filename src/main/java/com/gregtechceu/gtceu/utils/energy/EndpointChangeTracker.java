package com.gregtechceu.gtceu.utils.energy;

import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.WeakHashMap;

/**
 * Tracks BlockEntity identity at positions for each EnergyNet to distinguish real endpoint add/remove
 * (BE identity changes) from noisy neighbor updates (BE remains the same).
 *
 * This avoids triggering global route-cache invalidation on every neighbor update, which would
 * reintroduce repeated network walks and TPS regressions.
 */
public final class EndpointChangeTracker {

    private EndpointChangeTracker() {}

    private static final WeakHashMap<EnergyNet, Long2ObjectOpenHashMap<BlockEntity>> LAST_BE = new WeakHashMap<>();

    private static Long2ObjectOpenHashMap<BlockEntity> map(EnergyNet net) {
        synchronized (LAST_BE) {
            return LAST_BE.computeIfAbsent(net, n -> new Long2ObjectOpenHashMap<>());
        }
    }

    /**
     * @return true if the BlockEntity identity at pos changed since last call for this net (including null<->non-null)
     */
    public static boolean didBlockEntityChange(EnergyNet net, BlockPos pos, BlockEntity current) {
        long key = pos.asLong();
        Long2ObjectOpenHashMap<BlockEntity> m = map(net);
        BlockEntity prev = m.get(key);
        if (prev == current) {
            return false;
        }
        // update
        if (current == null) {
            m.remove(key);
        } else {
            m.put(key, current);
        }
        return true;
    }

    public static void clear(EnergyNet net) {
        synchronized (LAST_BE) {
            LAST_BE.remove(net);
        }
    }
}
