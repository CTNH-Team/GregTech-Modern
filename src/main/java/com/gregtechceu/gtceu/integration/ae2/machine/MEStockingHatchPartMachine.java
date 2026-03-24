package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHost;
import com.gregtechceu.gtceu.integration.ae2.utils.GenericStackHandler;
import com.gregtechceu.gtceu.utils.GTMath;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.DropSaved;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class MEStockingHatchPartMachine extends MEHatchPartMachine implements IDataStickInteractable {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEStockingHatchPartMachine.class,
            MEHatchPartMachine.MANAGED_FIELD_HOLDER
    );

    protected final int slots;

    @DescSynced
    @Persisted
    @Getter
    private boolean autoPull;

    @Getter
    @Setter
    @Persisted
    @DropSaved
    private int minStackSize = 1;

    private @UnknownNullability IStackWatcher storageWatcher;

    @Persisted
    protected final GenericStackHandler configStacks;

    public MEStockingHatchPartMachine(IMachineBlockEntity holder, int tier, int slots, Object... args) {
        super(holder, tier, IO.IN, -1, slots, args);
        this.slots = slots;
        this.configStacks = new GenericStackHandler(slots) {
            @Override
            public void setStackInSlot(int slot, @Nullable GenericStack stack) {
                GenericStack oldStack = getStackInSlot(slot);
                super.setStackInSlot(slot, stack);
                if (storageWatcher != null) {
                    if (oldStack != null) {
                        storageWatcher.remove(oldStack.what());
                    }
                    if (stack != null) {
                        storageWatcher.add(stack.what());
                    }
                }
                tank.onContentsChanged();
            }
        };
    }

    @Override
    protected GridNodeHost createNodeHost() {
        GridNodeHost nodeHost = super.createNodeHost();
        nodeHost.getMainNode().addService(IStorageWatcherNode.class, new IStorageWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                storageWatcher = newWatcher;
                for (int i = 0; i < configStacks.getSlots(); i++) {
                    GenericStack stack = configStacks.getStackInSlot(i);
                    if (stack != null) {
                        storageWatcher.add(stack.what());
                    }
                }
            }

            @Override
            public void onStackChange(AEKey what, long amount) {
                tank.onContentsChanged();
            }
        });
        return nodeHost;
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        var storages = new ArrayList<CustomFluidTank>(slots);
        for (int i = 0; i < slots; i++) {
            storages.add(new MEStorageBackedFluidStorage(i));
        }
        return new NotifiableFluidTank(this, storages, IO.IN, IO.NONE);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateTankSubscription();
    }

    @Override
    protected void updateTankSubscription(Direction newFacing) {
        IManagedGridNode node = nodeHost.getMainNode();
        if (isWorkingEnabled() && node.isActive() && isAutoPull()) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
            return;
        }
        if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    @Override
    protected void autoIO() {
        IGrid grid = nodeHost.getMainNode().getGrid();
        if (grid == null) return;

        int updateInterval = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        if (getOffsetTimer() % updateInterval != 0) return;

        KeyCounter cachedInv = grid.getStorageService().getCachedInventory();
        var topFluids = new PriorityQueue<>(Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue));

        for (var entry : cachedInv) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();

            if (!(key instanceof AEFluidKey)) continue;
            if (amount < minStackSize) continue;

            if (topFluids.size() < configStacks.getSlots()) {
                topFluids.offer(entry);
            } else if (amount > topFluids.peek().getLongValue()) {
                topFluids.poll();
                topFluids.offer(entry);
            }
        }

        for (int i = 0; i < configStacks.getSlots(); i++) {
            configStacks.setStackInSlot(i, null);
        }

        for (int i = 0; i < configStacks.getSlots(); i++) {
            var entry = topFluids.poll();
            if (entry == null) break;
            configStacks.setStackInSlot(configStacks.getSlots() - i - 1, new GenericStack(entry.getKey(), 1));
        }
    }

    public void setAutoPull(boolean autoPull) {
        this.autoPull = autoPull;
        updateTankSubscription();
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        sideTabs.setMainTab(this);
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (!isRemote()) {
            setAutoPull(!autoPull);
            if (autoPull) {
                playerIn.sendSystemMessage(
                        Component.translatable("gtceu.machine.me.stocking_auto_pull_enabled"));
            } else {
                playerIn.sendSystemMessage(
                        Component.translatable("gtceu.machine.me.stocking_auto_pull_disabled"));
            }
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        if (!isRemote()) {
            CompoundTag tag = new CompoundTag();
            tag.put("MEInputHatch", writeConfig());
            dataStick.setTag(tag);
            dataStick.setHoverName(Component.translatable("gtceu.machine.me.fluid_import.data_stick.name"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_copy_settings"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        CompoundTag tag = dataStick.getTag();
        if (tag == null || !tag.contains("MEInputHatch")) {
            return InteractionResult.PASS;
        }
        if (!isRemote()) {
            readConfig(tag.getCompound("MEInputHatch"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_paste_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    protected CompoundTag writeConfig() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AutoPull", autoPull);
        if (!autoPull) {
            tag.put("ConfigStacks", configStacks.serializeNBT());
        }
        tag.putByte(
                "GhostCircuit",
                (byte) IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0))
        );
        return tag;
    }

    protected void readConfig(CompoundTag tag) {
        setAutoPull(tag.getBoolean("AutoPull"));
        if (!autoPull && tag.contains("ConfigStacks")) {
            var oldStacks = captureConfiguredKeys();
            configStacks.deserializeNBT(tag.getCompound("ConfigStacks"));
            syncStorageWatcher(oldStacks);
            tank.onContentsChanged();
        }
        if (tag.contains("GhostCircuit")) {
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(tag.getByte("GhostCircuit")));
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private @Nullable AEFluidKey getConfiguredKey(int slot) {
        GenericStack configuredStack = configStacks.getStackInSlot(slot);
        if (configuredStack == null) return null;

        assert configuredStack.what() instanceof AEFluidKey;
        return (AEFluidKey) configuredStack.what();
    }

    private @Nullable IGrid getActiveGrid() {
        IManagedGridNode gridNode = nodeHost.getMainNode();
        if (!gridNode.isActive()) return null;
        return gridNode.getGrid();
    }

    private GenericStack[] captureConfiguredKeys() {
        GenericStack[] stacks = new GenericStack[configStacks.getSlots()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = configStacks.getStackInSlot(i);
        }
        return stacks;
    }

    private void syncStorageWatcher(GenericStack[] oldStacks) {
        if (storageWatcher == null) return;

        for (GenericStack stack : oldStacks) {
            if (stack != null) {
                storageWatcher.remove(stack.what());
            }
        }
        for (int i = 0; i < configStacks.getSlots(); i++) {
            GenericStack stack = configStacks.getStackInSlot(i);
            if (stack != null) {
                storageWatcher.add(stack.what());
            }
        }
    }

    private final class MEStorageBackedFluidStorage extends CustomFluidTank {

        private final int slot;

        private MEStorageBackedFluidStorage(int slot) {
            super(Integer.MAX_VALUE);
            this.slot = slot;
        }

        @Override
        public void setFluid(FluidStack stack) {
            // no-op
        }

        @Override
        public FluidStack getFluid() {
            IGrid grid = getActiveGrid();
            if (grid == null) return FluidStack.EMPTY;

            AEFluidKey key = getConfiguredKey(slot);
            if (key == null) return FluidStack.EMPTY;

            long existing = grid.getStorageService().getCachedInventory().get(key);
            if (existing <= 0) return FluidStack.EMPTY;

            return key.toStack(GTMath.saturatedCast(existing));
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            AEFluidKey key = getConfiguredKey(slot);
            return key != null && key.matches(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public boolean supportsFill(int tank) {
            return false;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;

            AEFluidKey key = getConfiguredKey(slot);
            if (key == null || !key.matches(resource)) return FluidStack.EMPTY;

            return extract(key, resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;

            AEFluidKey key = getConfiguredKey(slot);
            if (key == null) return FluidStack.EMPTY;

            return extract(key, maxDrain, action);
        }

        private FluidStack extract(AEFluidKey key, int amount, FluidAction action) {
            IGrid grid = getActiveGrid();
            if (grid == null) return FluidStack.EMPTY;

            MEStorage networkInv = grid.getStorageService().getInventory();
            long extracted = networkInv.extract(
                    key,
                    amount,
                    action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                    actionSource
            );
            if (extracted <= 0) return FluidStack.EMPTY;

            if (action.execute()) {
                onContentsChanged();
            }
            return key.toStack(Math.toIntExact(extracted));
        }
    }
}
