package com.gregtechceu.gtceu.integration.ae2.gui;

import com.gregtechceu.gtceu.integration.ae2.gui.slot.AEItemConfigSlotWidget;
import com.gregtechceu.gtceu.integration.ae2.slot.ConfigurableItemList;
import com.gregtechceu.gtceu.integration.ae2.slot.ConfigurableItemSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ConfigurableSlot;

import appeng.api.stacks.GenericStack;

public class AEItemConfigWidget extends ConfigWidget {

    private static final int SLOTS_PER_ROW = 8;
    private static final int SLOT_SIZE = 18;
    private static final int ROW_SPACING = 2;

    private final ConfigurableItemList itemList;

    public AEItemConfigWidget(int x, int y, ConfigurableItemList list) {
        super(x, y, list.getInventory(), list.isStocking());
        this.itemList = list;
    }

    @Override
    void init() {
        int line;
        this.displayList = new ConfigurableSlot[this.config.length];
        this.cached = new ConfigurableSlot[this.config.length];
        for (int index = 0; index < this.config.length; index++) {
            this.displayList[index] = new ConfigurableItemSlot();
            this.cached[index] = new ConfigurableItemSlot();
            line = index / SLOTS_PER_ROW;
            this.addWidget(new AEItemConfigSlotWidget((index - line * SLOTS_PER_ROW) * SLOT_SIZE,
                    line * (SLOT_SIZE * 2 + ROW_SPACING), this, index));
        }
    }

    public boolean hasStackInConfig(GenericStack stack) {
        return itemList.isStackConfigured(stack, true);
    }

    public boolean isAutoPull() {
        return itemList.isAutoPull();
    }
}
