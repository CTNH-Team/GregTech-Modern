package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHost;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class HullMachine extends TieredPartMachine implements IMonitorComponent {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(HullMachine.class,
            MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    private final @Nullable MachineTrait gridNodeHost;
    @Persisted
    protected NotifiableEnergyContainer energyContainer;

    public HullMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
        if (GTCEu.Mods.isAE2Loaded()) {
            this.gridNodeHost = new GridNodeHost(this);
        } else {
            this.gridNodeHost = null;
        }
        reinitializeEnergyContainer();
    }

    protected void reinitializeEnergyContainer() {
        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer = new NotifiableEnergyContainer(this, tierVoltage * 16L, tierVoltage, 1L, tierVoltage, 1L);
        this.energyContainer.setSideOutputCondition(s -> s == getFrontFacing());
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////////////////////////
    // ********** Misc **********//
    //////////////////////////////////////

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    @Override
    public IGuiTexture getComponentIcon() {
        return GuiTextures.BUTTON_CHECK; // temporary (until there's a texture that is not fully 16x16 for this)
    }
}
