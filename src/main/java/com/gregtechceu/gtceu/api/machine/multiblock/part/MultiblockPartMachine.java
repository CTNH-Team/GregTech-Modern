package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockPartMachine extends MetaMachine implements IMultiPart {

    private static final String CONTROLLER_BINDINGS = "gtceuMultiblockControllerBindings";
    private static final String CONTROLLER_POS = "pos";
    private static final String CONTROLLER_INSTANCE_ID = "instanceId";

    @DescSynced
    @UpdateListener(methodName = "onControllersUpdated")
    protected final Set<BlockPos> controllerPositions = new ObjectOpenHashSet<>(8);
    protected final SortedSet<IMultiController> controllers = new ReferenceLinkedOpenHashSet<>(8);
    private final Map<BlockPos, Long> controllerBindings = new HashMap<>();
    @Nullable
    private TickableSubscription controllerBindingSubscription;
    private boolean controllerBindingReconciliationArmed;
    private boolean controllerBindingInitialReconciliationComplete;

    protected @Nullable RecipeHandlerList handlerList;

    public MultiblockPartMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public boolean hasController(BlockPos controllerPos) {
        return controllerPositions.contains(controllerPos) || controllerBindings.containsKey(controllerPos);
    }

    @Override
    public boolean hasController(IMultiController controller) {
        if (controllers.contains(controller)) {
            return true;
        }
        BlockPos controllerPos = controller.self().getPos();
        Long boundInstance = controllerBindings.get(controllerPos);
        long controllerInstance = controller.getStructureInstanceId();
        if (boundInstance != null && controllerInstance > 0 && boundInstance == controllerInstance) {
            return true;
        }
        if (boundInstance != null && getLevel() instanceof ServerLevel serverLevel) {
            var savedData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            if (savedData.isControllerBindingRetired(controllerPos, boundInstance)) {
                retireControllerBinding(controllerPos, boundInstance);
            }
        }
        return false;
    }

    @Override
    public boolean isFormed() {
        // A persisted owner whose chunk is unavailable still owns an exclusive part. Treating the transient runtime
        // set as authoritative would let another controller steal the part and inherit locked recipe contents.
        return !controllerPositions.isEmpty() || !controllerBindings.isEmpty();
    }

    // Not sure if necessary, but added to match the Controller class
    @SuppressWarnings("unused")
    public void onControllersUpdated(Set<BlockPos> newPositions, Set<BlockPos> old) {
        controllers.clear();
        for (BlockPos blockPos : newPositions) {
            if (MetaMachine.getMachine(getLevel(), blockPos) instanceof IMultiController controller) {
                controllers.add(controller);
            }
        }
    }

    @Override
    @UnmodifiableView
    public SortedSet<IMultiController> getControllers() {
        // Necessary to rebuild the set of controllers on client-side
        if (controllers.size() != controllerPositions.size()) {
            onControllersUpdated(controllerPositions, Collections.emptySet());
        }
        return Collections.unmodifiableSortedSet(controllers);
    }

    public List<RecipeHandlerList> getRecipeHandlers() {
        return List.of(getHandlerList());
    }

    protected RecipeHandlerList getHandlerList() {
        if (handlerList == null) {
            List<IRecipeHandler<?>> handlers = new ArrayList<>();
            IO handlerIO = null;
            for (var trait : getAllTraits()) {
                if (trait instanceof IRecipeHandler<?> rht) {
                    if (handlerIO == null) handlerIO = rht.getHandlerIO();
                    handlers.add(rht);
                }
            }

            if (handlers.isEmpty()) {
                handlerList = RecipeHandlerList.NO_DATA;
            } else if (this instanceof IDistinctPart distinctPart) {
                handlerList = RecipeHandlerList.of(this::getPaintingColor, distinctPart::isDistinct, handlers);
            } else {
                handlerList = RecipeHandlerList.of(this::getPaintingColor, handlers);
            }
        }
        return handlerList;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            reconcileRetiredControllerBindings(MultiblockWorldSavedData.getOrCreate(serverLevel));
            updateControllerBindingSubscription();
        }
    }

    @Override
    public void onUnload() {
        unsubscribe(controllerBindingSubscription);
        controllerBindingSubscription = null;
        controllerBindingReconciliationArmed = false;
        controllerBindingInitialReconciliationComplete = false;
        super.onUnload();
        if (getLevel() instanceof ServerLevel serverLevel) {
            // Need to copy if > 1 so that we can unlink safely without CME
            Set<IMultiController> toIter = controllers.size() > 1 ? new ObjectOpenHashSet<>(controllers) : controllers;
            for (IMultiController controller : toIter) {
                if (serverLevel.isLoaded(controller.self().getPos())) {
                    unloadedFromController(controller);
                    controller.onPartUnload();
                }
            }
        }
        controllerPositions.clear();
        controllers.clear();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop || controllerBindings.isEmpty()) {
            // A placed part must establish ownership through a newly validated structure, never through item NBT.
            tag.remove(CONTROLLER_BINDINGS);
            return;
        }
        ListTag bindings = new ListTag();
        for (var entry : controllerBindings.entrySet()) {
            CompoundTag binding = new CompoundTag();
            binding.putLong(CONTROLLER_POS, entry.getKey().asLong());
            binding.putLong(CONTROLLER_INSTANCE_ID, entry.getValue());
            bindings.add(binding);
        }
        tag.put(CONTROLLER_BINDINGS, bindings);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        controllerBindings.clear();
        ListTag bindings = tag.getList(CONTROLLER_BINDINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < bindings.size(); i++) {
            CompoundTag binding = bindings.getCompound(i);
            long instanceId = binding.getLong(CONTROLLER_INSTANCE_ID);
            if (instanceId > 0 && binding.contains(CONTROLLER_POS, Tag.TAG_LONG)) {
                controllerBindings.put(BlockPos.of(binding.getLong(CONTROLLER_POS)), instanceId);
            }
        }
    }

    /**
     * Reconcile persisted owner pairs without requesting any owner chunk. Unknown registry state, an unloaded owner,
     * and a controller still waiting for structure validation are deliberately inconclusive and retain ownership.
     */
    public void reconcileControllerBindings(MultiblockWorldSavedData savedData) {
        for (var entry : List.copyOf(controllerBindings.entrySet())) {
            BlockPos controllerPos = entry.getKey();
            long instanceId = entry.getValue();
            var status = savedData.getControllerBindingStatus(controllerPos, instanceId);
            if (status == MultiblockWorldSavedData.ControllerBindingStatus.RETIRED) {
                retireControllerBinding(controllerPos, instanceId);
                continue;
            }
            IMultiController controller = savedData.getLoadedControllerInstance(controllerPos, instanceId);
            if (controller == null) {
                // Chunk NBT and SavedData are not saved atomically. Even an UNKNOWN pair can be newer than the
                // registry and then outlive a controller whose deletion chunk was saved first. Once this position is
                // entity-ticking, the absence of the exact ownership epoch is positive removal/replacement evidence. A
                // missing or not-yet-ticking chunk remains inconclusive and never gets requested by this check.
                if (savedData.isControllerPositionLoadedNoChunkRequest(controllerPos)) {
                    retireControllerBinding(controllerPos, instanceId);
                }
                continue;
            }
            if (controller.isStructureRevalidationPending()) {
                continue;
            }
            if (!controller.isStructureFormedSnapshot() || controller.isStructureOperational() &&
                    !controller.self().hasRuntimePart(this)) {
                retireControllerBinding(controllerPos, instanceId);
            }
        }
        updateControllerBindingSubscription();
    }

    private void reconcileRetiredControllerBindings(MultiblockWorldSavedData savedData) {
        for (var entry : List.copyOf(controllerBindings.entrySet())) {
            if (savedData.getControllerBindingStatus(entry.getKey(), entry.getValue()) ==
                    MultiblockWorldSavedData.ControllerBindingStatus.RETIRED) {
                retireControllerBinding(entry.getKey(), entry.getValue());
            }
        }
    }

    private void tickControllerBindings() {
        if (!controllerBindingReconciliationArmed) {
            // Block entities in the same freshly loaded chunk may not all have completed onLoad yet. Never interpret
            // that first transient tick as evidence that an ACTIVE controller is absent.
            controllerBindingReconciliationArmed = true;
            return;
        }
        if (!controllerBindingInitialReconciliationComplete) {
            // The second tick is already past the same-chunk onLoad window. Reconcile immediately instead of waiting
            // up to another 20 ticks with a stale source or capability still attached to a positively absent owner.
            controllerBindingInitialReconciliationComplete = true;
            if (getLevel() instanceof ServerLevel serverLevel) {
                reconcileControllerBindings(MultiblockWorldSavedData.getOrCreate(serverLevel));
            }
            return;
        }
        if (getOffsetTimer() % 20 == 0 && getLevel() instanceof ServerLevel serverLevel) {
            reconcileControllerBindings(MultiblockWorldSavedData.getOrCreate(serverLevel));
        }
    }

    private void updateControllerBindingSubscription() {
        if (!isRemote() && !controllerBindings.isEmpty()) {
            controllerBindingSubscription = subscribeServerTick(controllerBindingSubscription,
                    this::tickControllerBindings);
        } else if (controllerBindingSubscription != null) {
            controllerBindingSubscription.unsubscribe();
            controllerBindingSubscription = null;
            controllerBindingReconciliationArmed = false;
            controllerBindingInitialReconciliationComplete = false;
        }
    }

    public long getControllerBindingInstanceId(BlockPos controllerPos) {
        return controllerBindings.getOrDefault(controllerPos, 0L);
    }

    //////////////////////////////////////
    // *** Multiblock LifeCycle ***//
    //////////////////////////////////////

    @MustBeInvokedByOverriders
    @Override
    public void removedFromController(IMultiController controller) {
        BlockPos controllerPos = controller.self().getPos();
        long instanceId = controller.getStructureInstanceId();
        Long boundInstance = controllerBindings.get(controllerPos);
        unlinkRuntimeController(controller);
        if (boundInstance != null && instanceId > 0 && boundInstance == instanceId) {
            controllerBindings.remove(controllerPos);
            markDirty();
            updateControllerBindingSubscription();
            if (!isFormed()) {
                onControllerBindingRetired(controllerPos, instanceId);
            }
        }
    }

    @Override
    public void unloadedFromController(IMultiController controller) {
        unlinkRuntimeController(controller);
    }

    private void unlinkRuntimeController(IMultiController controller) {
        BlockPos controllerPos = controller.self().getPos();
        controllers.remove(controller);
        if (controllers.stream().noneMatch(candidate -> candidate.self().getPos().equals(controllerPos))) {
            controllerPositions.remove(controllerPos);
        }

        updateFormedRenderState();
    }

    private void retireControllerBinding(BlockPos controllerPos, long instanceId) {
        Long boundInstance = controllerBindings.get(controllerPos);
        if (boundInstance == null || boundInstance != instanceId) {
            return;
        }
        controllerBindings.remove(controllerPos);
        controllers.removeIf(controller -> controller.self().getPos().equals(controllerPos) &&
                controller.getStructureInstanceId() == instanceId);
        if (controllers.stream().noneMatch(controller -> controller.self().getPos().equals(controllerPos))) {
            controllerPositions.remove(controllerPos);
        }
        markDirty();
        updateFormedRenderState();
        updateControllerBindingSubscription();
        onControllerBindingRetired(controllerPos, instanceId);
    }

    private void updateFormedRenderState() {
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
            setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, isFormed()));
        }
    }

    @MustBeInvokedByOverriders
    @Override
    public void addedToController(IMultiController controller) {
        BlockPos controllerPos = controller.self().getPos().immutable();
        long instanceId = controller.getStructureInstanceId();
        if (getLevel() instanceof ServerLevel serverLevel) {
            instanceId = MultiblockWorldSavedData.getOrCreate(serverLevel).ensureControllerActive(controller);
        }
        Long previousInstance = controllerBindings.get(controllerPos);
        if (previousInstance != null && instanceId > 0 && previousInstance != instanceId) {
            retireControllerBinding(controllerPos, previousInstance);
        }
        if (instanceId > 0 && !Long.valueOf(instanceId).equals(controllerBindings.put(controllerPos, instanceId))) {
            markDirty();
        }
        updateControllerBindingSubscription();
        controllerPositions.add(controllerPos);
        controllers.add(controller);

        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
            setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, true));
        }
    }

    @Override
    public boolean replacePartModelWhenFormed() {
        var renderState = getRenderState();
        return renderState.hasProperty(GTMachineModelProperties.IS_FORMED) &&
                renderState.getValue(GTMachineModelProperties.IS_FORMED);
    }

    @Override
    @Nullable
    public BlockState getFormedAppearance(BlockState sourceState, BlockPos sourcePos, Direction side) {
        if (!replacePartModelWhenFormed()) return null;
        return IMultiPart.super.getFormedAppearance(sourceState, sourcePos, side);
    }
}
