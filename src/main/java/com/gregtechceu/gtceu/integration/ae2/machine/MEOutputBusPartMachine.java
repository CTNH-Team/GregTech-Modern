package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.integration.ae2.gui.list.AEListGridWidget;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.KeyStorageBakedHandler;
import com.gregtechceu.gtceu.integration.ae2.utils.AEKeyStorage;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import org.apache.commons.lang3.ArrayUtils;

public class MEOutputBusPartMachine extends MEBusPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEOutputBusPartMachine.class,
            MEBusPartMachine.MANAGED_FIELD_HOLDER
    );

    @Persisted
    protected final AEKeyStorage keyStorage;

    public MEOutputBusPartMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, IO.OUT, args = ArrayUtils.addAll(args, new AEKeyStorage()));
        this.keyStorage = (AEKeyStorage) args[args.length - 1];
    }

    @Override
    protected NotifiableItemStackHandler createInventory(Object... args) {
        return new KeyStorageBakedHandler(this, (AEKeyStorage) args[args.length - 1]);
    }

    @Override
    protected boolean shouldUpdateSubscription(Direction newFacing) {
        IManagedGridNode mainNode = nodeHost.getMainNode();
        return isWorkingEnabled() && mainNode.isActive() && !keyStorage.isEmpty();
    }

    @Override
    protected void autoIO() {
        if (!isMESyncTick()) return;

        IGrid grid = nodeHost.getMainNode().getGrid();
        if (grid == null) return;

        keyStorage.transferTo(grid.getStorageService().getInventory(), actionSource);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateInventorySubscription();
    }

    @Override
    public void onMachineRemoved() {
        IManagedGridNode mainNode = nodeHost.getMainNode();
        if (!keyStorage.isEmpty() && mainNode.isActive()) {
            assert mainNode.getGrid() != null;
            keyStorage.transferTo(mainNode.getGrid().getStorageService().getInventory(), actionSource);
        }
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 170, 65);
        group.addWidget(new LabelWidget(5, 0, () -> nodeHost.getMainNode().isActive() ?
                "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline"));
        group.addWidget(new LabelWidget(5, 10, "gtceu.gui.waiting_list"));
        group.addWidget(new AEListGridWidget.Item(5, 20, 3, this.keyStorage));
        return group;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

}
