package com.gregtechceu.gtceu.utils.energy;

import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyRoutePath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Per-net, per-tick sink state cache.
 *
 * Values include remaining per-tick budgets for sinks (EU-space + input amps).
 * Uses HandlerCache for multi-tick handler resolution.
 */
public final class SinkCache {

    /**
     * Hot path: called from {@code EnergyNetHandler.acceptEnergyFromNetwork()} for every output hatch tick.
     *
     * The energynet delivery happens on the main server thread, so we can avoid the overhead of
     * {@link java.util.WeakHashMap} (ReferenceQueue + synchronized access) and use an identity map.
     *
     * This cache is still conservative: it is per-net and per-tick, and is invalidated alongside
     * the rest of our handler/sink caches when the net is marked dirty.
     */
    private static final Reference2ObjectOpenHashMap<EnergyNet, SinkCache> NET_CACHE = new Reference2ObjectOpenHashMap<>();

    // Tiny single-entry hot cache to avoid hashing on repeated calls within the same tick.
    private static EnergyNet LAST_NET;
    private static long LAST_TICK;
    private static SinkCache LAST_CACHE;

    private final Long2ObjectOpenHashMap<SinkState> sinks = new Long2ObjectOpenHashMap<>();
    private long tick;

    // Per-net, per-tick distribution context: shared route cursor and exhaustion tracking.
    private Object routesRef;
    private int routesSize;
    private int cursor;

    // Cached per-route endpoint data (stable until the routes list changes).
    private BlockPos[] endpointPosCache;
    private byte[] insertSideOrdinalCache; // Direction.ordinal()

    // Fast-path: set true when we have proven there is no demand on this net for the current tick.
    private boolean noDemandThisTick;
    private boolean[] exhaustedRoutes;
    private boolean[] visitedRoutes;
    private int visitedCount;

    // Tick-level saturation: once all routes (i.e., all sinks) are known to be dead/exhausted
    // for this tick, later producer calls can immediately return O(1).
    private boolean saturatedThisTick;

    // ---------------------------------------------------------------------
    // Active-routes + probe budget (hard cap expensive discovery work)
    // ---------------------------------------------------------------------

    /**
     * Max number of "new" routes we are allowed to probe (compute SinkState + try accept)
     * per-net, per-tick, when active routes don't satisfy demand.
     *
     * This intentionally trades a small delay in discovering newly-consuming sinks
     * for a hard upper bound on worst-case CPU.
     */
    private static final int PROBE_BUDGET_PER_TICK = 128;

    private int probeBudgetRemaining;
    private int probeCursor;
    private int exhaustedCount;
    // Active routes (persist across ticks): indices of routes that actually accepted energy last tick.
    private int[] activeA;
    private int activeASize;
    private int[] activeB;
    private int activeBSize;
    private boolean writeIsA;

    private int[] activeRead;
    private int activeReadSize;
    private int[] activeWrite;
    private int activeWriteSize;

    private BitSet activeReadMark;
    private BitSet activeWriteMark;

    private SinkCache(long tick) {
        this.tick = tick;

        // Per-tick state
        this.cursor = 0;
        this.visitedCount = 0;
        this.exhaustedCount = 0;
        this.saturatedThisTick = false;
        noDemandThisTick = false;

        // Active-route hint buffers (double-buffered across ticks).
        // We intentionally keep these across ticks to avoid rescanning hundreds of endpoints
        // when only a small subset are actually consuming.
        this.activeA = new int[64];
        this.activeB = new int[64];
        this.activeASize = 0;
        this.activeBSize = 0;
        this.writeIsA = true;
    }

