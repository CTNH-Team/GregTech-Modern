package com.gregtechceu.gtceu.utils.energy;

import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, server-thread-only debug counters for energynet performance.
 *
 * Designed to answer: "how many times per tick are we scanning routes / recomputing sinks / rebuilding net data?"
 * Uses an EWMA (exponentially weighted moving average) to avoid storing long histories.
 */
public final class EnergyNetDebugStats {

    private EnergyNetDebugStats() {}

    private static final double ALPHA = 0.10; // 10% new, 90% old

    private static final Reference2ObjectOpenHashMap<EnergyNet, NetStats> STATS = new Reference2ObjectOpenHashMap<>();

    public static final class NetStats {

        public long lastTick = -1L;

        public int curAcceptCalls;
        public int curRouteChecks;
        public int curSinkComputes;
        public int curNetRebuilds;

        public double avgAcceptCalls;
        public double avgRouteChecks;
        public double avgSinkComputes;
        public double avgNetRebuilds;

        public ResourceKey<Level> lastDim;

        private void roll(long tick) {
            if (lastTick == -1L) {
                lastTick = tick;
                return;
            }
            if (tick == lastTick) return;

            // Advance by exactly one tick worth of samples (server tick is monotonic).
            avgAcceptCalls = avgAcceptCalls * (1.0 - ALPHA) + curAcceptCalls * ALPHA;
            avgRouteChecks = avgRouteChecks * (1.0 - ALPHA) + curRouteChecks * ALPHA;
            avgSinkComputes = avgSinkComputes * (1.0 - ALPHA) + curSinkComputes * ALPHA;
            avgNetRebuilds = avgNetRebuilds * (1.0 - ALPHA) + curNetRebuilds * ALPHA;

            curAcceptCalls = 0;
            curRouteChecks = 0;
            curSinkComputes = 0;
            curNetRebuilds = 0;

            lastTick = tick;
        }
    }

    private static NetStats get(EnergyNet net, Level level) {
        final long tick = level.getGameTime();
        NetStats s = STATS.get(net);
        if (s == null) {
            s = new NetStats();
            STATS.put(net, s);
        }
        s.lastDim = level.dimension();
        s.roll(tick);
        return s;
    }

    public static void recordAccept(EnergyNet net, Level level) {
        get(net, level).curAcceptCalls++;
    }

    public static void recordRouteCheck(EnergyNet net, Level level) {
        get(net, level).curRouteChecks++;
    }

    public static void recordSinkCompute(EnergyNet net, Level level) {
        get(net, level).curSinkComputes++;
    }

    public static void recordNetRebuild(EnergyNet net, Level level) {
        get(net, level).curNetRebuilds++;
    }

    /** Snapshot top nets by avgRouteChecks (descending). */
    public static List<Map.Entry<EnergyNet, NetStats>> topByRouteChecks(int limit) {
        final ArrayList<Map.Entry<EnergyNet, NetStats>> list = new ArrayList<>(STATS.entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<EnergyNet, NetStats> e) -> e.getValue().avgRouteChecks)
                .reversed());
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    public static int trackedNetCount() {
        return STATS.size();
    }
}
