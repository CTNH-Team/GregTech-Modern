package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;

public enum MachineNodeListener implements IGridNodeListener<MetaMachine> {

    INSTANCE;

    @Override
    public void onSaveChanges(MetaMachine nodeOwner, IGridNode node) {
        nodeOwner.onChanged();
    }

    @Override
    public void onStateChanged(MetaMachine nodeOwner, IGridNode node, State state) {
        if (nodeOwner instanceof IGridConnectedMachine machine) {
            machine.onMainNodeStateChanged(state);
        }
    }
}