    /**
     * Reset per-tick state while keeping cross-tick hint buffers.
     * Called from {@link #get(EnergyNet, long)}.
     */
    public void beginTick(long tick) {
        if (this.tick == tick) return;

        this.tick = tick;
        this.sinks.clear();

        // Force per-tick route bookkeeping to be re-prepared.
        this.routesRef = null;
        this.routesSize = 0;
        this.visitedRoutes = null;
        this.exhaustedRoutes = null;
        this.visitedCount = 0;
        this.exhaustedCount = 0;
        this.saturatedThisTick = false;
        noDemandThisTick = false;

        // Reset discovery/probing budget for this net for the tick.
        this.probeBudgetRemaining = PROBE_BUDGET_PER_TICK;
        this.probeCursor = 0;

        // Rotate active buffers: last tick's write buffer becomes this tick's read buffer.
        this.writeIsA = !this.writeIsA;

        // After flipping, select the new write/read buffers.
        // Write buffer is cleared each tick; read buffer retains the previous tick's active routes.
        if (this.writeIsA) {
            // Writing into A this tick, reading from previous tick's B.
            this.activeWrite = this.activeA;
            this.activeASize = 0;
            this.activeWriteSize = 0;

            this.activeRead = this.activeB;
            this.activeReadSize = this.activeBSize;
        } else {
            // Writing into B this tick, reading from previous tick's A.
            this.activeWrite = this.activeB;
            this.activeBSize = 0;
            this.activeWriteSize = 0;

            this.activeRead = this.activeA;
            this.activeReadSize = this.activeASize;
        }
        if (this.activeWriteMark != null) this.activeWriteMark.clear();
        // activeReadMark will be rebuilt lazily in prepareRoutes once we know routesSize.
    }

    public static SinkCache get(EnergyNet net, long tick) {
        // Fast-path: same net and tick as last call.
        if (net == LAST_NET && tick == LAST_TICK && LAST_CACHE != null) {
            return LAST_CACHE;
        }

        SinkCache cache = NET_CACHE.get(net);
        if (cache == null) {
            cache = new SinkCache(tick);
            NET_CACHE.put(net, cache);
        }
        cache.beginTick(tick);

        LAST_NET = net;
        LAST_TICK = tick;
        LAST_CACHE = cache;
        return cache;
    }

    /**
     * Prepare distribution context for the given routes list.
     * Must be called before iterating routes so multiple producers share a cursor.
     */
    public void prepareRoutes(List<?> routes) {
        if (routes == null) {
            this.routesRef = null;
            this.routesSize = 0;
            this.cursor = 0;
            this.exhaustedRoutes = null;
            this.visitedRoutes = null;
            this.visitedCount = 0;
            this.saturatedThisTick = false;
            noDemandThisTick = false;
            this.exhaustedCount = 0;
            return;
        }

        // Only reset when the routes instance or size changes, or at tick start (new SinkCache instance).
        if (this.routesRef != routes || this.routesSize != routes.size()) {
            this.routesRef = routes;
            this.routesSize = routes.size();
            this.cursor = 0;

            final int n = this.routesSize;
            if (n > 0) {
                if (this.exhaustedRoutes == null || this.exhaustedRoutes.length != n) {
                    this.exhaustedRoutes = new boolean[n];
                    this.endpointPosCache = new BlockPos[n];
                    this.insertSideOrdinalCache = new byte[n];
                } else {
                    Arrays.fill(this.exhaustedRoutes, false);
                }

                this.exhaustedCount = 0;
                this.saturatedThisTick = false;
                noDemandThisTick = false;

                if (this.visitedRoutes == null || this.visitedRoutes.length != n) {
                    this.visitedRoutes = new boolean[n];
                } else {
                    Arrays.fill(this.visitedRoutes, false);
                }
                this.visitedCount = 0;

                // Active-route hint bookkeeping.
                // BitSet is used so we can cheaply check if a route was active recently.
                if (this.activeReadMark == null || this.activeReadMark.size() < n) {
                    this.activeReadMark = new BitSet(n);
                } else {
                    this.activeReadMark.clear();
                }
                if (this.activeWriteMark == null || this.activeWriteMark.size() < n) {
                    this.activeWriteMark = new BitSet(n);
                } else {
                    this.activeWriteMark.clear();
                }

                // Ensure ring buffers can hold at least all routes (worst-case).
                if (this.activeA == null || this.activeA.length < n) {
                    this.activeA = new int[Math.max(n, this.activeA == null ? 64 : this.activeA.length * 2)];
                }
                if (this.activeB == null || this.activeB.length < n) {
                    this.activeB = new int[Math.max(n, this.activeB == null ? 64 : this.activeB.length * 2)];
                }

                // Rebuild the read-mark from the previous tick's active buffer.
                this.activeReadMark.clear();
                final int[] read = this.writeIsA ? this.activeB : this.activeA;
                final int readSize = this.writeIsA ? this.activeBSize : this.activeASize;
                for (int i = 0; i < readSize; i++) {
                    int idx = read[i];
                    if (idx >= 0 && idx < n) this.activeReadMark.set(idx);
                }
            } else {
                this.exhaustedRoutes = null;
                this.visitedRoutes = null;
                this.visitedCount = 0;
                this.saturatedThisTick = false;
                noDemandThisTick = false;
                this.exhaustedCount = 0;
            }
        } else {
            // Same route list instance for the same tick.
            // IMPORTANT: do NOT clear exhaustedRoutes / saturation here.
            // Those are exactly the per-tick state we want to preserve across
            // multiple producer calls within the same net+tick.
        }

        // Rotate start index for fairness (cursor will be advanced as producers deliver).
        if (this.routesSize > 0) {
            // A simple deterministic rotation: tick mod N is applied by caller via setCursor().
        }
    }

