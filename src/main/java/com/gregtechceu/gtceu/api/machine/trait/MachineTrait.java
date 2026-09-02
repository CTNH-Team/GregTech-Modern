package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.utils.ManagedFieldHolderMap;

import com.lowdragmc.lowdraglib.syncdata.IEnhancedManaged;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.data.ModelData;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;
import java.util.function.Predicate;

/**
 * represents an abstract capability held by machine. Such as item, fluid, energy, etc.
 * All trait should be added while MetaMachine is creating. you cannot modify it on the fly。
 */
public abstract class MachineTrait implements IEnhancedManaged {

    static {
        ManagedFieldHolderMap.createManagedFieldHolder(MachineTrait.class);
    }

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    protected final MetaMachine machine;
    @Setter
    protected Predicate<@Nullable Direction> capabilityValidator;

    @Getter
    @Setter
    private int traitPriority = 1;

    protected MachineTrait(MetaMachine machine) {
        this.machine = machine;
        this.capabilityValidator = side -> true;
    }

    protected List<Class<?>> validMachineClasses() {
        return List.of();
    }

    @Override
    public final ManagedFieldHolder getFieldHolder() {
        return ManagedFieldHolderMap.getManagedFieldHolder(getClass());
    }

    public final boolean hasCapability(@Nullable Direction side) {
        return capabilityValidator.test(side);
    }

    @Override
    public void onChanged() {
        machine.onChanged();
    }

    public void onMachineLoad() {}

    public void onMachineUnload() {}

    public void onMachineDestroyed() {}

    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {}

    public void onWorkAllowedChanged(boolean isWorkAllowed) {}

    public void updateModelData(ModelData.Builder builder) {}

    public void writeJadeData(CompoundTag data, BlockAccessor accessor) {}

    public void appendJadeTooltip(CompoundTag data, ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {}

    public int jadePriority() {
        return 0;
    }

    public MachineRenderState getRenderState() {
        return getMachine().getRenderState();
    }

    public void setRenderState(MachineRenderState state) {
        getMachine().setRenderState(state);
    }

    /**
     * Use for data not able to be saved with the SyncData system, like optional mod compatiblity in internal machines.
     *
     * @param tag     the CompoundTag to load data from
     * @param forDrop if the save is done for dropping the machine as an item.
     */
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {}

    public void loadCustomPersistedData(@NotNull CompoundTag tag) {}

    @Override
    public void scheduleRenderUpdate() {
        machine.scheduleRenderUpdate();
    }
}
