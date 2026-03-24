package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHost;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public abstract class MEHatchPartMachine extends FluidHatchPartMachine implements IGridConnectedMachine, IActionHost {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEHatchPartMachine.class,
            FluidHatchPartMachine.MANAGED_FIELD_HOLDER
    );

    protected final GridNodeHost nodeHost;
    protected final IActionSource actionSource = IActionSource.ofMachine(this);

    public MEHatchPartMachine(IMachineBlockEntity holder, int tier, IO io, int initialCapacity, int slots, Object... args) {
        super(holder, tier, io, initialCapacity, slots, args);
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
