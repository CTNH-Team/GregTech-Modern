package com.gregtechceu.gtceu.integration.ae2.gui;

import com.gregtechceu.gtceu.integration.ae2.gui.slot.AEFluidConfigSlotWidget;
import com.gregtechceu.gtceu.integration.ae2.slot.ConfigurableFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ConfigurableFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ConfigurableSlot;

import appeng.api.stacks.GenericStack;

public class AEFluidConfigWidget extends ConfigWidget {

    private final ConfigurableFluidList fluidList;

    public AEFluidConfigWidget(int x, int y, ConfigurableFluidList list) {
        super(x, y, list.getInventory(), list.isStocking());
        this.fluidList = list;
    }

    @Override
    void init() {
        int line;
        this.displayList = new ConfigurableSlot[this.config.length];
        this.cached = new ConfigurableSlot[this.config.length];
        for (int index = 0; index < this.config.length; index++) {
            this.displayList[index] = new ConfigurableFluidSlot();
            this.cached[index] = new ConfigurableFluidSlot();
            line = index / 8;
            this.addWidget(new AEFluidConfigSlotWidget((index - line * 8) * 18, line * (18 * 2 + 2), this, index));
        }
    }

    public boolean hasStackInConfig(GenericStack stack) {
        return fluidList.isStackConfigured(stack, true);
    }

    public boolean isAutoPull() {
        return fluidList.isAutoPull();
    }
}