    /** Get the current shared route cursor (start index). */
    public int getCursor() {
        return cursor;
    }

    /** Advance the shared cursor to the given next index (mod routesSize). */
    public void setCursor(int next) {
        if (routesSize <= 0) {
            this.cursor = 0;
            return;
        }
        int n = routesSize;
        int v = next % n;
        if (v < 0) v += n;
        this.cursor = v;
    }

    public int[] getActiveRoutesRead() {
        return activeRead;
    }

    public int getActiveRoutesReadSize() {
        return activeReadSize;
    }

    public boolean isRouteActiveFromLastTick(int routeIndex) {
        return activeReadMark.get(routeIndex);
    }

    public void markRouteActiveThisTick(int routeIndex) {
        if (routeIndex < 0 || routeIndex >= routesSize) return;
        if (activeWriteMark.get(routeIndex)) return;
        activeWriteMark.set(routeIndex);

        // append to activeWrite buffer
        if (activeWriteSize >= activeWrite.length) {
            activeWrite = Arrays.copyOf(activeWrite, activeWrite.length * 2);
            if (writeIsA) {
                // write buffer is A
                activeA = activeWrite;
            } else {
                activeB = activeWrite;
            }
        }
        activeWrite[activeWriteSize++] = routeIndex;

        if (writeIsA) {
            activeASize = activeWriteSize;
        } else {
            activeBSize = activeWriteSize;
        }
    }

    /** Mark a route index as exhausted for this tick (skip it for other producers). */
    public void exhaustRoute(int idx) {
        if (exhaustedRoutes == null) return;
        if (idx < 0 || idx >= exhaustedRoutes.length) return;
        if (!exhaustedRoutes[idx]) {
            exhaustedRoutes[idx] = true;
            exhaustedCount++;
            if (routesSize > 0 && exhaustedCount >= routesSize) {
                saturatedThisTick = true;
            }
        }
    }

    /** Check whether a route index is exhausted for this tick. */
    public boolean isRouteExhausted(int idx) {
        if (exhaustedRoutes == null) return false;
        return idx >= 0 && idx < exhaustedRoutes.length && exhaustedRoutes[idx];
    }

    /**
     * Mark a route index as visited for this tick.
     * 
     * @return true if this is the first visit this tick; false if it was already visited.
     */
    public boolean visitRoute(int idx) {
        if (visitedRoutes == null) return true; // treat as visitable when no tracking
        if (idx < 0 || idx >= visitedRoutes.length) return true;
        if (visitedRoutes[idx]) return false;
        visitedRoutes[idx] = true;
        visitedCount++;
        return true;
    }

