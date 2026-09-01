package com.gregtechceu.gtceu.api.machine.trait.feature;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;

/** A machine trait that contributes controls to a fancy machine UI. */
public interface IAttachConfiguratorsTrait {

    void attachConfigurators(ConfiguratorPanel left, ConfiguratorPanel right);
}
