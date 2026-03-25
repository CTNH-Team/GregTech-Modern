package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class MEInputHatchPartMachine extends MEHatchPartMachine implements IDataStickInteractable {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEInputHatchPartMachine.class,
            MEHatchPartMachine.MANAGED_FIELD_HOLDER
    );

    @Persisted
    protected final GenericStackHandler configHandler;

    public MEInputHatchPartMachine(IMachineBlockEntity holder, int tier, int slots, Object... args) {
        super(holder, tier, IO.IN, -1, slots, args);
        this.configHandler = new GenericStackHandler(slots);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        return new NotifiableFluidTank(this, slots, Integer.MAX_VALUE, IO.IN, IO.NONE);
    }

    @Override
    protected boolean shouldUpdateSubscription(Direction newFacing) {
        IManagedGridNode node = nodeHost.getMainNode();
        return isWorkingEnabled() && node.isActive();
    }

    @Override
    protected void autoIO() {
        IGrid grid = nodeHost.getMainNode().getGrid();
        if (grid == null) return;

        int updateInterval = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        if (self().getOffsetTimer() % updateInterval != 0) return;

        MEStorage networkInv = grid.getStorageService().getInventory();
        FluidTank[] tanks = tank.getStorages();

        for (int i = 0; i < configHandler.getSlots(); i++) {
            GenericStack configStack = configHandler.getStackInSlot(i);
            if (configStack == null) continue;
            AEFluidKey configKey = (AEFluidKey) configStack.what();
            long configAmount = configStack.amount();

            FluidTank tank = tanks[i];
            // Ensure config amount does not exceed tank capacity
            if (configAmount > tank.getCapacity()) {
                throw new IllegalArgumentException("Config amount exceeds tank capacity!");
            }

            // Ensure fluid in tank matches the config key
            if (!tank.isEmpty() && !configKey.matches(tank.getFluid())) continue;
            // Ensure tank has enough space
            int actualAmount = tank.getFluidAmount();
            if (actualAmount >= configAmount) continue;

            FluidStack requestedStack = configKey.toStack(Math.toIntExact(configAmount - actualAmount));
            int inserted = tank.fill(
                    requestedStack,
                    IFluidHandler.FluidAction.SIMULATE
            );
            if (inserted <= 0) continue;

            int extracted = Math.toIntExact(
                    networkInv.extract(configKey, inserted, Actionable.MODULATE, actionSource)
            );
            if (extracted > 0) {
                requestedStack.setAmount(extracted);
                tank.fill(requestedStack, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    @Override
    public boolean swapIO() {
        return false;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateTankSubscription();
    }

    @Override
    public void onMachineRemoved() {
        nodeHost.getMainNode().ifPresent(grid -> {
            for (var storage : tank.getStorages()) {
                FluidStack stack = storage.getFluid();
                if (stack.isEmpty()) continue;
                grid.getStorageService().getInventory().insert(
                        AEFluidKey.of(stack), stack.getAmount(), Actionable.MODULATE, actionSource
                );
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
//        group.addWidget(new AEFluidConfigWidget(3, 10, this.aeFluidHandler));
//        return group;
//    }

    @Override
    public final InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
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
    public final InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
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
        tag.put("ConfigHandler", configHandler.serializeNBT());
        tag.putByte(
                "GhostCircuit",
                (byte) IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0))
        );
        return tag;
    }

    protected void readConfig(CompoundTag tag) {
        if (tag.contains("ConfigHandler")) {
            configHandler.deserializeNBT(tag.getCompound("ConfigHandler"));
        }
        if (tag.contains("GhostCircuit")) {
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(tag.getByte("GhostCircuit")));
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
