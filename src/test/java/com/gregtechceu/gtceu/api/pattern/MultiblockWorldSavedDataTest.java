package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MultiblockWorldSavedDataTest {

    private static final BlockPos LCR_CONTROLLER_POS = new BlockPos(1, 2, 0);

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void pendingCheckMembershipSurvivesRepeatedRequeue(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        savedData.removeAsyncLogic(controller);

        for (int i = 0; i < 128; i++) {
            savedData.addAsyncLogic(controller);
            savedData.removeAsyncLogic(controller);
        }
        savedData.addAsyncLogic(controller);

        helper.assertTrue(savedData.isStructureCheckPending(controller),
                "The controller lost its pending check after repeated remove-and-add cycles");
        savedData.removeAsyncLogic(controller);
        helper.assertFalse(savedData.isStructureCheckPending(controller),
                "Removing the controller left an active pending check");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", timeoutTicks = 80)
    public static void pendingChecksFormOnTheServerTick(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        savedData.removeAsyncLogic(controller);
        controller.onStructureInvalid();
        state.setError(MultiblockState.UNINIT_ERROR);
        savedData.addAsyncLogic(controller);

        helper.succeedWhen(() -> {
            helper.assertTrue(controller.isFormed(), "The intact multiblock was not formed by the tick queue");
            helper.assertTrue(savedData.mapping.get(controller.getPos()) == state,
                    "The formed multiblock was not installed as the current mapping");
            helper.assertFalse(savedData.isStructureCheckPending(controller),
                    "The formed controller remained in the pending queue");
            helper.assertFalse(controller.isStructureRevalidationPending(),
                    "The formed controller remained marked for revalidation");
            helper.assertTrue(hasWorkSubscription((WorkableMultiblockMachine) controller),
                    "A successful revalidation did not restore the work subscription");
        });
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void cachedChangeQueuesWithoutReadingUnloadedPosition(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        BlockPos unloadedPos = findUnloadedPos(helper, controller.getPos());
        helper.assertTrue(controller.isFormed(), "The intact multiblock was not formed before the cached change");
        BlockPos lastCheckedPos = state.getPos();
        var previousError = state.error;
        savedData.removeAsyncLogic(controller);
        state.addPosCache(unloadedPos, new TraceabilityPredicate());
        helper.assertTrue(savedData.tryAddMapping(state), "The current controller mapping was not installed");
        var workable = (WorkableMultiblockMachine) controller;
        workable.getWorkLogic().updateTickSubscription();
        helper.assertTrue(hasWorkSubscription(workable),
                "The work subscription was not active before the cached change");
        state.onBlockStateChanged(unloadedPos, Blocks.AIR.defaultBlockState());

        helper.assertTrue(state.getPos().equals(lastCheckedPos),
                "The block-change callback read the unloaded cached position");
        helper.assertTrue(state.error == previousError,
                "The loaded block-change callback synthesized a chunk-unload pattern error");
        helper.assertTrue(controller.isStructureFormedSnapshot(),
                "The block-change callback discarded the controller's formation snapshot before its queued check");
        helper.assertFalse(controller.isFormed(),
                "The formed facade remained available before the queued check completed");
        helper.assertFalse(workable.isWorkLogicAvailable(),
                "Recipe logic remained available while the structure awaited validation");
        helper.assertFalse(hasWorkSubscription(workable),
                "The cached change did not immediately cancel the existing work subscription");
        assertMappingRemovedAndCheckPending(helper, controller, savedData, "block-change callback");
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void chunkUnloadPausesMappedStructuresWithoutReadingTheChunk(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        BlockPos unloadedPos = findUnloadedPos(helper, controller.getPos());
        ChunkPos unloadedChunk = new ChunkPos(unloadedPos);

        savedData.removeAsyncLogic(controller);
        state.addPosCache(unloadedPos, new TraceabilityPredicate());
        helper.assertTrue(savedData.tryAddMapping(state), "The mapped structure was not installed before chunk unload");
        savedData.onChunkUnload(unloadedChunk);

        helper.assertTrue(state.error == MultiblockState.UNLOAD_ERROR,
                "Chunk unload did not preserve the temporary unload reason");
        helper.assertTrue(controller.isStructureFormedSnapshot(),
                "Chunk unload invalidated the persisted formed state");
        helper.assertFalse(controller.isFormed(),
                "Chunk unload left the formed facade available before revalidation");
        assertMappingRemovedAndCheckPending(helper, controller, savedData, "chunk-unload callback");
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void queuedFullRecheckUnloadKeepsControllerFormedAndQueued(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        BlockPos unloadedPos = findUnloadedPos(helper, controller.getPos());
        var definition = (MultiblockMachineDefinition) controller.getDefinition();
        Supplier<BlockPattern> originalPatternFactory = definition.getPatternFactory();
        helper.assertTrue(controller.isFormed(), "The intact multiblock was not formed before the queued recheck");
        var confirmedCache = new LongOpenHashSet(state.getCache());
        helper.assertFalse(confirmedCache.isEmpty(),
                "The confirmed-cache regression requires an existing successful structure cache");

        savedData.removeAsyncLogic(controller);
        savedData.removeMapping(state);
        state.setError(null);
        definition.setPatternFactory(() -> new UnloadedPattern(unloadedPos));
        try {
            savedData.addAsyncLogic(controller);
            // Exercise this controller directly while the global definition is replaced. Ticking the level-wide queue
            // here would let the temporary pattern factory inspect other GameTests' controllers in the same batch.
            controller.asyncCheckPattern(savedData.getPeriodID());
        } finally {
            definition.setPatternFactory(originalPatternFactory);
        }

        helper.assertTrue(state.error == MultiblockState.UNLOAD_ERROR,
                "The queued full recheck did not encounter the unloaded structure position");
        helper.assertTrue(controller.isStructureFormedSnapshot(),
                "The queued full recheck invalidated the controller while a structure chunk was unloaded");
        helper.assertFalse(controller.isFormed(),
                "An unloaded queued check left the formed facade available");
        helper.assertTrue(state.getCache().equals(confirmedCache),
                "An incomplete scan replaced the last confirmed structure cache with its partial attempt");
        assertMappingRemovedAndCheckPending(helper, controller, savedData, "queued full recheck");
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void unloadedCandidateCannotBeOverwrittenByLaterMismatch(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var definition = (MultiblockMachineDefinition) controller.getDefinition();
        Supplier<BlockPattern> originalPatternFactory = definition.getPatternFactory();
        helper.assertTrue(controller.allowFlip(),
                "The candidate-order regression test requires a flippable controller");
        helper.assertTrue(controller.isFormed(), "The intact multiblock was not formed before the candidate check");

        savedData.removeAsyncLogic(controller);
        savedData.removeMapping(state);
        state.setError(null);
        definition.setPatternFactory(UnloadedThenMismatchPattern::new);
        try {
            savedData.addAsyncLogic(controller);
            controller.asyncCheckPattern(savedData.getPeriodID());
        } finally {
            definition.setPatternFactory(originalPatternFactory);
        }

        helper.assertTrue(state.error == MultiblockState.UNLOAD_ERROR,
                "A later flipped mismatch overwrote an earlier unloaded-chunk result");
        helper.assertTrue(controller.isStructureFormedSnapshot(),
                "Candidate fallback invalidated the controller despite an uninspectable orientation");
        helper.assertFalse(controller.isFormed(),
                "An uninspectable candidate left the formed facade available");
        assertMappingRemovedAndCheckPending(helper, controller, savedData, "candidate fallback");
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void persistedFlippedCandidateUnloadDoesNotSwitchOrientation(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var definition = (MultiblockMachineDefinition) controller.getDefinition();
        Supplier<BlockPattern> originalPatternFactory = definition.getPatternFactory();
        var oppositeFlipChecks = new AtomicInteger();
        helper.assertTrue(controller.allowFlip(),
                "The persisted-flip regression test requires a flippable controller");
        helper.assertTrue(controller.isFormed(), "The intact multiblock was not formed before the flip check");

        controller.setFlipped(true);
        savedData.removeAsyncLogic(controller);
        savedData.removeMapping(state);
        state.setError(null);
        definition.setPatternFactory(() -> new PersistedFlipUnloadPattern(oppositeFlipChecks));
        try {
            savedData.addAsyncLogic(controller);
            controller.asyncCheckPattern(savedData.getPeriodID());
        } finally {
            definition.setPatternFactory(originalPatternFactory);
        }

        helper.assertTrue(oppositeFlipChecks.get() == 0,
                "An unavailable persisted flipped structure fell back to the opposite orientation");
        helper.assertTrue(state.error == MultiblockState.UNLOAD_ERROR,
                "The unavailable persisted flipped orientation was not retained as a temporary unload");
        helper.assertTrue(controller.isStructureFormedSnapshot(),
                "The unavailable persisted flipped orientation discarded the formation snapshot");
        helper.assertTrue(controller.isStructureFlippedSnapshot(),
                "The unavailable persisted flipped orientation discarded its owned flip state");
        assertMappingRemovedAndCheckPending(helper, controller, savedData, "persisted flip");
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void unloadedOldPatternCannotSwitchToLoadedFallback(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        BlockPos unloadedPos = findUnloadedPos(helper, controller.getPos());
        var definition = (MultiblockMachineDefinition) controller.getDefinition();
        Supplier<BlockPattern> originalPatternFactory = definition.getPatternFactory();
        var fallbackChecks = new AtomicInteger();
        helper.assertTrue(controller.isFormed(), "The intact multiblock was not formed before the fallback check");

        savedData.removeAsyncLogic(controller);
        savedData.removeMapping(state);
        state.addPosCache(unloadedPos, new TraceabilityPredicate());
        helper.assertTrue(savedData.tryAddMapping(state),
                "The augmented old pattern cache could not be confirmed for the preflight regression");
        savedData.removeMapping(state);
        definition.setPatternFactory(() -> new SuccessfulFallbackPattern(fallbackChecks));
        try {
            savedData.addAsyncLogic(controller);
            controller.asyncCheckPattern(savedData.getPeriodID());
        } finally {
            definition.setPatternFactory(originalPatternFactory);
        }

        helper.assertTrue(fallbackChecks.get() == 0,
                "The controller tried a new pattern while its previous structure cache was incomplete");
        helper.assertTrue(state.error == MultiblockState.UNLOAD_ERROR,
                "An incomplete previous structure was not retained as a temporary unload");
        helper.assertTrue(controller.isStructureFormedSnapshot(),
                "Trying a loaded fallback discarded the previous formation snapshot");
        assertMappingRemovedAndCheckPending(helper, controller, savedData, "old-pattern preflight");
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void confirmedPatternCacheUsesCopyOnWriteAndConstantTimeRestore(GameTestHelper helper) {
        var state = new MultiblockState(helper.getLevel(), helper.absolutePos(LCR_CONTROLLER_POS));
        BlockPos confirmedPos = helper.absolutePos(new BlockPos(2, 2, 0));
        BlockPos attemptedPos = helper.absolutePos(new BlockPos(3, 2, 0));

        state.clean();
        state.addPosCache(confirmedPos, new TraceabilityPredicate());
        state.confirmPatternCache();
        assertCacheAliases(helper, state, true, "A confirmed cache retained a duplicate working copy");

        state.addPosCache(attemptedPos, new TraceabilityPredicate());
        assertCacheAliases(helper, state, false, "Writing through addPosCache mutated the confirmed alias");
        helper.assertTrue(state.getPosPredicateMap().containsKey(attemptedPos),
                "The detached working cache did not receive the attempted position");

        state.restoreConfirmedPatternCache();
        assertCacheAliases(helper, state, true, "Restoring a failed attempt copied instead of aliasing the cache");
        helper.assertTrue(state.isPosInCache(confirmedPos), "Failed restoration lost the confirmed position");
        helper.assertFalse(state.getPosPredicateMap().containsKey(attemptedPos),
                "Failed restoration retained an unconfirmed attempted position");

        state.clean();
        state.addPosCache(attemptedPos, new TraceabilityPredicate());
        state.restoreConfirmedPatternCache();
        assertCacheAliases(helper, state, true, "Restoring a clean scan attempt did not reuse the confirmed cache");
        helper.assertFalse(state.getPosPredicateMap().containsKey(attemptedPos),
                "A clean failed scan replaced the confirmed predicate cache");

        state.getPredicateMap().put(attemptedPos.asLong(), new TraceabilityPredicate());
        assertCacheAliases(helper, state, false, "The mutable predicate-map getter exposed the confirmed alias");
        state.restoreConfirmedPatternCache();
        helper.assertFalse(state.getPosPredicateMap().containsKey(attemptedPos),
                "Mutating the exposed predicate map corrupted the confirmed cache");

        state.getCache().clear();
        assertCacheAliases(helper, state, false, "The mutable cache getter exposed the confirmed alias");
        state.restoreConfirmedPatternCache();
        helper.assertTrue(state.isPosInCache(confirmedPos),
                "Mutating the exposed cache key set corrupted the confirmed cache");

        state.clean();
        var retainedMap = state.getPredicateMap();
        BlockPos unloadedPos = findUnloadedPos(helper, confirmedPos);
        retainedMap.put(unloadedPos.asLong(), new TraceabilityPredicate());
        state.confirmPatternCache();
        helper.assertTrue(state.hasUnloadedCachedPosition(),
                "Committing a directly edited map omitted its unloaded chunk dependency");
        retainedMap.clear();
        helper.assertTrue(state.isPosInCache(unloadedPos),
                "A mutable handle retained before commit corrupted the confirmed snapshot afterward");
        assertCacheAliases(helper, state, true, "Committing an exposed map retained two resident cache copies");

        state.clean();
        state.addPosCache(confirmedPos, new TraceabilityPredicate());
        var retainedKeys = state.getCache();
        state.confirmPatternCache();
        retainedKeys.clear();
        helper.assertTrue(state.isPosInCache(confirmedPos),
                "A key-set handle retained before commit corrupted the confirmed snapshot afterward");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void persistedConfirmedChunksGuardFirstRestartValidation(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var definition = (MultiblockMachineDefinition) controller.getDefinition();
        Supplier<BlockPattern> originalPatternFactory = definition.getPatternFactory();
        var fallbackChecks = new AtomicInteger();
        CompoundTag originalWorldTag = new CompoundTag();
        controller.saveCustomPersistedData(originalWorldTag, false);
        long[] originalChunks = originalWorldTag.getLongArray("gtceuConfirmedStructureChunks");
        helper.assertTrue(originalChunks.length > 0,
                "A successful structure mapping did not persist its confirmed chunk set");
        helper.assertTrue(Arrays.stream(originalChunks)
                .anyMatch(chunk -> chunk == new ChunkPos(controller.getPos()).toLong()),
                "The persisted mapping omitted the controller chunk");

        CompoundTag legacyTag = originalWorldTag.copy();
        legacyTag.remove("gtceuConfirmedStructureChunks");
        controller.loadCustomPersistedData(legacyTag);
        CompoundTag legacyRoundTrip = new CompoundTag();
        controller.saveCustomPersistedData(legacyRoundTrip, false);
        helper.assertFalse(legacyRoundTrip.contains("gtceuConfirmedStructureChunks"),
                "Loading an old save invented a confirmed chunk snapshot");

        BlockPos unloadedPos = findUnloadedPos(helper, controller.getPos());
        long unloadedChunk = new ChunkPos(unloadedPos).toLong();
        long[] restartChunks = Arrays.copyOf(originalChunks, originalChunks.length + 2);
        restartChunks[originalChunks.length] = unloadedChunk;
        restartChunks[originalChunks.length + 1] = unloadedChunk;
        CompoundTag restartTag = originalWorldTag.copy();
        restartTag.putLongArray("gtceuConfirmedStructureChunks", restartChunks);
        controller.loadCustomPersistedData(restartTag);
        CompoundTag restartRoundTrip = new CompoundTag();
        controller.saveCustomPersistedData(restartRoundTrip, false);
        helper.assertTrue(Arrays.stream(restartRoundTrip.getLongArray("gtceuConfirmedStructureChunks"))
                .filter(chunk -> chunk == unloadedChunk)
                .count() == 1,
                "The persisted chunk snapshot was not normalized during restart loading");

        savedData.removeAsyncLogic(controller);
        savedData.removeMapping(state);
        state.setError(null);
        definition.setPatternFactory(() -> new SuccessfulFallbackPattern(fallbackChecks));
        try {
            savedData.addAsyncLogic(controller);
            controller.asyncCheckPattern(savedData.getPeriodID());

            helper.assertTrue(fallbackChecks.get() == 0,
                    "The first restart validation scanned a fallback while an old structure chunk was unavailable");
            helper.assertTrue(state.error == MultiblockState.UNLOAD_ERROR,
                    "The persisted restart preflight did not report a temporary unload");
            helper.assertTrue(controller.isStructureFormedSnapshot(),
                    "The persisted restart preflight discarded the formation snapshot");
            helper.assertTrue(controller.isStructureRevalidationPending(),
                    "The persisted restart preflight did not keep the controller pending");
        } finally {
            definition.setPatternFactory(originalPatternFactory);
            controller.loadCustomPersistedData(originalWorldTag);
            savedData.removeAsyncLogic(controller);
        }
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void positionsOutsideBuildHeightAreDefinitiveFailures(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = new MultiblockState(level, helper.absolutePos(LCR_CONTROLLER_POS));
        var predicate = new TraceabilityPredicate();
        BlockPos belowWorld = new BlockPos(0, level.getMinBuildHeight() - 1, 0);
        BlockPos aboveWorld = new BlockPos(0, level.getMaxBuildHeight(), 0);

        helper.assertFalse(state.update(belowWorld, predicate),
                "A position below the build height was accepted by the pattern state");
        helper.assertTrue(state.error == MultiblockState.UNINIT_ERROR,
                "A position below the build height was mistaken for a temporarily unloaded chunk");
        helper.assertFalse(state.update(aboveWorld, predicate),
                "A position above the build height was accepted by the pattern state");
        helper.assertTrue(state.error == MultiblockState.UNINIT_ERROR,
                "A position above the build height was mistaken for a temporarily unloaded chunk");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void successfulReformationUnlinksPartsMissingFromNewPattern(GameTestHelper helper) {
        var controller = getController(helper);
        var removals = new AtomicInteger();
        IMultiPart stalePart = removalTrackingPart(removals);
        controller.getParts().add(stalePart);

        controller.onStructureFormed();

        helper.assertTrue(removals.get() == 1,
                "A part missing from the refreshed pattern was not definitively unlinked");
        helper.assertTrue(controller.getParts().stream().noneMatch(part -> part == stalePart),
                "A part missing from the refreshed pattern remained in the controller's part list");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void activeRegistryIdentityRepairsLaggingControllerNbt(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long activeInstance = savedData.ensureControllerActive(controller);
        helper.assertTrue(activeInstance > 0, "The loaded controller did not receive a persistent instance id");

        controller.setStructureInstanceId(0);
        long adoptedInstance = savedData.ensureControllerActive(controller);

        helper.assertTrue(adoptedInstance == activeInstance,
                "A lagging controller chunk replaced its still-active registry identity");
        helper.assertTrue(controller.getStructureInstanceId() == activeInstance,
                "The active registry identity was not written back to the lagging controller");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void retiredAndOlderControllerIdentitiesCannotBeReactivated(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        BlockPos controllerPos = controller.getPos();
        long originalInstance = savedData.ensureControllerActive(controller);
        savedData.retireController(controller);
        controller.setStructureInstanceId(0);

        long replacementInstance = savedData.ensureControllerActive(controller);
        helper.assertTrue(replacementInstance > originalInstance,
                "A controller without item identity reactivated a tombstoned incarnation");
        helper.assertTrue(savedData.isControllerBindingRetired(controllerPos, originalInstance),
                "The replaced controller incarnation was not reported retired");
        helper.assertTrue(savedData.getControllerBindingStatus(controllerPos, replacementInstance + 1) ==
                MultiblockWorldSavedData.ControllerBindingStatus.UNKNOWN,
                "A registry identity newer than the saved world record was treated as retired");

        controller.setStructureInstanceId(originalInstance);
        long afterStaleChunk = savedData.ensureControllerActive(controller);
        helper.assertTrue(afterStaleChunk > replacementInstance,
                "An older controller chunk reactivated an already replaced incarnation");
        controller.onStructureFormed();
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void newerCrashTombstoneAdvancesTheOwnerRegistry(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long activeInstance = savedData.ensureControllerActive(controller);
        long newerRemovedInstance = activeInstance + 2;

        savedData.retireControllerBinding(controller.getPos(), newerRemovedInstance);

        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), newerRemovedInstance) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "A controller epoch newer than crash-lagging SavedData lost its tombstone");
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), activeInstance) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "Advancing to a newer tombstone did not retire the older registry epoch");
        controller.setStructureInstanceId(0);
        long replacementInstance = savedData.ensureControllerActive(controller);
        helper.assertTrue(replacementInstance > newerRemovedInstance,
                "A replacement controller reused an identity at or below a newer crash tombstone");
        controller.onStructureFormed();
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void definitiveInvalidationAdvancesOwnershipEpochOnReformation(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long oldEpoch = savedData.ensureControllerActive(controller);
        var offlinePart = getPersistentPart(controller);
        savedData.removeAsyncLogic(controller);

        offlinePart.unloadedFromController(controller);
        controller.getParts().removeIf(candidate -> candidate == offlinePart);
        controller.onStructureInvalid();

        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), oldEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "A definitive structure invalidation did not retire its ownership epoch");
        helper.assertTrue(offlinePart.getControllerBindingInstanceId(controller.getPos()) == oldEpoch,
                "Invalidation could not preserve the offline part's old claim for tombstone reconciliation");

        controller.onStructureFormed();
        long newEpoch = controller.getStructureInstanceId();
        helper.assertTrue(newEpoch > oldEpoch,
                "Reforming a definitively invalidated structure reused its retired ownership epoch");
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), oldEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "Activating the new formation epoch revived the old epoch");
        helper.assertTrue(offlinePart.getControllerBindingInstanceId(controller.getPos()) == newEpoch,
                "The reformed structure did not migrate its offline part to the new ownership epoch");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void formedControllerLoadRepairsATombstoneFirstSave(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long staleFormedEpoch = savedData.ensureControllerActive(controller);
        helper.assertTrue(controller.isStructureFormedSnapshot(),
                "The tombstone-first regression requires persisted formed state");
        savedData.retireController(controller);

        controller.onLoad();

        long repairedEpoch = controller.getStructureInstanceId();
        helper.assertTrue(repairedEpoch > staleFormedEpoch,
                "Loading a persisted formation reactivated its tombstoned ownership epoch");
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), repairedEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.ACTIVE,
                "Loading a persisted formation did not publish its repaired ownership epoch");
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), staleFormedEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "Repairing a tombstone-first save revived the stale formed epoch");
        savedData.removeAsyncLogic(controller);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void unformedControllerLoadKeepsNbtFirstEpochRetired(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        savedData.ensureControllerActive(controller);
        controller.onStructureInvalid();
        long nbtFirstEpoch = savedData.ensureControllerActive(controller);
        helper.assertFalse(controller.isStructureFormedSnapshot(),
                "The NBT-first regression requires an unformed controller snapshot");
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), nbtFirstEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.ACTIVE,
                "The NBT-first regression did not create the simulated premature ACTIVE epoch");

        controller.onLoad();

        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), nbtFirstEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "Loading unformed controller NBT published an empty ACTIVE ownership epoch");
        controller.onStructureFormed();
        long formedEpoch = controller.getStructureInstanceId();
        helper.assertTrue(formedEpoch > nbtFirstEpoch,
                "The next successful formation reused the unformed NBT epoch");
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), formedEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.ACTIVE,
                "The successful formation did not publish its new ownership epoch");
        savedData.removeAsyncLogic(controller);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void unformedZeroNbtTombstonesActiveSavedData(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        savedData.ensureControllerActive(controller);
        controller.onStructureInvalid();
        long savedDataFirstEpoch = savedData.ensureControllerActive(controller);
        controller.setStructureInstanceId(0);
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), savedDataFirstEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.ACTIVE,
                "The rollback regression did not create ACTIVE SavedData ahead of controller NBT");

        controller.onLoad();

        helper.assertTrue(controller.getStructureInstanceId() == 0,
                "Loading an unformed controller adopted an ACTIVE epoch missing from its NBT");
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), savedDataFirstEpoch) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "Unformed zero-id NBT did not tombstone the higher ACTIVE SavedData epoch");
        controller.onStructureFormed();
        helper.assertTrue(controller.getStructureInstanceId() > savedDataFirstEpoch,
                "A successful formation did not advance beyond the rolled-back ACTIVE epoch");
        savedData.removeAsyncLogic(controller);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void controllerAndPartOwnerIdentitiesAreNotSavedIntoDrops(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long controllerInstance = savedData.ensureControllerActive(controller);
        var part = (MultiblockPartMachine) controller.getParts().stream()
                .filter(MultiblockPartMachine.class::isInstance)
                .findFirst()
                .orElseThrow();
        helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == controllerInstance,
                "A formed part did not persist the exact controller incarnation");

        part.unloadedFromController(controller);
        helper.assertTrue(part.isFormed(),
                "A transient runtime unlink made an owned exclusive part available to another controller");
        helper.assertTrue(part.hasController(controller),
                "A transient runtime unlink lost the exact persisted owner pair");

        CompoundTag controllerWorldTag = new CompoundTag();
        CompoundTag controllerDropTag = new CompoundTag();
        CompoundTag partWorldTag = new CompoundTag();
        CompoundTag partDropTag = new CompoundTag();
        controller.saveCustomPersistedData(controllerWorldTag, false);
        controller.saveCustomPersistedData(controllerDropTag, true);
        part.saveCustomPersistedData(partWorldTag, false);
        part.saveCustomPersistedData(partDropTag, true);

        helper.assertTrue(controllerWorldTag.getLong("gtceuStructureInstanceId") == controllerInstance,
                "A world save omitted the controller incarnation");
        helper.assertFalse(controllerDropTag.contains("gtceuStructureInstanceId"),
                "A controller item inherited the old physical incarnation");
        helper.assertTrue(controllerWorldTag.contains("gtceuConfirmedStructureChunks"),
                "A world save omitted the controller's confirmed structure chunks");
        helper.assertFalse(controllerDropTag.contains("gtceuConfirmedStructureChunks"),
                "A controller item inherited the old structure geometry");
        helper.assertTrue(partWorldTag.contains("gtceuMultiblockControllerBindings"),
                "A world save omitted the part owner pair");
        helper.assertFalse(partDropTag.contains("gtceuMultiblockControllerBindings"),
                "A part item inherited a structure owner pair");

        part.addedToController(controller);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation",
              batch = "MultiblockWorldSavedData",
              setupTicks = 40,
              timeoutTicks = 40)
    public static void retiredControllerBindingReleasesAStillLoadedPart(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long controllerInstance = savedData.ensureControllerActive(controller);
        var part = getPersistentPart(controller);
        savedData.removeAsyncLogic(controller);
        helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == controllerInstance,
                "The retirement regression requires an exact persisted owner pair");

        // This is the part-side state after its controller unloads while the part chunk remains loaded. Destruction of
        // the controller later supplies the positive tombstone; no owner chunk lookup is needed to release the part.
        part.unloadedFromController(controller);
        savedData.retireController(controller);

        helper.succeedWhen(() -> {
            helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == 0,
                    "A controller tombstone did not release its still-loaded part on the periodic reconciliation");
            helper.assertFalse(part.isFormed(),
                    "A part stayed exclusively locked after its exact controller incarnation was retired");
        });
    }

    @GameTest(template = "lcr_input_separation",
              batch = "MultiblockWorldSavedData",
              setupTicks = 40,
              timeoutTicks = 40)
    public static void loadedPositionWithoutExactControllerReleasesActiveClaim(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long claimedInstance = savedData.ensureControllerActive(controller);
        var part = getPersistentPart(controller);
        savedData.removeAsyncLogic(controller);

        part.unloadedFromController(controller);
        controller.setStructureInstanceId(claimedInstance + 1);
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), claimedInstance) ==
                MultiblockWorldSavedData.ControllerBindingStatus.ACTIVE,
                "The crash-order regression requires a stale ACTIVE registry entry");
        helper.assertTrue(savedData.isControllerPositionLoadedNoChunkRequest(controller.getPos()),
                "The controller position was not available for a non-loading absence check");

        helper.succeedWhen(() -> {
            helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == 0,
                    "A loaded position holding a different controller generation kept an ACTIVE claim forever");
            helper.assertFalse(part.isFormed(),
                    "A part stayed exclusive after its claimed controller generation was positively absent");
        });
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void firstBindingReconciliationRunsImmediatelyAfterTheOnLoadGraceTick(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long claimedInstance = savedData.ensureControllerActive(controller);
        var part = getPersistentPart(controller);
        savedData.removeAsyncLogic(controller);

        part.unloadedFromController(controller);
        controller.setStructureInstanceId(claimedInstance + 1);
        resetBindingReconciliationDelay(part);

        tickControllerBindings(part);
        helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == claimedInstance,
                "The first reconciliation tick did not preserve the same-chunk block-entity load grace period");
        tickControllerBindings(part);
        helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == 0,
                "The second reconciliation tick waited for the 20-tick cadence before releasing an absent owner");

        controller.setStructureInstanceId(claimedInstance);
        part.addedToController(controller);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation",
              batch = "MultiblockWorldSavedData",
              setupTicks = 40,
              timeoutTicks = 40)
    public static void loadedPositionWithoutExactControllerReleasesUnknownNewerClaim(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long registryInstance = savedData.ensureControllerActive(controller);
        long newerClaim = registryInstance + 1;
        var part = getPersistentPart(controller);
        savedData.removeAsyncLogic(controller);

        part.unloadedFromController(controller);
        CompoundTag persistedPart = new CompoundTag();
        ListTag bindings = new ListTag();
        CompoundTag binding = new CompoundTag();
        binding.putLong("pos", controller.getPos().asLong());
        binding.putLong("instanceId", newerClaim);
        bindings.add(binding);
        persistedPart.put("gtceuMultiblockControllerBindings", bindings);
        part.loadCustomPersistedData(persistedPart);
        controller.setStructureInstanceId(newerClaim + 1);

        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), newerClaim) ==
                MultiblockWorldSavedData.ControllerBindingStatus.UNKNOWN,
                "A claim newer than the crash-lagging registry was not kept UNKNOWN");
        helper.assertTrue(savedData.isControllerPositionLoadedNoChunkRequest(controller.getPos()),
                "The controller position was not entity-ticking for a positive absence check");

        helper.succeedWhen(() -> {
            helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == 0,
                    "An UNKNOWN newer claim survived even though its exact owner was positively absent");
            helper.assertFalse(part.isFormed(),
                    "An UNKNOWN orphan kept a part exclusive after the owner position was fully ticking");
        });
    }

    @GameTest(template = "lcr_input_separation",
              batch = "MultiblockWorldSavedData",
              setupTicks = 40,
              timeoutTicks = 40)
    public static void activeButDefinitivelyInvalidControllerReleasesAnOfflinePart(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        long controllerInstance = savedData.ensureControllerActive(controller);
        var part = getPersistentPart(controller);
        savedData.removeAsyncLogic(controller);

        part.unloadedFromController(controller);
        controller.getParts().removeIf(candidate -> candidate == part);
        controller.onStructureInvalid();
        helper.assertTrue(savedData.getControllerBindingStatus(controller.getPos(), controllerInstance) ==
                MultiblockWorldSavedData.ControllerBindingStatus.RETIRED,
                "A definitive invalidation left the controller ownership epoch active");

        helper.succeedWhen(() -> {
            helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == 0,
                    "A loaded, definitively unformed controller kept an offline part locked");
            helper.assertFalse(part.isFormed(),
                    "A part stayed exclusive after its loaded owner completed an invalid structure check");
        });
    }

    @GameTest(template = "lcr_input_separation",
              batch = "MultiblockWorldSavedData",
              setupTicks = 40,
              timeoutTicks = 40)
    public static void operationalControllerCannotKeepAnUnattachedPartClaim(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        savedData.ensureControllerActive(controller);
        var part = getPersistentPart(controller);
        savedData.removeAsyncLogic(controller);

        part.unloadedFromController(controller);
        controller.getParts().removeIf(candidate -> candidate == part);
        helper.assertTrue(controller.isStructureOperational(),
                "The membership regression requires a fully validated controller");

        helper.succeedWhen(() -> {
            helper.assertTrue(part.getControllerBindingInstanceId(controller.getPos()) == 0,
                    "An operational controller retained a part absent from its runtime identity membership");
            helper.assertFalse(part.isFormed(),
                    "A part stayed exclusive after its operational owner stopped attaching it");
        });
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData", setupTicks = 40)
    public static void definitiveMismatchInvalidatesButKeepsRetryQueued(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var definition = (MultiblockMachineDefinition) controller.getDefinition();
        Supplier<BlockPattern> originalPatternFactory = definition.getPatternFactory();
        helper.assertTrue(controller.isFormed(), "The intact multiblock was not formed before the mismatch check");
        CompoundTag formedTag = new CompoundTag();
        controller.saveCustomPersistedData(formedTag, false);
        helper.assertTrue(formedTag.contains("gtceuConfirmedStructureChunks"),
                "The invalidation regression requires a persisted confirmed chunk snapshot");

        savedData.removeAsyncLogic(controller);
        savedData.removeMapping(state);
        state.setError(null);
        definition.setPatternFactory(DefinitiveMismatchPattern::new);
        try {
            savedData.addAsyncLogic(controller);
            controller.asyncCheckPattern(savedData.getPeriodID());
        } finally {
            definition.setPatternFactory(originalPatternFactory);
        }

        helper.assertFalse(controller.isStructureFormedSnapshot(),
                "A definitive mismatch retained the previous formation snapshot");
        helper.assertFalse(controller.isFormed(), "A definitive mismatch left the formed facade available");
        helper.assertTrue(state.error != MultiblockState.UNLOAD_ERROR,
                "A definitive mismatch was misclassified as a temporary unload");
        helper.assertFalse(savedData.mapping.containsKey(controller.getPos()),
                "The definitive mismatch left an unverified structure mapping installed");
        helper.assertTrue(savedData.isStructureCheckPending(controller),
                "The definitive mismatch did not leave the controller queued for a retry");
        helper.assertFalse(controller.isStructureRevalidationPending(),
                "A definitive mismatch remained marked as transiently awaiting revalidation");
        CompoundTag invalidTag = new CompoundTag();
        controller.saveCustomPersistedData(invalidTag, false);
        helper.assertFalse(invalidTag.contains("gtceuConfirmedStructureChunks"),
                "A definitive structure invalidation retained the old confirmed chunk snapshot");
        savedData.removeAsyncLogic(controller);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void staleMappingCannotRemoveItsReplacement(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        BlockPos controllerPos = controller.getPos();
        var stale = new MultiblockState(helper.getLevel(), controllerPos);
        var current = controller.getMultiblockState();
        stale.clean();
        current.clean();
        BlockPos indexedPos = helper.absolutePos(new BlockPos(48, 2, 0));
        current.addPosCache(indexedPos, new TraceabilityPredicate());

        helper.assertTrue(savedData.tryAddMapping(current), "The current controller mapping was not installed");
        helper.assertFalse(savedData.tryAddMapping(stale), "A stale controller mapping was accepted");
        savedData.removeMapping(stale);

        helper.assertTrue(savedData.mapping.get(controllerPos) == current,
                "A stale state replaced or removed the current controller's mapping");
        helper.assertTrue(savedData.getControllersInChunk(new ChunkPos(indexedPos)).contains(current),
                "Removing a stale state also removed the current state's chunk index");
        savedData.removeMapping(current);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void remappingRefreshesDynamicChunkIndexes(GameTestHelper helper) {
        var controller = getController(helper);
        var state = controller.getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        BlockPos oldPos = helper.absolutePos(new BlockPos(48, 2, 0));
        BlockPos newPos = helper.absolutePos(new BlockPos(80, 2, 0));
        ChunkPos oldChunk = new ChunkPos(oldPos);
        ChunkPos newChunk = new ChunkPos(newPos);

        state.clean();
        state.addPosCache(oldPos, new TraceabilityPredicate());
        helper.assertTrue(savedData.tryAddMapping(state), "The original controller mapping was not installed");
        state.clean();
        state.addPosCache(newPos, new TraceabilityPredicate());
        helper.assertTrue(savedData.tryAddMapping(state), "The refreshed controller mapping was not installed");

        helper.assertFalse(savedData.getControllersInChunk(oldChunk).contains(state),
                "Refreshing a dynamic pattern left its old chunk index behind");
        helper.assertTrue(savedData.getControllersInChunk(newChunk).contains(state),
                "Refreshing a dynamic pattern did not install its new chunk index");
        savedData.removeMapping(state);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void cancelledQueueTokensRemainBoundedWithAnActiveSentinel(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        int baselinePending = savedData.getPendingStructureCheckCount();
        IMultiController sentinel = countingController(controller, new AtomicInteger(),
                Collections.newSetFromMap(new IdentityHashMap<>()));
        IMultiController churn = countingController(controller, new AtomicInteger(),
                Collections.newSetFromMap(new IdentityHashMap<>()));
        savedData.addAsyncLogic(sentinel);

        for (int i = 0; i < 256; i++) {
            savedData.addAsyncLogic(churn);
            savedData.removeAsyncLogic(churn);
        }

        helper.assertTrue(savedData.getPendingStructureCheckCount() == baselinePending + 1,
                "Cancelled controllers leaked into the active pending set");
        helper.assertTrue(savedData.getQueuedStructureCheckTokenCount() <=
                Math.max(64, savedData.getPendingStructureCheckCount() * 2),
                "Cancelled controller tokens grew the queue beyond its rebuild bound");
        savedData.removeAsyncLogic(sentinel);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void queueCompactionDoesNotDuplicateInFlightToken(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var actorCalls = new AtomicInteger();
        IMultiController churn = countingController(controller, new AtomicInteger(),
                Collections.newSetFromMap(new IdentityHashMap<>()));
        IMultiController actor = callbackController(controller, actorCalls, () -> {
            // Force removeAsyncLogic() to rebuild the ordered queue while actor's token is out but still active.
            // Copying that in-flight token here would make tick() append the same mutable token twice.
            for (int i = 0; i < 128; i++) {
                savedData.addAsyncLogic(churn);
                savedData.removeAsyncLogic(churn);
            }
        });
        savedData.addAsyncLogic(actor);

        for (int tick = 0; tick < 128 && actorCalls.get() == 0; tick++) {
            savedData.tick();
        }

        helper.assertTrue(actorCalls.get() == 1, "The in-flight controller was not checked exactly once");
        helper.assertTrue(savedData.getQueuedStructureCheckTokenCount(actor) == 1,
                "Heap compaction duplicated the in-flight controller token");
        savedData.removeAsyncLogic(actor);
        savedData.removeAsyncLogic(churn);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockScheduler", setupTicks = 40)
    public static void invalidRetriesBackOffAndBlockChangesWakeImmediately(GameTestHelper helper) {
        var machine = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var calls = new AtomicInteger();
        var state = new MultiblockState(helper.getLevel(), machine.getPos());
        IMultiController candidate = statefulCallbackController(machine, state, calls,
                () -> state.setError(MultiblockState.UNINIT_ERROR));
        savedData.addAsyncLogic(candidate);

        tickUntilCalls(helper, savedData, calls, 1, 64);
        helper.assertTrue(savedData.getStructureCheckInvalidRetryCount(candidate) == 1,
                "The first definitive mismatch did not enter exponential backoff");
        helper.assertTrue(savedData.getStructureCheckDelay(candidate) == 20,
                "The first definitive mismatch did not use the 20-tick base delay");

        tickUntilCalls(helper, savedData, calls, 2, 64);
        helper.assertTrue(savedData.getStructureCheckInvalidRetryCount(candidate) == 2,
                "A repeated definitive mismatch did not advance the backoff exponent");
        helper.assertTrue(savedData.getStructureCheckDelay(candidate) == 40,
                "The second definitive mismatch did not double the retry delay");

        savedData.onBlockChanged(machine.getPos());
        helper.assertTrue(savedData.getStructureCheckInvalidRetryCount(candidate) == 0,
                "A relevant block change did not reset the mismatch backoff");
        helper.assertTrue(savedData.getStructureCheckDelay(candidate) == 1,
                "A relevant block change did not expedite validation to the next scheduler tick");
        tickUntilCalls(helper, savedData, calls, 3, 64);
        helper.assertTrue(savedData.getStructureCheckDelay(candidate) == 20,
                "The retry after a block-change wake did not restart at the base delay");

        savedData.removeAsyncLogic(candidate);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockScheduler", setupTicks = 40)
    public static void unloadedChecksWaitOnTheirConfirmedChunksWithoutScanning(GameTestHelper helper) {
        var machine = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var calls = new AtomicInteger();
        var state = new MultiblockState(helper.getLevel(), machine.getPos());
        long missingChunk = new ChunkPos(findUnloadedPos(helper, machine.getPos())).toLong();
        IMultiController candidate = statefulCallbackController(machine, state, calls, new long[] { missingChunk },
                () -> state.setError(MultiblockState.UNLOAD_ERROR));
        savedData.addAsyncLogic(candidate);

        tickUntilCalls(helper, savedData, calls, 1, 64);
        helper.assertTrue(savedData.isStructureCheckParked(candidate),
                "An unloaded confirmed structure was not parked after its first scan");
        for (int tick = 0; tick < 80; tick++) {
            savedData.tick();
        }
        helper.assertTrue(calls.get() == 1,
                "Cheap unloaded-chunk readiness probes unexpectedly ran another full pattern scan");
        savedData.onChunkLoad(new ChunkPos(machine.getPos()));
        helper.assertTrue(savedData.isStructureCheckParked(candidate),
                "An unrelated chunk-load event woke a precisely parked structure");

        savedData.removeAsyncLogic(candidate);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockScheduler", setupTicks = 40)
    public static void unloadWithoutChunkMetadataHasEventAndTimerFallbacks(GameTestHelper helper) {
        var machine = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var calls = new AtomicInteger();
        var state = new MultiblockState(helper.getLevel(), machine.getPos());
        IMultiController candidate = statefulCallbackController(machine, state, calls,
                () -> state.setError(MultiblockState.UNLOAD_ERROR));
        savedData.addAsyncLogic(candidate);

        tickUntilCalls(helper, savedData, calls, 1, 64);
        helper.assertTrue(savedData.isStructureCheckParked(candidate),
                "A compatibility controller with an unloaded result was not parked");
        helper.assertTrue(savedData.getStructureCheckDelay(candidate) == 20,
                "A parked compatibility controller did not retain a bounded readiness fallback");
        tickUntilCalls(helper, savedData, calls, 2, 64);
        helper.assertTrue(savedData.isStructureCheckParked(candidate),
                "The readiness fallback lost the parked state after another unloaded result");
        helper.assertTrue(savedData.getStructureCheckDelay(candidate) == 40,
                "Repeated non-chunk UNLOAD results did not back off their full-scan fallback");

        savedData.onChunkLoad(new ChunkPos(machine.getPos()));
        helper.assertTrue(savedData.getStructureCheckDelay(candidate) == 2,
                "A chunk-load event did not expedite a compatibility controller's fallback");
        tickUntilCalls(helper, savedData, calls, 3, 64);

        savedData.removeAsyncLogic(candidate);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = "MultiblockWorldSavedData")
    public static void queuedChecksAreBoundedAndEventuallyReachEveryController(GameTestHelper helper) {
        var controller = getController(helper);
        var savedData = MultiblockWorldSavedData.getOrCreate(helper.getLevel());
        var calls = new AtomicInteger();
        Set<IMultiController> checked = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IMultiController> queued = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            IMultiController candidate = countingController(controller, calls, checked);
            queued.add(candidate);
            savedData.addAsyncLogic(candidate);
        }

        for (int tick = 0; tick < 128 && checked.size() < queued.size(); tick++) {
            int before = calls.get();
            savedData.tick();
            helper.assertTrue(calls.get() - before <= 1,
                    "One level tick performed more than one full structure check");
        }

        helper.assertTrue(checked.size() == queued.size(),
                "The bounded scheduler starved one or more queued controllers");
        queued.forEach(savedData::removeAsyncLogic);
        helper.succeed();
    }

    private static void tickUntilCalls(GameTestHelper helper, MultiblockWorldSavedData savedData,
                                       AtomicInteger calls, int expectedCalls, int maxTicks) {
        for (int tick = 0; tick < maxTicks && calls.get() < expectedCalls; tick++) {
            savedData.tick();
        }
        helper.assertTrue(calls.get() == expectedCalls,
                "The scheduler did not perform the expected number of full checks within its bounded test window");
    }

    private static MultiblockControllerMachine getController(GameTestHelper helper) {
        return (MultiblockControllerMachine) ((IMachineBlockEntity) helper.getBlockEntity(LCR_CONTROLLER_POS))
                .getMetaMachine();
    }

    private static MultiblockPartMachine getPersistentPart(MultiblockControllerMachine controller) {
        return (MultiblockPartMachine) controller.getParts().stream()
                .filter(MultiblockPartMachine.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private static BlockPos findUnloadedPos(GameTestHelper helper, BlockPos origin) {
        for (int chunkOffset = 32; chunkOffset <= 2048; chunkOffset += 32) {
            BlockPos candidate = origin.offset(chunkOffset * 16, 0, 0);
            if (!helper.getLevel().isLoaded(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find an unloaded chunk for the multiblock regression test");
    }

    private static void assertMappingRemovedAndCheckPending(GameTestHelper helper,
                                                            MultiblockControllerMachine controller,
                                                            MultiblockWorldSavedData savedData, String action) {
        helper.assertFalse(savedData.mapping.containsKey(controller.getPos()),
                "The " + action + " left an unverified structure mapping installed");
        helper.assertTrue(savedData.isStructureCheckPending(controller),
                "The " + action + " did not leave the controller queued for a retry");
        helper.assertTrue(controller.isStructureRevalidationPending(),
                "The " + action + " did not mark the controller as awaiting revalidation");
        savedData.removeAsyncLogic(controller);
        helper.succeed();
    }

    private static boolean hasWorkSubscription(WorkableMultiblockMachine controller) {
        try {
            Field subscription = WorkLogic.class.getDeclaredField("subscription");
            subscription.setAccessible(true);
            return subscription.get(controller.getWorkLogic()) != null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not inspect the work subscription", e);
        }
    }

    private static void resetBindingReconciliationDelay(MultiblockPartMachine part) {
        setBooleanField(part, "controllerBindingReconciliationArmed", false);
        setBooleanField(part, "controllerBindingInitialReconciliationComplete", false);
    }

    private static void assertCacheAliases(GameTestHelper helper, MultiblockState state, boolean expected,
                                           String message) {
        boolean aliases = getFieldValue(state, "predicateMap") == getFieldValue(state, "confirmedPredicateMap") &&
                getFieldValue(state, "cachedChunks") == getFieldValue(state, "confirmedCachedChunks");
        helper.assertTrue(aliases == expected, message);
    }

    private static Object getFieldValue(MultiblockState state, String fieldName) {
        try {
            Field field = MultiblockState.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect multiblock pattern cache", exception);
        }
    }

    private static void setBooleanField(MultiblockPartMachine part, String fieldName, boolean value) {
        try {
            Field field = MultiblockPartMachine.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(part, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not configure controller-binding reconciliation", exception);
        }
    }

    private static void tickControllerBindings(MultiblockPartMachine part) {
        try {
            var method = MultiblockPartMachine.class.getDeclaredMethod("tickControllerBindings");
            method.setAccessible(true);
            method.invoke(part);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not invoke controller-binding reconciliation", exception);
        }
    }

    private static IMultiController countingController(MultiblockControllerMachine machine, AtomicInteger calls,
                                                       Set<IMultiController> checked) {
        MultiblockState state = new MultiblockState(machine.getLevel(), machine.getPos());
        return statefulCallbackController(machine, state, calls,
                () -> checked.add(state.lastController));
    }

    private static IMultiController statefulCallbackController(MultiblockControllerMachine machine,
                                                               MultiblockState state, AtomicInteger calls,
                                                               Runnable callback) {
        return statefulCallbackController(machine, state, calls, new long[0], callback);
    }

    private static IMultiController statefulCallbackController(MultiblockControllerMachine machine,
                                                               MultiblockState state, AtomicInteger calls,
                                                               long[] confirmedChunks, Runnable callback) {
        return (IMultiController) Proxy.newProxyInstance(IMultiController.class.getClassLoader(),
                new Class<?>[] { IMultiController.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "self" -> machine;
                        case "getMultiblockState" -> state;
                        case "getConfirmedStructureChunks" -> Arrays.copyOf(confirmedChunks, confirmedChunks.length);
                        case "asyncCheckPattern" -> {
                            calls.incrementAndGet();
                            state.lastController = (IMultiController) proxy;
                            callback.run();
                            yield null;
                        }
                        case "isStructureRevalidationPending", "isFormed", "isStructureFormedSnapshot", "isStructureOperational" -> false;
                        case "setStructureRevalidationPending" -> null;
                        case "toString" -> "queued-test-controller";
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static IMultiController callbackController(MultiblockControllerMachine machine, AtomicInteger calls,
                                                       Runnable callback) {
        return statefulCallbackController(machine, new MultiblockState(machine.getLevel(), machine.getPos()), calls,
                callback);
    }

    private static IMultiPart removalTrackingPart(AtomicInteger removals) {
        return (IMultiPart) Proxy.newProxyInstance(IMultiPart.class.getClassLoader(),
                new Class<?>[] { IMultiPart.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "removedFromController" -> {
                            removals.incrementAndGet();
                            yield null;
                        }
                        case "toString" -> "stale-test-part";
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static final class UnloadedPattern extends BlockPattern {

        private final BlockPos unloadedPos;

        private UnloadedPattern(BlockPos unloadedPos) {
            super(new TraceabilityPredicate[0][][], new RelativeDirection[0], new int[0][], new int[5]);
            this.unloadedPos = unloadedPos;
        }

        @Override
        public boolean checkPatternAt(MultiblockState state, boolean savePredicate) {
            state.clean();
            state.addPosCache(state.controllerPos, new TraceabilityPredicate());
            return state.update(unloadedPos, new TraceabilityPredicate());
        }
    }

    private static final class UnloadedThenMismatchPattern extends BlockPattern {

        private UnloadedThenMismatchPattern() {
            super(new TraceabilityPredicate[0][][], new RelativeDirection[0], new int[0][], new int[5]);
        }

        @Override
        public boolean checkPatternAt(MultiblockState state, BlockPos centerPos, Direction frontFacing,
                                      Direction upwardsFacing, boolean isFlipped, boolean savePredicate) {
            state.setError(isFlipped ? MultiblockState.UNINIT_ERROR : MultiblockState.UNLOAD_ERROR);
            return false;
        }
    }

    private static final class DefinitiveMismatchPattern extends BlockPattern {

        private DefinitiveMismatchPattern() {
            super(new TraceabilityPredicate[0][][], new RelativeDirection[0], new int[0][], new int[5]);
        }

        @Override
        public boolean checkPatternAt(MultiblockState state, boolean savePredicate) {
            state.setError(MultiblockState.UNINIT_ERROR);
            return false;
        }
    }

    private static final class PersistedFlipUnloadPattern extends BlockPattern {

        private final AtomicInteger oppositeFlipChecks;

        private PersistedFlipUnloadPattern(AtomicInteger oppositeFlipChecks) {
            super(new TraceabilityPredicate[0][][], new RelativeDirection[0], new int[0][], new int[5]);
            this.oppositeFlipChecks = oppositeFlipChecks;
        }

        @Override
        public boolean checkPatternAt(MultiblockState state, BlockPos centerPos, Direction frontFacing,
                                      Direction upwardsFacing, boolean isFlipped, boolean savePredicate) {
            if (isFlipped) {
                state.setError(MultiblockState.UNLOAD_ERROR);
                return false;
            }
            oppositeFlipChecks.incrementAndGet();
            state.setError(null);
            return true;
        }
    }

    private static final class SuccessfulFallbackPattern extends BlockPattern {

        private final AtomicInteger checks;

        private SuccessfulFallbackPattern(AtomicInteger checks) {
            super(new TraceabilityPredicate[0][][], new RelativeDirection[0], new int[0][], new int[5]);
            this.checks = checks;
        }

        @Override
        public boolean checkPatternAt(MultiblockState state, boolean savePredicate) {
            checks.incrementAndGet();
            state.setError(null);
            return true;
        }
    }
}
