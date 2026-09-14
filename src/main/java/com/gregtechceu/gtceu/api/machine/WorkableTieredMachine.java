package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import lombok.Getter;

import java.util.function.Function;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class WorkableTieredMachine extends TieredEnergyMachine
                                            implements IAllowSameUIProvider, IWorkLogicMachine, IMachineLife {

    @Getter
    @Persisted
    @DescSynced
    protected final WorkLogic workLogic;

    public WorkableTieredMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, args);
        this.workLogic = attachTrait(createWorkLogic(args));
    }

    protected WorkableTieredMachine(IMachineBlockEntity holder, int tier,
                                    Function<MetaMachine, NotifiableEnergyContainer> energyContainerFactory) {
        super(holder, tier, energyContainerFactory);
        this.workLogic = attachTrait(createWorkLogic());
    }

    protected WorkLogic createWorkLogic(Object... args) {
        return new WorkLogic(this);
    }

    @Override
    public void serverRunningTick() {}
}
