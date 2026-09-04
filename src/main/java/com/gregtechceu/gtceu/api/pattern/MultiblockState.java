package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

public class MultiblockState {

    public final static PatternError UNLOAD_ERROR = new PatternStringError("multiblocked.pattern.error.chunk");
    public final static PatternError UNINIT_ERROR = new PatternStringError("multiblocked.pattern.error.init");

    private BlockPos pos;
    private BlockState blockState;
    private BlockEntity tileEntity;
    private boolean tileEntityInitialized;
    @Getter
    private final PatternMatchContext matchContext;
    @Getter
    private Object2IntOpenHashMap<SimplePredicate> globalCount;
    @Getter
    private Object2IntOpenHashMap<SimplePredicate> layerCount;
    public TraceabilityPredicate predicate;
    public IO io;
    public PatternError error;
    @Getter
    @Setter
    private boolean neededFlip = false;
    @Getter
    public final Level world;
    public final BlockPos controllerPos;
    public IMultiController lastController;

    private Long2ObjectOpenHashMap<TraceabilityPredicate> predicateMap;
    private LongSet cachedChunks;
    private boolean workingCacheExposed;
    @Nullable
    private Long2ObjectOpenHashMap<TraceabilityPredicate> confirmedPredicateMap;
    @Nullable
    private LongSet confirmedCachedChunks;

    public MultiblockState(Level world, BlockPos controllerPos) {
        this.world = world;
        this.controllerPos = controllerPos;
        this.error = UNINIT_ERROR;
        this.matchContext = new PatternMatchContext();
    }

    public void clean() {
        this.matchContext.reset();
        this.globalCount = new Object2IntOpenHashMap<>();
        this.layerCount = new Object2IntOpenHashMap<>();

        predicateMap = new Long2ObjectOpenHashMap<>();
        cachedChunks = new LongOpenHashSet();
        workingCacheExposed = false;
    }

    public Map<BlockPos, TraceabilityPredicate> getPosPredicateMap() {
        return predicateMap.long2ObjectEntrySet()
                .stream()
                .collect(Collectors.toMap(
                        l -> BlockPos.of(l.getLongKey()),
                        Long2ObjectMap.Entry::getValue));
    }

    /**
     * Return the mutable working predicate map retained by the historical API. If the working cache is currently the
     * last confirmed cache, detach it first so an addon mutating the returned map cannot corrupt the confirmed shape.
     */
    public Long2ObjectOpenHashMap<TraceabilityPredicate> getPredicateMap() {
        detachWorkingPatternCache();
        workingCacheExposed = true;
        return predicateMap;
    }

    public boolean update(BlockPos posIn, TraceabilityPredicate predicate) {
        this.pos = posIn;
        this.blockState = null;
        this.tileEntity = null;
        this.tileEntityInitialized = false;
        this.predicate = predicate;
        this.error = null;
        if (world.isOutsideBuildHeight(posIn)) {
            error = UNINIT_ERROR;
            return false;
        }
        if (!world.isLoaded(posIn)) {
            error = UNLOAD_ERROR;
            return false;
        }
        return true;
    }

    public IMultiController getController() {
        if (world.isLoaded(controllerPos)) {
            if (world.getBlockEntity(controllerPos) instanceof IMachineBlockEntity machineBlockEntity &&
                    machineBlockEntity.getMetaMachine() instanceof IMultiController controller) {
                return lastController = controller;
            }
        } else {
            error = UNLOAD_ERROR;
        }
        return null;
    }

    public boolean hasError() {
        return error != null;
    }

    public void setError(PatternError error) {
        this.error = error;
        if (error != null && error != UNLOAD_ERROR && error != UNINIT_ERROR) {
            // These two errors are static identity sentinels. Binding either one to this state would keep its Level
            // reachable from a static root after a server world is closed.
            error.setWorldState(this);
        }
    }

    public BlockState getBlockState() {
        if (this.blockState == null) {
            this.blockState = this.world.getBlockState(this.pos);
        }
        if (this.blockState == null) {
            GTCEu.LOGGER.error("could not get BlockState at " + this.pos + " in MultiblockState");
        }
        return this.blockState;
    }

    @Nullable
    public BlockEntity getTileEntity() {
        if (!getBlockState().hasBlockEntity()) {
            return null;
        }
        if (this.tileEntity == null && !this.tileEntityInitialized) {
            this.tileEntity = this.world.getBlockEntity(this.pos);
            this.tileEntityInitialized = true;
        }

        return this.tileEntity;
    }

    public BlockPos getPos() {
        return this.pos.immutable();
    }

    public BlockState getOffsetState(Direction face) {
        if (pos instanceof BlockPos.MutableBlockPos) {
            ((BlockPos.MutableBlockPos) pos).move(face);
            BlockState blockState = world.getBlockState(pos);
            ((BlockPos.MutableBlockPos) pos).move(face.getOpposite());
            return blockState;
        }
        return world.getBlockState(this.pos.relative(face));
    }

    public LongSet getCache() {
        // keySet() is mutable (remove/clear write through to predicateMap), so preserve that API while protecting the
        // confirmed alias. Internal read-only mapping code uses getCacheForMapping() and avoids this copy-on-access.
        detachWorkingPatternCache();
        workingCacheExposed = true;
        return predicateMap.keySet();
    }