    /** @return true if all routes have been visited at least once this tick. */
    public boolean allRoutesVisited() {
        return routesSize > 0 && visitedCount >= routesSize;
    }

    public boolean isSaturatedThisTick() {
        return saturatedThisTick;
    }

    public void setSaturatedThisTick(boolean saturated) {
        this.saturatedThisTick = saturated;
    }

    public boolean isNoDemandThisTick() {
        return noDemandThisTick;
    }

    public void setNoDemandThisTick(boolean noDemand) {
        this.noDemandThisTick = noDemand;
    }

    public BlockPos getEndpointPosCached(int routeIndex, EnergyRoutePath path) {
        if (endpointPosCache == null || routeIndex < 0 || routeIndex >= endpointPosCache.length) {
            return path.getTargetPipePos().relative(path.getTargetFacing());
        }
        BlockPos cached = endpointPosCache[routeIndex];
        if (cached == null) {
            cached = path.getTargetPipePos().relative(path.getTargetFacing());
            endpointPosCache[routeIndex] = cached;
        }
        return cached;
    }

    public Direction getInsertSideCached(int routeIndex, EnergyRoutePath path) {
        if (insertSideOrdinalCache == null || routeIndex < 0 || routeIndex >= insertSideOrdinalCache.length) {
            return path.getTargetFacing().getOpposite();
        }
        // If we haven't cached this route yet, cache both pos + side together.
        if (endpointPosCache == null || endpointPosCache[routeIndex] == null) {
            endpointPosCache[routeIndex] = path.getTargetPipePos().relative(path.getTargetFacing());
            Direction side = path.getTargetFacing().getOpposite();
            insertSideOrdinalCache[routeIndex] = (byte) side.ordinal();
            return side;
        }
        return Direction.values()[insertSideOrdinalCache[routeIndex] & 0x7F];
    }

    /** Remaining number of "new route" probes allowed this tick for this net. */
    public int getProbeBudgetRemaining() {
        return probeBudgetRemaining;
    }

    /** Consume one probe budget slot (for an expensive route check). */
    public boolean tryConsumeProbeBudget() {
        if (probeBudgetRemaining <= 0) return false;
        probeBudgetRemaining--;
        return true;
    }

    /**
     * Returns the next route index to probe, cycling from 0..routeCount-1.
     * Callers should still check bounds and skip exhausted routes.
     */
    public int nextProbeIndex(int routeCount) {
        if (routeCount <= 0) return 0;
        if (probeCursor >= routeCount) {
            probeCursor = probeCursor % routeCount;
        }
        int idx = probeCursor;
        probeCursor++;
        if (probeCursor >= routeCount) probeCursor = 0;
        return idx;
    }

    /** Sets the probe cursor (will be normalized on nextProbeIndex). */
    public void setProbeCursor(int probeCursor) {
        this.probeCursor = Math.max(0, probeCursor);
    }

    /**
     * Conservative invalidation hook used when the net is marked dirty.
     */
    public static void clear(EnergyNet net) {
        NET_CACHE.remove(net);
        if (net == LAST_NET) {
            LAST_NET = null;
            LAST_TICK = 0L;
            LAST_CACHE = null;
        }
    }

    public SinkState getOrCompute(
                                  EnergyNet net,
                                  EnergyRoutePath path,
                                  Level level,
                                  BlockPos endpointPos,
                                  Direction insertSide,
                                  long voltage // kept for signature compatibility; key is endpoint+side only
    ) {
        long posSide = KeyUtil.packPosSide(endpointPos, insertSide);
        SinkState s = sinks.get(posSide);
        if (s != null) return s;

        EnergyNetDebugStats.recordSinkCompute(net, level);
        s = SinkState.compute(net, path, level, endpointPos, insertSide);
        if (s.cacheable) sinks.put(posSide, s);
        return s;
    }
}
