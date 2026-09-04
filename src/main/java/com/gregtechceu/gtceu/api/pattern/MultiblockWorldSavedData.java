package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MultiblockWorldSavedData extends SavedData {

    /** A pattern scan is unbounded internally, so never start more than one from a level tick. */
    private static final int MAX_STRUCTURE_CHECKS_PER_TICK = 1;
    private static final int MAX_QUEUE_POLLS_PER_TICK = 64;
    /**
     * Soft wall-clock budget for starting structure scans. The first due scan is always allowed; this guard prevents
     * future increases to {@link #MAX_STRUCTURE_CHECKS_PER_TICK} from accidentally turning one slow tick into a scan
     * storm.
     */
    private static final long STRUCTURE_CHECK_SOFT_BUDGET_NANOS = 2_000_000L;
    private static final int INITIAL_INVALID_RETRY_DELAY_TICKS = 20;
    private static final int MAX_INVALID_RETRY_DELAY_TICKS = 200;
    private static final int INITIAL_UNLOAD_READINESS_DELAY_TICKS = 20;
    private static final int MAX_UNLOAD_READINESS_DELAY_TICKS = 100;
    private static final int CHUNK_LOAD_RECHECK_DELAY_TICKS = 2;
    private static final String NEXT_CONTROLLER_INSTANCE_ID = "nextControllerInstanceId";
    private static final String CONTROLLER_INSTANCES = "controllerInstances";
    private static final String CONTROLLER_POS = "pos";
    private static final String CONTROLLER_INSTANCE_ID = "instanceId";
    private static final String CONTROLLER_ACTIVE = "active";

    public static MultiblockWorldSavedData getOrCreate(ServerLevel serverLevel) {
        MultiblockWorldSavedData data = serverLevel.getDataStorage()
                .computeIfAbsent(MultiblockWorldSavedData::new, MultiblockWorldSavedData::new, "gtceu_multiblock");
        data.serverLevel = serverLevel;
        return data;
    }

    public enum ControllerBindingStatus {
        UNKNOWN,
        ACTIVE,
        RETIRED
    }

    private record ControllerInstance(long instanceId, boolean active) {}

    /**
     * Store all formed multiblocks' structure info
     */
    public final Map<BlockPos, MultiblockState> mapping;
    /**
     * Chunk pos mapping.
     */
    public final Map<ChunkPos, Set<MultiblockState>> chunkPosMapping;
    private final Map<MultiblockState, Set<ChunkPos>> mappedChunks = new IdentityHashMap<>();
    private final Map<BlockPos, ControllerInstance> controllerInstances = new HashMap<>();
    private long nextControllerInstanceId = 1;
    @Nullable
    private ServerLevel serverLevel;

    private MultiblockWorldSavedData() {
        this.mapping = new Object2ObjectOpenHashMap<>();
        this.chunkPosMapping = new HashMap<>();
    }

    private MultiblockWorldSavedData(CompoundTag tag) {
        this();
        nextControllerInstanceId = Math.max(1, tag.getLong(NEXT_CONTROLLER_INSTANCE_ID));
        ListTag instances = tag.getList(CONTROLLER_INSTANCES, Tag.TAG_COMPOUND);
        for (int i = 0; i < instances.size(); i++) {
            CompoundTag instanceTag = instances.getCompound(i);
            long instanceId = instanceTag.getLong(CONTROLLER_INSTANCE_ID);
            if (instanceId <= 0 || !instanceTag.contains(CONTROLLER_POS, Tag.TAG_LONG)) {
                continue;
            }
            BlockPos pos = BlockPos.of(instanceTag.getLong(CONTROLLER_POS));
            controllerInstances.put(pos, new ControllerInstance(instanceId,
                    instanceTag.getBoolean(CONTROLLER_ACTIVE)));
            if (instanceId < Long.MAX_VALUE) {
                nextControllerInstanceId = Math.max(nextControllerInstanceId, instanceId + 1);
            }
        }
    }

    public Set<MultiblockState> getControllersInChunk(ChunkPos chunkPos) {
        return chunkPosMapping.getOrDefault(chunkPos, Collections.emptySet());
    }

    /**
     * Install a mapping only when the controller at the persisted position still owns this exact state.
     *
     * @return whether the mapping was installed
     */
    public boolean tryAddMapping(MultiblockState state) {
        if (!(state.world instanceof ServerLevel serverLevel)) {
            return false;
        }
        MetaMachine currentMachine = getLoadedMachine(serverLevel, state.controllerPos);
        if (!(currentMachine instanceof IMultiController currentController) || currentMachine.isInValid() ||
                currentController.getMultiblockState() != state) {
            removeMapping(state);
            return false;
        }
        // Custom pattern implementations are not required to call MultiblockState#getController themselves.
        state.lastController = currentController;
        MultiblockState previous = this.mapping.put(state.controllerPos, state);
        if (previous != null && previous != state) {
            removeFromChunkMappings(previous);
        }
        // A dynamic pattern may have changed its cache since the last successful check.
        removeFromChunkMappings(state);
        Set<ChunkPos> chunks = new HashSet<>();
        chunks.add(new ChunkPos(state.controllerPos));
        for (long blockPos : state.getCacheForMapping()) {
            chunks.add(new ChunkPos(BlockPos.of(blockPos)));
        }
        mappedChunks.put(state, chunks);
        for (ChunkPos chunk : chunks) {
            chunkPosMapping.computeIfAbsent(chunk, c -> new HashSet<>()).add(state);
        }
        state.confirmPatternCache();
        currentController.onStructureMappingInstalled(chunks.stream()
                .mapToLong(ChunkPos::toLong)
                .sorted()
                .toArray());
        return true;
    }

    public void removeMapping(MultiblockState state) {
        if (this.mapping.get(state.controllerPos) == state) {
            this.mapping.remove(state.controllerPos);
        }
        removeFromChunkMappings(state);
    }

    private void removeFromChunkMappings(MultiblockState state) {
        Set<ChunkPos> chunks = mappedChunks.remove(state);
        if (chunks == null) {
            return;
        }
        for (ChunkPos chunk : chunks) {
            Set<MultiblockState> states = chunkPosMapping.get(chunk);
            if (states == null) {
                continue;
            }
            states.removeIf(candidate -> candidate == state);
            if (states.isEmpty()) {
                chunkPosMapping.remove(chunk);
            }
        }
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag compound) {
        compound.putLong(NEXT_CONTROLLER_INSTANCE_ID, nextControllerInstanceId);
        ListTag instances = new ListTag();
        for (var entry : controllerInstances.entrySet()) {
            CompoundTag instanceTag = new CompoundTag();
            instanceTag.putLong(CONTROLLER_POS, entry.getKey().asLong());
            instanceTag.putLong(CONTROLLER_INSTANCE_ID, entry.getValue().instanceId());
            instanceTag.putBoolean(CONTROLLER_ACTIVE, entry.getValue().active());
            instances.add(instanceTag);
        }
        compound.put(CONTROLLER_INSTANCES, instances);
        return compound;
    }

    /**
     * Ensure that a loaded base controller has a current, persistent structure-ownership epoch. A stale identity can
     * never reactivate a tombstone or replace a newer epoch at the same position.
     *
     * @return the active identity
     */
    public long ensureControllerActive(IMultiController controller) {
        BlockPos pos = controller.self().getPos().immutable();
        ControllerInstance current = controllerInstances.get(pos);
        long requested = controller.getStructureInstanceId();
        boolean adoptActiveRegistryIdentity = requested <= 0 && current != null && current.active();
        boolean allocate = !adoptActiveRegistryIdentity && (requested <= 0 || current != null &&
                (requested < current.instanceId() || requested == current.instanceId() && !current.active()));
        long activeId = adoptActiveRegistryIdentity ? current.instanceId() : requested;
        if (allocate) {
            activeId = allocateControllerInstanceId(current);
        }
        if (allocate || adoptActiveRegistryIdentity) {
            controller.setStructureInstanceId(activeId);
            controller.self().markDirty();
        } else if (requested < Long.MAX_VALUE) {
            nextControllerInstanceId = Math.max(nextControllerInstanceId, requested + 1);
        }

        ControllerInstance active = new ControllerInstance(activeId, true);
        if (!active.equals(current)) {
            controllerInstances.put(pos, active);
            setDirty();
        }
        return activeId;
    }

    private long allocateControllerInstanceId(@Nullable ControllerInstance current) {
        long minimum = current == null || current.instanceId() == Long.MAX_VALUE ? 1 : current.instanceId() + 1;
        long allocated = Math.max(nextControllerInstanceId, minimum);
        if (allocated <= 0 || allocated == Long.MAX_VALUE) {
            throw new IllegalStateException("Exhausted multiblock controller ownership epoch identities");
        }
        nextControllerInstanceId = allocated + 1;
        setDirty();
        return allocated;
    }

    /** Mark a controller ownership epoch as definitively ended. Chunk unloads must never call this method. */
    public void retireController(IMultiController controller) {
        retireControllerPosition(controller.self().getPos(), controller.getStructureInstanceId());
    }

    /**
     * Mark a controller position definitively unowned, retaining the highest identity known by either SavedData or the
     * controller chunk. This is used only when the loaded controller block is known to be unformed or destroyed; it
     * closes both directions of non-atomic chunk/SavedData save ordering without reviving an older epoch.
     */
    public void retireControllerPosition(BlockPos controllerPos, long knownInstanceId) {
        BlockPos immutablePos = controllerPos.immutable();
        ControllerInstance current = controllerInstances.get(immutablePos);
        long retiredInstanceId = Math.max(Math.max(0, knownInstanceId), current == null ? 0 : current.instanceId());
        if (retiredInstanceId <= 0) {
            return;
        }
        ControllerInstance retired = new ControllerInstance(retiredInstanceId, false);
        if (!retired.equals(current)) {
            controllerInstances.put(immutablePos, retired);
            if (retiredInstanceId < Long.MAX_VALUE) {
                nextControllerInstanceId = Math.max(nextControllerInstanceId, retiredInstanceId + 1);
            }
            setDirty();
        }
    }

    /**
     * Mark one exact position/epoch pair retired without overwriting a newer epoch already registered at that position.
     */
    public void retireControllerBinding(BlockPos controllerPos, long instanceId) {
        if (instanceId <= 0) {
            return;
        }
        BlockPos immutablePos = controllerPos.immutable();
        ControllerInstance current = controllerInstances.get(immutablePos);
        if (current != null && current.instanceId() > instanceId) {
            return;
        }
        ControllerInstance retired = new ControllerInstance(instanceId, false);
        if (!retired.equals(current)) {
            controllerInstances.put(immutablePos, retired);
            if (instanceId < Long.MAX_VALUE) {
                nextControllerInstanceId = Math.max(nextControllerInstanceId, instanceId + 1);
            }
            setDirty();
        }
    }

    /** Pure persisted-registry lookup; this method never requests or loads a chunk. */
    public ControllerBindingStatus getControllerBindingStatus(BlockPos controllerPos, long instanceId) {
        if (instanceId <= 0) {
            return ControllerBindingStatus.UNKNOWN;
        }
        ControllerInstance current = controllerInstances.get(controllerPos);
        if (current == null) {
            return ControllerBindingStatus.UNKNOWN;
        }
        if (instanceId < current.instanceId()) {
            return ControllerBindingStatus.RETIRED;
        }
        if (instanceId > current.instanceId()) {
            // A chunk can be saved after this SavedData during a crash. A newer owner id is therefore not positive
            // retirement evidence until that controller is loaded and advances the registry.
            return ControllerBindingStatus.UNKNOWN;
        }
        return current.active() ? ControllerBindingStatus.ACTIVE : ControllerBindingStatus.RETIRED;
    }

    public boolean isControllerBindingRetired(BlockPos controllerPos, long instanceId) {
        return getControllerBindingStatus(controllerPos, instanceId) == ControllerBindingStatus.RETIRED;
    }

    /**
     * Exact loaded-controller check which uses {@code getChunkNow}; it never requests a missing owner chunk.
     */
    public boolean isLoadedControllerInstance(BlockPos controllerPos, long instanceId) {
        return getLoadedControllerInstance(controllerPos, instanceId) != null;
    }

    /**
     * Whether the controller position's chunk is already loaded and entity-ticking. Requiring both states prevents a
     * FULL-but-not-yet-ticking chunk from looking like positive evidence that its controller block entity is absent.
     * This method never requests the chunk.
     */
    public boolean isControllerPositionLoadedNoChunkRequest(BlockPos controllerPos) {
        return serverLevel != null && serverLevel.getChunkSource()
                .getChunkNow(controllerPos.getX() >> 4, controllerPos.getZ() >> 4) != null &&
                serverLevel.shouldTickBlocksAt(controllerPos);
    }

    /**
     * Return the exact loaded controller ownership epoch, or {@code null}. The lookup uses {@code getChunkNow} and
     * never
     * requests a missing owner chunk; callers may apply controller-specific membership rules to the result.
     */
    @Nullable
    public IMultiController getLoadedControllerInstance(BlockPos controllerPos, long instanceId) {
        if (instanceId <= 0 || serverLevel == null) {
            return null;
        }
        MetaMachine machine = getLoadedMachine(serverLevel, controllerPos);
        if (machine instanceof IMultiController controller && !machine.isInValid() &&
                controller.getStructureInstanceId() == instanceId) {
            return controller;
        }
        return null;
    }

    /**
     * Whether a loaded, fully validated controller ownership epoch currently claims a cached structure position. This
     * is
     * an exact positive check; {@code false} may also mean that either chunk is merely unavailable.
     */
    public boolean isLoadedValidatedControllerMember(BlockPos controllerPos, long instanceId, BlockPos memberPos) {
        if (getControllerBindingStatus(controllerPos, instanceId) != ControllerBindingStatus.ACTIVE ||
                !isLoadedControllerInstance(controllerPos, instanceId)) {
            return false;
        }
        MultiblockState state = mapping.get(controllerPos);
        IMultiController controller = state == null ? null : state.lastController;
        return controller != null && controller.isStructureOperational() &&
                controller.getStructureInstanceId() == instanceId &&
                controller.getMultiblockState() == state && state.hasPatternCache() && state.isPosInCache(memberPos);
    }

    // ******************************** structure check queue ******************************** //
    private final Map<IMultiController, PendingStructureCheck> controllers = new IdentityHashMap<>();
    private final Set<PendingStructureCheck> inFlightChecks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final NavigableSet<PendingStructureCheck> controllerQueue = new TreeSet<>(Comparator
            .comparingLong((PendingStructureCheck pending) -> pending.nextCheckTick)
            .thenComparingLong(pending -> pending.sequence));
    private final Map<Long, Set<PendingStructureCheck>> checksWaitingForChunk = new HashMap<>();
    private final Set<PendingStructureCheck> checksWaitingForAnyChunk = Collections
            .newSetFromMap(new IdentityHashMap<>());
    private final Map<Long, Set<PendingStructureCheck>> checksWaitingForBlockChange = new HashMap<>();
    private long periodID;
    private long nextSequence;

    private static final class PendingStructureCheck {

        private final IMultiController controller;
        private long nextCheckTick;
        private long sequence;
        private int invalidRetryCount;
        private int unloadReadinessRetryCount;
        private boolean queued;
        private boolean parkedForChunkLoad;
        private boolean wakeRequested;
        private final Set<Long> awaitedChunks = new HashSet<>();
        private final Set<Long> blockWakeChunks = new HashSet<>();

        private PendingStructureCheck(IMultiController controller, long nextCheckTick, long sequence) {
            this.controller = controller;
            this.nextCheckTick = nextCheckTick;
            this.sequence = sequence;
            this.queued = true;
        }
    }

    /**
     * Queue a controller for a structure check. All accesses are made from the server thread and the identity set makes
     * repeated unload notifications idempotent.
     * 
     * @param controller controller
     */
    public void addAsyncLogic(IMultiController controller) {
        controller.setStructureRevalidationPending(true);
        PendingStructureCheck pending = controllers.get(controller);
        if (pending == null) {
            pending = new PendingStructureCheck(controller, periodID + 1, nextSequence++);
            controllers.put(controller, pending);
            controllerQueue.add(pending);
        } else
            if (!pending.parkedForChunkLoad || controller.getMultiblockState().error != MultiblockState.UNLOAD_ERROR) {
                // A mapped block update or a manual controller notification should not wait for an old exponential
                // retry.
                // Repeated unload callbacks, however, must not wake a controller before its missing chunk returns.
                wakeStructureCheck(pending, 1, true);
            }
    }

    /**
     * Remove a queued controller.
     * 
     * @param controller controller
     */
    public void removeAsyncLogic(IMultiController controller) {
        PendingStructureCheck removed = controllers.remove(controller);
        if (removed != null) {
            unregisterWakeIndexes(removed);
            removed.queued = false;
            removed.parkedForChunkLoad = false;
        }
        if (controllers.isEmpty()) {
            controllerQueue.clear();
            checksWaitingForChunk.clear();
            checksWaitingForAnyChunk.clear();
            checksWaitingForBlockChange.clear();
        } else if (removed != null && controllerQueue.size() > Math.max(64, controllers.size() * 2)) {
            // Identity tokens make cancellation O(1), but cancelled tokens remain in the ordered queue until polled.
            // Rebuild occasionally so a world with at least one permanently unloaded controller cannot accumulate
            // stale tokens forever as other structures repeatedly form and unload. A token currently executing is
            // still present in controllers, but must not be copied back into the queue: tick() alone owns its eventual
            // requeue.
            controllerQueue.clear();
            controllers.values().stream()
                    .filter(pending -> !inFlightChecks.contains(pending))
                    .forEach(pending -> {
                        pending.queued = true;
                        controllerQueue.add(pending);
                    });
        }
    }

    /**
     * Process pending checks from the level tick. Pattern matching reads mutable world and block-entity state, so it
     * must never run on a wall-clock executor. At most one full scan starts per tick. Definitive failures use bounded
     * exponential backoff, while unloaded structures are parked until a relevant chunk-load event rather than scanned
     * every 20 ticks. Both pattern checks and discarded stale queue tokens are bounded per tick.
     */
    public void tick() {
        periodID++;
        long tickStartNanos = System.nanoTime();
        int polls = 0;
        int checks = 0;
        while (checks < MAX_STRUCTURE_CHECKS_PER_TICK && polls < MAX_QUEUE_POLLS_PER_TICK &&
                !controllerQueue.isEmpty() && controllerQueue.first().nextCheckTick <= periodID &&
                (checks == 0 || System.nanoTime() - tickStartNanos < STRUCTURE_CHECK_SOFT_BUDGET_NANOS)) {
            PendingStructureCheck pending = controllerQueue.pollFirst();
            pending.queued = false;
            polls++;
            IMultiController controller = pending.controller;
            if (controllers.get(controller) != pending) {
                continue;
            }
            var machine = controller.self();
            if (machine.isInValid() || !(machine.getLevel() instanceof ServerLevel serverLevel) ||
                    getLoadedMachine(serverLevel, machine.getPos()) != machine) {
                controllers.remove(controller);
                unregisterWakeIndexes(pending);
                removeMapping(controller.getMultiblockState());
                continue;
            }
            if (pending.parkedForChunkLoad) {
                if (!areAwaitedChunksReady(pending)) {
                    scheduleParkedReadiness(pending, nextUnloadReadinessDelay(pending));
                    continue;
                }
                unregisterChunkWake(pending);
                pending.parkedForChunkLoad = false;
            }
            unregisterBlockWake(pending);
            checks++;
            inFlightChecks.add(pending);
            try {
                controller.asyncCheckPattern(periodID);
            } catch (Throwable e) {
                GTCEu.LOGGER.error("Error while assembling multiblock {}", controller, e);
            } finally {
                inFlightChecks.remove(pending);
            }
            // A remove-then-add during the callback installs a new identity token. Never append this stale token too.
            if (controllers.get(controller) == pending) {
                if (pending.wakeRequested) {
                    pending.wakeRequested = false;
                    wakeStructureCheck(pending, 1, true);
                } else if (controller.getMultiblockState().error == MultiblockState.UNLOAD_ERROR) {
                    parkUntilChunkLoad(pending, null);
                } else {
                    scheduleInvalidRetry(pending);
                }
            }
        }
    }

    private void scheduleInvalidRetry(PendingStructureCheck pending) {
        pending.unloadReadinessRetryCount = 0;
        int shift = Math.min(pending.invalidRetryCount, 30);
        long exponentialDelay = (long) INITIAL_INVALID_RETRY_DELAY_TICKS << shift;
        int delay = (int) Math.min(MAX_INVALID_RETRY_DELAY_TICKS, exponentialDelay);
        pending.invalidRetryCount++;
        registerBlockWake(pending);
        enqueue(pending, periodID + delay);
    }

    private void wakeStructureCheck(PendingStructureCheck pending, int delay, boolean resetBackoff) {
        if (controllers.get(pending.controller) != pending) {
            return;
        }
        if (resetBackoff) {
            pending.invalidRetryCount = 0;
            pending.unloadReadinessRetryCount = 0;
        }
        if (inFlightChecks.contains(pending)) {
            pending.wakeRequested = true;
            return;
        }
        unregisterWakeIndexes(pending);
        if (pending.queued) {
            controllerQueue.remove(pending);
            pending.queued = false;
        }
        pending.parkedForChunkLoad = false;
        enqueue(pending, periodID + Math.max(1, delay));
    }

    private void enqueue(PendingStructureCheck pending, long nextCheckTick) {
        if (pending.queued || pending.parkedForChunkLoad || controllers.get(pending.controller) != pending) {
            return;
        }
        pending.nextCheckTick = nextCheckTick;
        pending.sequence = nextSequence++;
        pending.queued = true;
        controllerQueue.add(pending);
    }

    private void parkUntilChunkLoad(PendingStructureCheck pending, @Nullable ChunkPos knownMissingChunk) {
        if (controllers.get(pending.controller) != pending) {
            return;
        }
        unregisterWakeIndexes(pending);
        if (pending.queued) {
            controllerQueue.remove(pending);
            pending.queued = false;
        }
        pending.parkedForChunkLoad = true;
        pending.invalidRetryCount = 0;

        if (knownMissingChunk != null) {
            pending.awaitedChunks.add(knownMissingChunk.toLong());
        }
        MultiblockState state = pending.controller.getMultiblockState();
        long[] confirmedChunks = pending.controller.getConfirmedStructureChunks();
        for (long chunk : confirmedChunks) {
            pending.awaitedChunks.add(chunk);
        }
        if (confirmedChunks.length == 0) {
            for (long blockPos : state.getCacheForMapping()) {
                BlockPos pos = BlockPos.of(blockPos);
                pending.awaitedChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            }
        }
        if (pending.awaitedChunks.isEmpty()) {
            // Old persisted formed states have only controller-owned chunk metadata until their first successful scan.
            // Wake those on the next chunk-load event; the one-scan-per-tick cap still prevents an unrelated load
            // storm.
            checksWaitingForAnyChunk.add(pending);
        } else {
            for (long chunk : pending.awaitedChunks) {
                checksWaitingForChunk.computeIfAbsent(chunk,
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(pending);
            }
        }
        scheduleParkedReadiness(pending, nextUnloadReadinessDelay(pending));
    }

    private void scheduleParkedReadiness(PendingStructureCheck pending, int delay) {
        if (controllers.get(pending.controller) != pending || !pending.parkedForChunkLoad) {
            return;
        }
        if (pending.queued) {
            controllerQueue.remove(pending);
            pending.queued = false;
        }
        pending.nextCheckTick = periodID + Math.max(1, delay);
        pending.sequence = nextSequence++;
        pending.queued = true;
        controllerQueue.add(pending);
    }

    private static int nextUnloadReadinessDelay(PendingStructureCheck pending) {
        int shift = Math.min(pending.unloadReadinessRetryCount, 30);
        long exponentialDelay = (long) INITIAL_UNLOAD_READINESS_DELAY_TICKS << shift;
        pending.unloadReadinessRetryCount++;
        return (int) Math.min(MAX_UNLOAD_READINESS_DELAY_TICKS, exponentialDelay);
    }

    private boolean areAwaitedChunksReady(PendingStructureCheck pending) {
        if (pending.awaitedChunks.isEmpty()) {
            // Before the first confirmed geometry is available, use the bounded readiness timer for UNLOAD causes
            // which have no corresponding Forge chunk event (for example a late entity).
            return true;
        }
        for (long chunk : pending.awaitedChunks) {
            if (!isChunkReadyForStructureCheck(chunk)) {
                return false;
            }
        }
        return true;
    }

    private boolean isChunkReadyForStructureCheck(long chunk) {
        if (serverLevel == null) {
            return false;
        }
        int chunkX = ChunkPos.getX(chunk);
        int chunkZ = ChunkPos.getZ(chunk);
        // getChunkNow is a no-request FULL-chunk probe. A pattern-specific resource (for example a late contraption
        // entity) can still report UNLOAD after this becomes true; that path parks again with the bounded timer below.
        return serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    private void registerBlockWake(PendingStructureCheck pending) {
        MultiblockState state = pending.controller.getMultiblockState();
        pending.blockWakeChunks.add(new ChunkPos(state.controllerPos).toLong());
        for (long blockPos : state.getCacheForMapping()) {
            BlockPos pos = BlockPos.of(blockPos);
            pending.blockWakeChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        for (long chunk : pending.blockWakeChunks) {
            checksWaitingForBlockChange.computeIfAbsent(chunk,
                    ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(pending);
        }
    }

    private void unregisterWakeIndexes(PendingStructureCheck pending) {
        unregisterChunkWake(pending);
        unregisterBlockWake(pending);
    }

    private void unregisterChunkWake(PendingStructureCheck pending) {
        checksWaitingForAnyChunk.remove(pending);
        for (long chunk : pending.awaitedChunks) {
            removeIndexedCheck(checksWaitingForChunk, chunk, pending);
        }
        pending.awaitedChunks.clear();
    }

    private void unregisterBlockWake(PendingStructureCheck pending) {
        for (long chunk : pending.blockWakeChunks) {
            removeIndexedCheck(checksWaitingForBlockChange, chunk, pending);
        }
        pending.blockWakeChunks.clear();
    }

    private static void removeIndexedCheck(Map<Long, Set<PendingStructureCheck>> index, long key,
                                           PendingStructureCheck pending) {
        Set<PendingStructureCheck> checks = index.get(key);
        if (checks == null) {
            return;
        }
        checks.remove(pending);
        if (checks.isEmpty()) {
            index.remove(key);
        }
    }

    /**
     * Pause every mapped structure touching a chunk that is about to unload. The reverse index is copied before any
     * mapping is removed, and this path intentionally does not inspect blocks or block entities in the unloading
     * chunk.
     */
    public void onChunkUnload(ChunkPos chunkPos) {
        for (MultiblockState state : List.copyOf(getControllersInChunk(chunkPos))) {
            IMultiController controller = state.lastController;
            removeMapping(state);
            if (controller == null || controller.getMultiblockState() != state || controller.self().isInValid()) {
                continue;
            }
            state.setError(MultiblockState.UNLOAD_ERROR);
            controller.setStructureRevalidationPending(true);
            if (new ChunkPos(state.controllerPos).equals(chunkPos)) {
                // The controller's own unload lifecycle removes it from the queue; do not create a doomed token.
                continue;
            }
            addAsyncLogic(controller);
            PendingStructureCheck pending = controllers.get(controller);
            if (pending != null) {
                // A scan cannot produce an authoritative result while this known structure chunk is absent. Park it
                // immediately instead of spending one of the global scan slots just to rediscover the unload.
                parkUntilChunkLoad(pending, chunkPos);
            }
        }
    }

    /**
     * Wake parked structures after one of their unavailable chunks loads. Structures without known chunk dependencies
     * are conservatively woken by the next chunk load.
     */
    public void onChunkLoad(ChunkPos chunkPos) {
        long loadedChunk = chunkPos.toLong();
        Set<PendingStructureCheck> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<PendingStructureCheck> exact = checksWaitingForChunk.get(loadedChunk);
        if (exact != null) {
            candidates.addAll(exact);
        }
        candidates.addAll(checksWaitingForAnyChunk);
        for (PendingStructureCheck pending : candidates) {
            if (controllers.get(pending.controller) != pending || !pending.parkedForChunkLoad) {
                unregisterChunkWake(pending);
                continue;
            }
            pending.unloadReadinessRetryCount = 0;
            if (areAwaitedChunksReady(pending)) {
                wakeStructureCheck(pending, CHUNK_LOAD_RECHECK_DELAY_TICKS, true);
            } else {
                // ChunkEvent.Load can precede all of a structure's other chunks. Keep this as a cheap readiness probe;
                // do not run a full pattern scan until every confirmed structure chunk is resident.
                scheduleParkedReadiness(pending, CHUNK_LOAD_RECHECK_DELAY_TICKS);
            }
        }
    }

    /**
     * Expedite invalid-pattern retries whose previous scan touched the changed chunk. This preserves a low idle retry
     * rate without making a player wait for the exponential timer after placing or breaking a structure block.
     */
    public void onBlockChanged(BlockPos pos) {
        Set<PendingStructureCheck> checks = checksWaitingForBlockChange.get(new ChunkPos(pos).toLong());
        if (checks == null || checks.isEmpty()) {
            return;
        }
        for (PendingStructureCheck pending : List.copyOf(checks)) {
            wakeStructureCheck(pending, 1, true);
        }
    }

    public void releaseExecutorService() {
        controllers.clear();
        controllerQueue.clear();
        inFlightChecks.clear();
        checksWaitingForChunk.clear();
        checksWaitingForAnyChunk.clear();
        checksWaitingForBlockChange.clear();
    }

    public long getPeriodID() {
        return periodID;
    }

    boolean isStructureCheckPending(IMultiController controller) {
        return controllers.containsKey(controller);
    }

    int getPendingStructureCheckCount() {
        return controllers.size();
    }

    int getQueuedStructureCheckTokenCount() {
        return controllerQueue.size();
    }

    int getQueuedStructureCheckTokenCount(IMultiController controller) {
        return (int) controllerQueue.stream().filter(pending -> pending.controller == controller).count();
    }

    boolean isStructureCheckParked(IMultiController controller) {
        PendingStructureCheck pending = controllers.get(controller);
        return pending != null && pending.parkedForChunkLoad;
    }

    int getStructureCheckInvalidRetryCount(IMultiController controller) {
        PendingStructureCheck pending = controllers.get(controller);
        return pending == null ? 0 : pending.invalidRetryCount;
    }

    long getStructureCheckDelay(IMultiController controller) {
        PendingStructureCheck pending = controllers.get(controller);
        return pending == null || !pending.queued ? -1 : Math.max(0, pending.nextCheckTick - periodID);
    }

    @Nullable
    private static MetaMachine getLoadedMachine(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : MetaMachine.getMachine(chunk, pos);
    }
}