    /** Read-only-by-convention internal view used while installing a mapping. */
    LongSet getCacheForMapping() {
        return predicateMap == null ? LongSets.EMPTY_SET : predicateMap.keySet();
    }

    /** Whether this state has a predicate cache from a successfully mapped pattern scan. */
    public boolean hasPatternCache() {
        return confirmedPredicateMap != null;
    }

    /**
     * A formed controller must not switch to another legal orientation or pattern while any position belonging to its
     * last confirmed structure is unavailable. Positions outside the build height are definitive mismatches and are
     * deliberately left for the normal pattern scan to diagnose.
     */
    public boolean hasUnloadedCachedPosition() {
        if (confirmedCachedChunks == null) {
            return false;
        }
        for (long cachedChunk : confirmedCachedChunks) {
            if (!world.hasChunk(ChunkPos.getX(cachedChunk), ChunkPos.getZ(cachedChunk))) {
                return true;
            }
        }
        return false;
    }

    public void addPosCache(BlockPos pos, TraceabilityPredicate predicate) {
        detachWorkingPatternCache();
        predicateMap.put(pos.asLong(), predicate);
        // update() rejects out-of-height positions before they can enter a normal pattern cache. Keep the guard for
        // compatibility callers which populate caches directly so those positions remain definitive mismatches.
        if (!world.isOutsideBuildHeight(pos)) {
            cachedChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
    }

    public boolean isPosInCache(BlockPos pos) {
        Long2ObjectOpenHashMap<TraceabilityPredicate> cache = confirmedPredicateMap != null ?
                confirmedPredicateMap : predicateMap;
        return cache != null && cache.containsKey(pos.asLong());
    }

    /**
     * Commit the cache built by the current scan as the last fully validated structure. Failed scans must never
     * replace this snapshot, because it is the authoritative set used to protect unloaded old structure chunks.
     */
    public void confirmPatternCache() {
        if (predicateMap == null) {
            predicateMap = new Long2ObjectOpenHashMap<>();
        }
        if (cachedChunks == null) {
            cachedChunks = new LongOpenHashSet();
        }
        if (workingCacheExposed) {
            // A caller can retain a mutable handle obtained before this commit. Detaching only when the getter is
            // called after commit cannot protect against that handle. Copy once at this boundary, and rebuild chunk
            // dependencies because direct map/key-set edits bypass addPosCache(). The normal scanner never exposes a
            // mutable handle and therefore still commits without copying.
            predicateMap = new Long2ObjectOpenHashMap<>(predicateMap);
            cachedChunks = new LongOpenHashSet();
            for (long packedPos : predicateMap.keySet()) {
                BlockPos cachedPos = BlockPos.of(packedPos);
                if (!world.isOutsideBuildHeight(cachedPos)) {
                    cachedChunks.add(ChunkPos.asLong(cachedPos.getX() >> 4, cachedPos.getZ() >> 4));
                }
            }
            workingCacheExposed = false;
        }
        // A successful scan is immutable until the next clean() or a compatibility caller asks for a mutable view.
        // Keep one resident copy in the common stable state; every write entry point detaches before mutation.
        confirmedPredicateMap = predicateMap;
        confirmedCachedChunks = cachedChunks;
    }

    /** Restore the working cache after an incomplete scan so legacy callers also keep seeing the confirmed shape. */
    public void restoreConfirmedPatternCache() {
        if (confirmedPredicateMap == null || confirmedCachedChunks == null) {
            return;
        }
        predicateMap = confirmedPredicateMap;
        cachedChunks = confirmedCachedChunks;
        workingCacheExposed = false;
    }

    private void detachWorkingPatternCache() {
        if (predicateMap != null && predicateMap == confirmedPredicateMap) {
            predicateMap = new Long2ObjectOpenHashMap<>(predicateMap);
        }
        if (cachedChunks != null && cachedChunks == confirmedCachedChunks) {
            cachedChunks = new LongOpenHashSet(cachedChunks);
        }
    }

    public void onBlockStateChanged(BlockPos pos, BlockState state) {
        if (world instanceof ServerLevel serverLevel) {
            var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
            if (pos.equals(controllerPos)) {
                if (lastController != null && lastController.getMultiblockState() == this) {
                    if (!state.is(lastController.self().getBlockState().getBlock())) {
                        mwsd.removeMapping(this);
                        lastController.onStructureInvalid();
                    }
                } else {
                    mwsd.removeMapping(this);
                }
            } else {
                IMultiController controller = getController();
                if (controller == null || controller.getMultiblockState() != this) {
                    mwsd.removeMapping(this);
                    if (controller == null && error == UNLOAD_ERROR && !serverLevel.isLoaded(controllerPos)) {
                        GTCEu.LOGGER.info("Controller not loaded, pos {}", controllerPos);
                    }
                    return;
                }
                if (controller.shouldIgnoreChange(pos, state)) {
                    return;
                }
                // A block update may arrive in a burst (for example while a chunk restores block entities). Do not
                // mutate this state's match context or run a full pattern scan in the notification callback. Removing
                // the mapping coalesces subsequent updates, and the bounded server-tick queue checks the final world
                // state once. The queue marks the controller as pending immediately, pausing work without turning a
                // loaded block update into a synthetic chunk-unload pattern error or unlinking any parts.
                mwsd.removeMapping(this);
                mwsd.addAsyncLogic(controller);
            }
        }
    }
}
