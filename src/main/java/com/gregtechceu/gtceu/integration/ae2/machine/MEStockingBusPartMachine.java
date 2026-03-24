package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Comparator;
import java.util.PriorityQueue;

public class MEStockingBusPartMachine extends MEBusPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEStockingBusPartMachine.class,
            MEBusPartMachine.MANAGED_FIELD_HOLDER
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
    private final GenericStackHandler configStacks;

    public MEStockingBusPartMachine(IMachineBlockEntity holder, int tier, int slots, Object... args) {
        super(holder, tier, IO.IN, args);
        this.slots = slots;
        this.configStacks = new GenericStackHandler(slots) {
            @Override
            public void setStackInSlot(int slot, @Nullable GenericStack stack) {
                GenericStack oldStack = getStackInSlot(slot);
                super.setStackInSlot(slot, stack);
                if (storageWatcher == null) return;
                if (oldStack != null) {
                    storageWatcher.remove(oldStack.what());
                }
                if (stack != null) {
                    storageWatcher.add(stack.what());
                }
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
                getInventory().onContentsChanged();
            }
        });
        return nodeHost;
    }

    @Override
    protected NotifiableItemStackHandler createInventory(Object... args) {
        return new NotifiableItemStackHandler(
                this,
                getInventorySize(),
                IO.IN,
                IO.NONE,
                MEStorageBackedItemHandler::new
        );
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateInventorySubscription();
    }

    @Override
    protected void updateInventorySubscription(Direction newFacing) {
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
    public void autoIO() {
        IGrid grid = nodeHost.getMainNode().getGrid();
        if (grid == null) return;

        int updateInterval = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        if (getOffsetTimer() % updateInterval != 0) return;

        // Refresh the configuration list in auto-pull mode.
        // Sets the config to the configStacks size items with the highest amount in the ME system.
        KeyCounter cachedInv = grid.getStorageService().getCachedInventory();

        // Use a PriorityQueue to sort the stacks on size, take the first configStacks size
        // biggest stacks.
        var topItems = new PriorityQueue<>(Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue));

        for (var entry : cachedInv) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();

            if (!(key instanceof AEItemKey)) continue;

            if (amount >= minStackSize) {
                if (topItems.size() < configStacks.getSlots()) {
                    topItems.offer(entry);
                } else if (amount > topItems.peek().getLongValue()) {
                    topItems.poll();
                    topItems.offer(entry);
                }
            }
        }

        // Now, topItems is a PQ with configStacks size highest amount items in the system.
        for (int i = 0; i < configStacks.getSlots(); i++) {
            var entry = topItems.poll();
            if (entry == null) {
                configStacks.setStackInSlot(i, null);
                continue;
            }
            AEKey what = entry.getKey();
            // Since we want our items to be displayed from highest to lowest, but poll() returns
            // the lowest first, we fill in the slots starting at itemAmount-1
            configStacks.setStackInSlot(configStacks.getSlots() - i - 1, new GenericStack(what, 1));
        }
    }

    @Override
    protected int getInventorySize() {
        return slots;
    }

    public void setAutoPull(boolean autoPull) {
        this.autoPull = autoPull;
        updateInventorySubscription();
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        sideTabs.setMainTab(this); // removes the cover configurator, it's pointless and clashes with layout.
    }

//    @Override
//    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
//        super.attachConfigurators(configuratorPanel);
//        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
//                GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0, 1, 0.5),
//                GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0.5, 1, 0.5),
//                this::isAutoPull,
//                (clickData, pressed) -> setAutoPull(pressed))
//                .setTooltipsSupplier(pressed -> List.of(Component.translatable("gtceu.gui.me_bus.auto_pull_button"))));
//        configuratorPanel.attachConfigurators(new AutoStockingFancyConfigurator(this));
//    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
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

    protected CompoundTag writeConfig() {
        if (!autoPull) {

        }
        // if in auto-pull, no need to write actual configured slots, but still need to write the ghost circuit
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AutoPull", true);
        tag.putByte("GhostCircuit",
                (byte) IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0)));
        return tag;
    }

    protected void readConfig(CompoundTag tag) {
        if (tag.getBoolean("AutoPull")) {
            // if being set to auto-pull, no need to read the configured slots
            this.setAutoPull(true);
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(tag.getByte("GhostCircuit")));
            return;
        }
        // set auto pull first to avoid issues with clearing the config after reading from the data stick
        this.setAutoPull(false);

    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    final class MEStorageBackedItemHandler extends CustomItemStackHandler {

        public MEStorageBackedItemHandler(int slots) {
            super(slots);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            AEItemKey key = getConfiguredKey(slot);
            return key != null && key.matches(stack);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateSlotIndex(slot);

            IGrid grid = getActiveGrid();
            if (grid == null) return ItemStack.EMPTY;

            AEItemKey key = getConfiguredKey(slot);
            if (key == null) return ItemStack.EMPTY;

            KeyCounter cachedInv = grid.getStorageService().getCachedInventory();
            long existing = cachedInv.get(key);

            return key.toStack(GTMath.saturatedCast(existing));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;

            validateSlotIndex(slot);

            IGrid grid = getActiveGrid();
            if (grid == null) return ItemStack.EMPTY;

            AEItemKey key = getConfiguredKey(slot);
            if (key == null) return ItemStack.EMPTY;

            // Extract the items from the real net to either validate (simulate)
            // or extract (modulate) when this is called
            MEStorage networkInv = grid.getStorageService().getInventory();
            long extracted = networkInv.extract(
                    key,
                    amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                    actionSource
            );

            return key.toStack(Math.toIntExact(extracted));
        }

        private @Nullable AEItemKey getConfiguredKey(int slot) {
            GenericStack configuredStack = configStacks.getStackInSlot(slot);
            if (configuredStack == null) return null;

            assert configuredStack.what() instanceof AEItemKey;
            return (AEItemKey) configuredStack.what();
        }

        private @Nullable IGrid getActiveGrid() {
            IManagedGridNode gridNode = nodeHost.getMainNode();
            if (!gridNode.isActive()) return null;

            return gridNode.getGrid();
        }
    }
}
