package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.IWorkLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.feature.IMultiblockMachineTrait;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.common.machine.trait.multiblock.ParallelHatchTrait;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockControllerMachine extends MetaMachine implements IMultiController {

    private static final String STRUCTURE_INSTANCE_ID = "gtceuStructureInstanceId";
    private static final String CONFIRMED_STRUCTURE_CHUNKS = "gtceuConfirmedStructureChunks";
    private static final long[] NO_CONFIRMED_STRUCTURE_CHUNKS = new long[0];

    private MultiblockState multiblockState;
    private final List<IMultiPart> parts = new ArrayList<>();
    private long structureInstanceId;
    private long[] confirmedStructureChunks = NO_CONFIRMED_STRUCTURE_CHUNKS;
    @Getter
    @DescSynced
    @UpdateListener(methodName = "onPartsUpdated")
    private BlockPos[] partPositions = new BlockPos[0];
    @Persisted
    @DescSynced
    @RequireRerender
    protected boolean isFormed;
    @Getter
    @DescSynced
    protected boolean structureRevalidationPending = true;
    @Getter
    @Setter
    @Persisted
    @DescSynced
    protected boolean isFlipped;

    public MultiblockControllerMachine(IMachineBlockEntity holder) {
        super(holder);
        attachTrait(new ParallelHatchTrait(this));
    }

    @Override
    protected void writeMachineJadeData(CompoundTag data, BlockAccessor accessor) {
        super.writeMachineJadeData(data, accessor);
        data.putBoolean("formed", isFormed());
        for (IMultiPart part : getParts()) {
            if (part instanceof IMaintenanceMachine maintenance) {
                data.putBoolean("hasMaintenanceProblems", maintenance.hasMaintenanceProblems());
                data.putInt("maintenanceProblems", maintenance.getMaintenanceProblems());
                break;
            }
        }
    }

    @Override
    protected void appendMachineJadeTooltip(CompoundTag data, ITooltip tooltip, BlockAccessor accessor,
                                            IPluginConfig config) {
        super.appendMachineJadeTooltip(data, tooltip, accessor, config);
        tooltip.add(Component.translatable(data.getBoolean("formed") ? "gtceu.top.valid_structure" :
                "gtceu.top.invalid_structure")
                .withStyle(data.getBoolean("formed") ? net.minecraft.ChatFormatting.GREEN :
                        net.minecraft.ChatFormatting.RED));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////
    @Override
    public MultiblockMachineDefinition getDefinition() {
        return (MultiblockMachineDefinition) super.getDefinition();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            if (isStructureFormedSnapshot()) {
                // A persisted formation may be newer than SavedData. Repair/adopt its epoch before revalidation.
                savedData.ensureControllerActive(this);
            } else {
                // An unformed controller must not publish or adopt an empty ACTIVE epoch. Tombstone the highest value
                // known by its NBT or SavedData; a later successful formation alone may allocate the replacement.
                savedData.retireControllerPosition(getPos(), structureInstanceId);
                clearConfirmedStructureChunks();
            }
            savedData.addAsyncLogic(this);
        }
    }

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (!forDrop && structureInstanceId > 0) {
            tag.putLong(STRUCTURE_INSTANCE_ID, structureInstanceId);
        } else {
            // A dropped and placed controller cannot inherit the previous block's ownership epoch.
            tag.remove(STRUCTURE_INSTANCE_ID);
        }
        if (!forDrop && isFormed && confirmedStructureChunks.length > 0) {
            tag.putLongArray(CONFIRMED_STRUCTURE_CHUNKS, confirmedStructureChunks);
        } else {
            // A placed controller must validate its own new structure rather than inheriting the dropped block's
            // geometry. Controllers without a confirmed structure have no geometry to persist.
            tag.remove(CONFIRMED_STRUCTURE_CHUNKS);
        }
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        structureInstanceId = Math.max(0, tag.getLong(STRUCTURE_INSTANCE_ID));
        confirmedStructureChunks = normalizeStructureChunks(tag.getLongArray(CONFIRMED_STRUCTURE_CHUNKS));
    }

    @Override
    public long getStructureInstanceId() {
        return structureInstanceId;
    }

    @Override
    public void setStructureInstanceId(long instanceId) {
        structureInstanceId = Math.max(0, instanceId);
    }

    @Override
    public void onStructureMappingInstalled(long[] mappedChunks) {
        long[] normalized = normalizeStructureChunks(mappedChunks);
        if (!Arrays.equals(confirmedStructureChunks, normalized)) {
            confirmedStructureChunks = normalized;
            markDirty();
        }
    }

    @Override
    public long[] getConfirmedStructureChunks() {
        return Arrays.copyOf(confirmedStructureChunks, confirmedStructureChunks.length);
    }

    @Override
    public void onMachineDestroyed() {
        // MetaMachineBlock invokes this synchronously only when the controller block is actually replaced. It is the
        // authoritative retirement point; ordinary chunk unloads never pass through here.
        if (getLevel() instanceof ServerLevel serverLevel) {
            var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            savedData.retireController(this);
            savedData.removeAsyncLogic(this);
            if (multiblockState != null) {
                savedData.removeMapping(multiblockState);
            }
        }
        if (isFormed || !parts.isEmpty()) {
            onStructureInvalid();
        } else {
            clearConfirmedStructureChunks();
        }
        super.onMachineDestroyed();
    }

    @Override
    public void onUnload() {
        if (getLevel() instanceof ServerLevel) {
            // Do not rely on ChunkEvent ordering: every server-side controller unload must pause gameplay and addon
            // topology before traits and runtime part links disappear. The persisted formed snapshot remains intact.
            setStructureRevalidationPending(true);
        }
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            savedData.removeAsyncLogic(this);
            if (multiblockState != null) {
                savedData.removeMapping(multiblockState);
            }
            var controllerChunk = serverLevel.getChunkSource().getChunkNow(getPos().getX() >> 4, getPos().getZ() >> 4);
            MetaMachine currentMachine = controllerChunk == null ? null : MetaMachine.getMachine(controllerChunk,
                    getPos());
            boolean controllerRemoved = controllerChunk != null &&
                    (!controllerChunk.getBlockState(getPos()).is(getBlockState().getBlock()) ||
                            currentMachine != null && currentMachine != this);
            for (IMultiPart part : List.copyOf(parts)) {
                MetaMachine partMachine = part.self();
                BlockPos partPos = partMachine.getPos();
                var partChunk = serverLevel.getChunkSource().getChunkNow(partPos.getX() >> 4, partPos.getZ() >> 4);
                if (!partMachine.isInValid() &&
                        partChunk != null && MetaMachine.getMachine(partChunk, partPos) == partMachine) {
                    if (controllerRemoved) {
                        part.removedFromController(this);
                    } else {
                        part.unloadedFromController(this);
                    }
                }
            }
            parts.clear();
            updatePartPositions();
        }
    }

    @Override
    @NotNull
    public MultiblockState getMultiblockState() {
        if (multiblockState == null) {
            multiblockState = new MultiblockState(getLevel(), getPos());
        }
        return multiblockState;
    }

    @Override
    public boolean isFormed() {
        return isFormed && !structureRevalidationPending;
    }

    @Override
    public boolean isStructureFormedSnapshot() {
        return isFormed;
    }

    @Override
    public boolean isStructureFlippedSnapshot() {
        return isFlipped;
    }

    @Override
    public void setStructureRevalidationPending(boolean pending) {
        if (structureRevalidationPending == pending) {
            return;
        }
        structureRevalidationPending = pending;
        if (this instanceof IWorkLogicMachine workMachine) {
            workMachine.getWorkLogic().updateTickSubscription();
        }
        onStructureRevalidationChanged(pending);
    }

    /**
     * Called after the transient validation state changes. Controllers with independent tick subscriptions should use
     * this hook to pause them immediately and restore them after validation succeeds.
     */
    protected void onStructureRevalidationChanged(boolean pending) {}

    @SuppressWarnings("unused")
    protected void onPartsUpdated(BlockPos[] newValue, BlockPos[] oldValue) {
        parts.clear();
        for (var pos : newValue) {
            if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                parts.add(part);
            }
        }
    }

    protected void updatePartPositions() {
        this.partPositions = this.parts.isEmpty() ? new BlockPos[0] :
                this.parts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new);
    }

    @Override
    public List<IMultiPart> getParts() {
        // for the client side, when the chunk unloaded
        if (parts.size() != this.partPositions.length) {
            parts.clear();
            for (var pos : this.partPositions) {
                if (getMachine(getLevel(), pos) instanceof IMultiPart part) {
                    parts.add(part);
                }
            }
        }
        return this.parts;
    }

    /**
     * Identity membership in the already attached runtime part list. Unlike {@link #getParts()}, this never attempts
     * to resolve persisted part positions and therefore cannot request an unavailable structure chunk.
     */
    public boolean hasRuntimePart(IMultiPart part) {
        return parts.stream().anyMatch(candidate -> candidate == part);
    }

    @Override
    public Optional<IParallelHatch> getParallelHatch() {
        return getTraitOptional(ParallelHatchTrait.class).map(ParallelHatchTrait::getParallelHatch);
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////
    @Getter
    private final Lock patternLock = new ReentrantLock();

    @Override
    public void asyncCheckPattern(long periodID) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        var state = getMultiblockState();
        var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
        if (!structureRevalidationPending && !state.hasError() && isFormed &&
                savedData.mapping.get(getPos()) == state) {
            savedData.removeAsyncLogic(this);
            return;
        }
        boolean wasFormed = isFormed;
        savedData.removeMapping(state);
        if (wasFormed && (hasUnloadedConfirmedStructureChunk(serverLevel) ||
                state.hasPatternCache() && state.hasUnloadedCachedPosition())) {
            // Keep the last confirmed pattern and part ownership intact. Trying alternate orientations or dynamic
            // patterns while an old structure chunk is unavailable can produce a valid but different match, orphaning
            // parts and addon-side topology that belonged to the previous structure.
            state.setError(MultiblockState.UNLOAD_ERROR);
            setStructureRevalidationPending(true);
            return;
        }
        if (checkPatternWithLock()) {
            setFlipped(state.isNeededFlip());
            state.setError(null);
            setStructureRevalidationPending(false);
            boolean mappingInstalled = false;
            try {
                onStructureFormed();
                if (isFormed) {
                    mappingInstalled = savedData.tryAddMapping(state);
                }
                if (mappingInstalled) {
                    savedData.removeAsyncLogic(this);
                }
            } finally {
                if (!mappingInstalled) {
                    savedData.removeMapping(state);
                    setStructureRevalidationPending(true);
                }
            }
        } else if (state.error == MultiblockState.UNLOAD_ERROR) {
            // Some dynamic pattern factories probe dimensions by mutating the raw formed field. An incomplete chunk
            // is not authoritative, so retain the snapshot captured before entering arbitrary pattern code.
            isFormed = wasFormed;
            state.restoreConfirmedPatternCache();
        } else {
            // A persisted formed flag is provisional until the first post-load check. Only a definitive pattern
            // failure invalidates it; an unloaded structure remains queued until all participating chunks return.
            if (wasFormed) {
                setFlipped(false);
                onStructureInvalid();
            }
            // The controller stays in the retry queue so a later block placement can form it, but this check produced
            // an authoritative result. Clear the transient pending state only after onStructureInvalid() has dropped
            // the formed snapshot, keeping the operational facade unavailable without misclassifying a known-invalid
            // structure as still waiting on unloaded chunks.
            setStructureRevalidationPending(false);
        }
    }

    @Override
    public void onStructureFormed() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            // This must precede every part, trait, and addon formation callback. If a definitive invalidation retired
            // the previous epoch, all claims made by this formation must carry the newly allocated identity.
            var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            if (!isFormed) {
                // A new formation must never adopt a stray ACTIVE record left by non-atomic save ordering. A
                // formed-to-formed refresh skips this step and retains its current ACTIVE epoch.
                savedData.retireControllerPosition(getPos(), structureInstanceId);
            }
            savedData.ensureControllerActive(this);
        }
        // Direct formation paths such as the pattern preview also represent a completed validation.
        setStructureRevalidationPending(false);
        isFormed = true;
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
            setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, true));
        }

        List<IMultiPart> previousParts = List.copyOf(this.parts);
        List<IMultiPart> nextParts = new ArrayList<>();
        Set<IMultiPart> set = getMultiblockState().getMatchContext().getOrCreate("parts", Collections::emptySet);
        for (IMultiPart part : set) {
            if (shouldAddPartToController(part)) {
                nextParts.add(part);
            }
        }
        nextParts.sort(getPartSorter());
        Set<IMultiPart> nextPartIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
        nextPartIdentities.addAll(nextParts);
        this.parts.clear();
        this.parts.addAll(nextParts);
        updatePartPositions();
        for (IMultiPart previousPart : previousParts) {
            if (!nextPartIdentities.contains(previousPart)) {
                previousPart.removedFromController(this);
            }
        }
        // Retained parts deliberately receive the existing refresh callback as well. Several part implementations
        // rebuild transient handlers and subscriptions after every successful structure check.
        for (var part : parts) {
            part.addedToController(this);
        }
        getTraits(IMultiblockMachineTrait.class).forEach(IMultiblockMachineTrait::onStructureFormed);
    }

    @Override
    public void onStructureInvalid() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            // Structure invalidation is authoritative even though the controller block remains. Retire ownership
            // before loaded parts are unlinked so unloaded parts can reconcile the same epoch without a timeout.
            MultiblockWorldSavedData.getOrCreate(serverLevel).retireController(this);
        }
        isFormed = false;
        clearConfirmedStructureChunks();
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
            setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, false));
        }

        for (IMultiPart part : parts) {
            part.removedFromController(this);
        }
        parts.clear();
        updatePartPositions();
        getTraits(IMultiblockMachineTrait.class).forEach(IMultiblockMachineTrait::onStructureInvalid);
    }

    /**
     * Mark the state as temporarily unloaded and queue one bounded validation. If the part was actually broken, the
     * queued pattern check turns this into a definitive {@link #onStructureInvalid()} callback; a mere chunk unload
     * keeps the persisted formed state intact until all chunks return.
     */
    @Override
    public void onPartUnload() {
        parts.removeIf(part -> part.self().isInValid());
        getMultiblockState().setError(MultiblockState.UNLOAD_ERROR);
        if (getLevel() instanceof ServerLevel serverLevel) {
            var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            savedData.removeMapping(getMultiblockState());
            var controllerChunk = serverLevel.getChunkSource().getChunkNow(getPos().getX() >> 4, getPos().getZ() >> 4);
            if (!isInValid() && controllerChunk != null && MetaMachine.getMachine(controllerChunk, getPos()) == this) {
                savedData.addAsyncLogic(this);
            }
        }
        updatePartPositions();
        getTraits(IMultiblockMachineTrait.class).forEach(IMultiblockMachineTrait::onPartUnload);
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        if (oldFacing != newFacing && getLevel() instanceof ServerLevel serverLevel) {
            // invalid structure
            var mwsd = MultiblockWorldSavedData.getOrCreate(serverLevel);
            mwsd.removeMapping(getMultiblockState());
            this.onStructureInvalid();
            mwsd.addAsyncLogic(this);
        }
    }

    public boolean allowFlip() {
        return getDefinition().isAllowFlip();
    }

    @Override
    public void setUpwardsFacing(@NotNull Direction upwardsFacing) {
        if (!getDefinition().isAllowExtendedFacing()) {
            return;
        }
        if (upwardsFacing.getAxis() == Direction.Axis.Y) {
            GTCEu.LOGGER.error("Tried to set upwards facing to invalid facing {}! Skipping", upwardsFacing);
            return;
        }
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock &&
                blockState.getValue(GTBlockStateProperties.UPWARDS_FACING) != upwardsFacing) {
            getLevel().setBlockAndUpdate(getPos(),
                    blockState.setValue(GTBlockStateProperties.UPWARDS_FACING, upwardsFacing));
            if (getLevel() != null && !getLevel().isClientSide) {
                notifyBlockUpdate();
                markDirty();
                checkPattern();
            }
        }
    }

    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                              BlockHitResult hitResult) {
        if (gridSide == getFrontFacing() && allowExtendedFacing()) {
            setUpwardsFacing(playerIn.isShiftKeyDown() ? getUpwardsFacing().getCounterClockWise() :
                    getUpwardsFacing().getClockWise());
            return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
        }
        if (playerIn.isShiftKeyDown()) {
            if (gridSide == getFrontFacing() || !isFacingValid(gridSide)) {
                return InteractionResult.FAIL;
            }
            if (!isRemote()) {
                setFrontFacing(gridSide);
            }
            return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
        }
        return super.onWrenchClick(playerIn, hand, gridSide, hitResult);
    }

    @Override
    public void setFrontFacing(Direction facing) {
        super.setFrontFacing(facing);

        if (getLevel() != null && !getLevel().isClientSide) {
            checkPattern();
        }
    }

    private boolean hasUnloadedConfirmedStructureChunk(ServerLevel serverLevel) {
        for (long chunk : confirmedStructureChunks) {
            if (!serverLevel.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
                return true;
            }
        }
        return false;
    }

    private void clearConfirmedStructureChunks() {
        if (confirmedStructureChunks.length > 0) {
            confirmedStructureChunks = NO_CONFIRMED_STRUCTURE_CHUNKS;
            markDirty();
        }
    }

    private static long[] normalizeStructureChunks(long[] chunks) {
        if (chunks.length == 0) {
            return NO_CONFIRMED_STRUCTURE_CHUNKS;
        }
        long[] normalized = Arrays.copyOf(chunks, chunks.length);
        Arrays.sort(normalized);
        int unique = 1;
        for (int i = 1; i < normalized.length; i++) {
            if (normalized[i] != normalized[unique - 1]) {
                normalized[unique++] = normalized[i];
            }
        }
        return unique == normalized.length ? normalized : Arrays.copyOf(normalized, unique);
    }
}
