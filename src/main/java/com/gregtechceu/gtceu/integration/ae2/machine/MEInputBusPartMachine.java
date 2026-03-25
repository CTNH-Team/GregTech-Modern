package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.utils.GenericStackHandler;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class MEInputBusPartMachine extends MEBusPartMachine implements IDataStickInteractable {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEInputBusPartMachine.class,
            MEBusPartMachine.MANAGED_FIELD_HOLDER
    );

    @Persisted
    protected final GenericStackHandler configHandler;
    protected final int slots;

    public MEInputBusPartMachine(IMachineBlockEntity holder, int tier, int slots, Object... args) {
        super(holder, tier, IO.IN, args);
        this.configHandler = new GenericStackHandler(slots);
        this.slots = slots;
    }

    @Override
    protected NotifiableItemStackHandler createInventory(Object... args) {
        return new NotifiableItemStackHandler(
                this,
                getInventorySize(),
                IO.IN,
                IO.NONE,
                slots -> new CustomItemStackHandler(slots) {
                    @Override
                    public int getSlotLimit(int slot) {
                        return Integer.MAX_VALUE;
                    }

                    @Override
                    protected int getStackLimit(int slot, ItemStack stack) {
                        return Integer.MAX_VALUE;
                    }
                }
        );
    }

    @Override
    protected boolean shouldUpdateSubscription(Direction newFacing) {
        IManagedGridNode node = nodeHost.getMainNode();
        return isWorkingEnabled() && node.isActive();
    }

    @Override
    protected int getInventorySize() {
        return slots;
    }

    @Override
    public boolean swapIO() {
        return false;
    }

    @Override
    protected void autoIO() {
        IGrid grid = nodeHost.getMainNode().getGrid();
        if (grid == null) return;

        int updateInterval = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        if (getOffsetTimer() % updateInterval != 0) return;

        MEStorage networkInv = grid.getStorageService().getInventory();
        NotifiableItemStackHandler inventory = getInventory();

        for (int i = 0; i < configHandler.getSlots(); i++) {
            GenericStack configStack = configHandler.getStackInSlot(i);
            if (configStack == null) continue;
            AEItemKey configKey = (AEItemKey) configStack.what();
            long configAmount = configStack.amount();

            // Ensure config amount does not exceed slot limit
            if (configAmount > inventory.getSlotLimit(i)) {
                throw new IllegalArgumentException("Config amount exceeds slot capacity!");
            }

            // Ensure item in slot matches the config key
            ItemStack stackInSlot = inventory.getStackInSlot(i);
            if (!stackInSlot.isEmpty() && !configKey.matches(stackInSlot)) continue;
            // Ensure slot has enough space
            int actualAmount = stackInSlot.getCount();
            if (actualAmount >= configAmount) continue;

            ItemStack requestedStack = configKey.toStack(Math.toIntExact(configAmount - actualAmount));
            int inserted = requestedStack.getCount() - inventory.insertItem(i, requestedStack, true).getCount();
            if (inserted <= 0) continue;

            int extracted = Math.toIntExact(
                    networkInv.extract(configKey, inserted, Actionable.MODULATE, actionSource)
            );
            if (extracted > 0) {
                requestedStack.setCount(extracted);
                inventory.insertItem(i, requestedStack, false);
            }
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateInventorySubscription();
    }

    @Override
    public void onMachineRemoved() {
        nodeHost.getMainNode().ifPresent(grid -> {
            var networkInv = grid.getStorageService().getInventory();
            NotifiableItemStackHandler inventory = getInventory();
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                networkInv.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, actionSource);
            }
        });
        super.onMachineRemoved();
    }

//    @Override
//    public Widget createUIWidget() {
//        WidgetGroup group = new WidgetGroup(new Position(0, 0));
//        // ME Network status
//        group.addWidget(new LabelWidget(3, 0, () -> this.isOnline ?
//                "gtceu.gui.me_network.online" :
//                "gtceu.gui.me_network.offline"));
//
//        // Config slots
//        group.addWidget(new AEItemConfigWidget(3, 10, this.aeItemHandler));
//
//        return group;
//    }

    @Override
    public final InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        if (!isRemote()) {
            CompoundTag tag = new CompoundTag();
            tag.put("MEInputBus", writeConfig());
            dataStick.setTag(tag);
            dataStick.setHoverName(Component.translatable("gtceu.machine.me.item_import.data_stick.name"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_copy_settings"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public final InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        CompoundTag tag = dataStick.getTag();
        if (tag == null || !tag.contains("MEInputBus")) {
            return InteractionResult.PASS;
        }
        if (!isRemote()) {
            readConfig(tag.getCompound("MEInputBus"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_paste_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    protected CompoundTag writeConfig() {
        CompoundTag tag = new CompoundTag();
        tag.put("ConfigHandler", configHandler.serializeNBT());
        tag.putByte(
                "GhostCircuit",
                (byte) IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0))
        );
        tag.putBoolean("DistinctBuses", isDistinct());
        return tag;
    }

    protected void readConfig(CompoundTag tag) {
        if (tag.contains("ConfigHandler")) {
            configHandler.deserializeNBT(tag.getCompound("ConfigHandler"));
        }
        if (tag.contains("GhostCircuit")) {
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(tag.getByte("GhostCircuit")));
        }
        if (tag.contains("DistinctBuses")) {
            setDistinct(tag.getBoolean("DistinctBuses"));
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
