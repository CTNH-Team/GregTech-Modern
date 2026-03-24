package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHost;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public abstract class MEBusPartMachine extends ItemBusPartMachine implements IGridConnectedMachine, IActionHost {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEBusPartMachine.class,
            ItemBusPartMachine.MANAGED_FIELD_HOLDER
    );

    protected final GridNodeHost nodeHost;
    protected final IActionSource actionSource = IActionSource.ofMachine(this);

    // bus LuV
    // UHV FluidHatchPartMachine.INITIAL_TANK_CAPACITY_1X 16
    public MEBusPartMachine(IMachineBlockEntity holder, int tier, IO io, Object... args) {
        super(holder, tier, io, args);
        this.nodeHost = createNodeHost();
    }

    protected GridNodeHost createNodeHost() {
        GridNodeHost host = new GridNodeHost(this);
        host.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(ConfigHolder.INSTANCE.compat.ae2.meHatchEnergyUsage)
                .setExposedOnSides(
                        hasFrontFacing() ? EnumSet.of(getFrontFacing()) : EnumSet.allOf(Direction.class)
                );
        return host;
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return nodeHost.getMainNode().getNode();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        nodeHost.getMainNode().setExposedOnSides(EnumSet.of(newFacing));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
