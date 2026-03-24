package com.gregtechceu.gtceu.integration.ae2.machine.trait;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.me.InWorldGridNode;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.integration.ae2.utils.MachineNodeListener;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class GridNodeHost extends MachineTrait implements IInWorldGridNodeHost {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(GridNodeHost.class);

    @Getter
    private final IManagedGridNode mainNode;

    @Setter
    private AECableType cableType = AECableType.SMART;

    public GridNodeHost(MetaMachine machine) {
        super(machine);
        this.mainNode = GridHelper.createManagedNode(machine, MachineNodeListener.INSTANCE)
                .setInWorldNode(true)
                .setVisualRepresentation(machine.getDefinition().getItem());
    }

    public @Nullable IGridNode getGridNode() {
        return mainNode.getNode();
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        var node = this.getMainNode().getNode();

        // We use the node rather than getGridConnectableSides since the node is already using absolute sides
        if (node instanceof InWorldGridNode inWorldGridNode && inWorldGridNode.isExposedOnSide(dir)) {
            return node;
        }

        return null;
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return cableType;
    }

    @Override
    public void onMachineLoad() {
        // ensure the node is created after calling IManagedGridNode#loadFromNBT()
        if (machine.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(
                    new TickTask(0, () -> mainNode.create(machine.getLevel(), machine.getPos()))
            );
        }
    }

    @Override
    public void onMachineUnLoad() {
        mainNode.destroy();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        mainNode.saveToNBT(tag);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        mainNode.loadFromNBT(tag);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
