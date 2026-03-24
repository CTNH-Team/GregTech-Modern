package com.gregtechceu.gtceu.integration.ae2.machine.feature;

import appeng.api.networking.IGridNodeListener;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;

/**
 * A machine that can connect to ME network.
 */
public interface IGridConnectedMachine extends IMachineFeature {

    /**
     * Called when the block entities main grid nodes power or channel assignment state changes. Primarily used to send
     * rendering updates to the client.
     */
    default void onMainNodeStateChanged(IGridNodeListener.State reason) {
    }
}
