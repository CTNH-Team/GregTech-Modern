package com.gregtechceu.gtceu.integration.ae2.gui.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class StockingFancyConfigurator implements IFancyConfigurator {

    private final String minSizeLabel;
    private final String minSizeTooltip;
    private final Supplier<Integer> minSizeGetter;
    private final Consumer<Integer> minSizeSetter;
    
    public StockingFancyConfigurator(
            String minSizeLabel,
            String minSizeTooltip,
            Supplier<Integer> minSizeGetter,
            Consumer<Integer> minSizeSetter
    ) {
        this.minSizeLabel = minSizeLabel;
        this.minSizeTooltip = minSizeTooltip;
        this.minSizeGetter = minSizeGetter;
        this.minSizeSetter = minSizeSetter;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtceu.gui.adv_stocking_config.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(GTItems.TOOL_DATA_STICK.asStack());
    }

    @Override
    public Widget createConfigurator() {
        var group = new WidgetGroup(0, 0, 90, 30);

        group.addWidget(new LabelWidget(4, 2, minSizeLabel));
        group.addWidget(new IntInputWidget(4, 12, 81, 14, minSizeGetter, minSizeSetter)
                .setMin(1)
                .appendHoverTooltips(Component.translatable(minSizeTooltip)));

        return group;
    }
}
