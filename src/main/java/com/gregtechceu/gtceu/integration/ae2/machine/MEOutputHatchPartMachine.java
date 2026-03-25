package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEFluidKey;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.gui.list.AEListGridWidget;
import com.gregtechceu.gtceu.integration.ae2.utils.AEKeyStorage;
import com.gregtechceu.gtceu.utils.GTMath;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Getter;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MEOutputHatchPartMachine extends MEHatchPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEOutputHatchPartMachine.class,
            MEHatchPartMachine.MANAGED_FIELD_HOLDER
    );

    @Persisted
    private AEKeyStorage internalBuffer;
    @Getter
    @Persisted
    private final NotifiableFluidTank tank;
    @Nullable
    protected TickableSubscription autoIOSubs;
    private boolean internalBufferListenerHooked;

    public MEOutputHatchPartMachine(IMachineBlockEntity holder) {
        this(holder, holder.getDefinition().getTier(), 0, 0);
    }

    public MEOutputHatchPartMachine(IMachineBlockEntity holder, int tier, int initialTankCapacity, int slots, Object... args) {
        super(holder, tier, IO.OUT);
        this.internalBuffer = new AEKeyStorage();
        this.tank = new InaccessibleInfiniteTank(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        hookInternalBufferListener();
        updateSubscription();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        IGridConnectedMachine.super.onMainNodeStateChanged(reason);
        updateSubscription();
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        super.setWorkingEnabled(workingEnabled);
        updateSubscription();
    }

    @Override
    protected void updateSubscription() {
        IManagedGridNode node = nodeHost.getMainNode();
        if (isWorkingEnabled() && node.isActive() && !internalBuffer.isEmpty()) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoIO() {
        var grid = nodeHost.getMainNode().getGrid();
        if (grid != null && !internalBuffer.isEmpty()) {
            internalBuffer.transferTo(grid.getStorageService().getInventory(), actionSource);
        }
    }

    @Override
    public void onMachineRemoved() {
        var grid = nodeHost.getMainNode().getGrid();
        if (grid != null && !internalBuffer.isEmpty())
            internalBuffer.transferTo(grid.getStorageService().getInventory(), actionSource);
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 170, 65);
        group.addWidget(new LabelWidget(5, 0, () -> nodeHost.getMainNode().isActive() ?
                "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline"));
        group.addWidget(new LabelWidget(5, 10, "gtceu.gui.waiting_list"));
        group.addWidget(new AEListGridWidget.Fluid(5, 20, 3, this.internalBuffer));
        return group;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private void hookInternalBufferListener() {
        if (internalBufferListenerHooked) return;
        internalBufferListenerHooked = true;

        Runnable previous = internalBuffer.getOnContentsChanged();
        internalBuffer.setOnContentsChanged(() -> {
            if (previous != null) previous.run();
            tank.onContentsChanged();
            updateSubscription();
        });
    }

    private class InaccessibleInfiniteTank extends NotifiableFluidTank {

        private final FluidStorageDelegate storage;

        public InaccessibleInfiniteTank(MetaMachine holder) {
            super(holder, List.of(new FluidStorageDelegate()), IO.OUT, IO.NONE);
            storage = (FluidStorageDelegate) getStorages()[0];
            allowSameFluids = true;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull List<Object> getContents() {
            return Collections.emptyList();
        }

        @Override
        public double getTotalContentAmount() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public void setFluidInTank(int tank, @NotNull FluidStack fluidStack) {}

        @Override
        public int getTankCapacity(int tank) {
            return storage.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return true;
        }

        @Override
        @Nullable
        public List<FluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<FluidIngredient> left,
                                                       boolean simulate) {
            if (io != IO.OUT) return left;
            FluidAction action = simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
            for (var it = left.iterator(); it.hasNext();) {
                var ingredient = it.next();
                if (ingredient.isEmpty()) {
                    it.remove();
                    continue;
                }
                var fluids = ingredient.getStacks();
                if (fluids.length == 0 || fluids[0].isEmpty()) {
                    it.remove();
                    continue;
                }
                FluidStack output = fluids[0];
                ingredient.shrink(storage.fill(output, action));
                if (ingredient.getAmount() <= 0) it.remove();
            }
            return left.isEmpty() ? null : left;
        }
    }

    private class FluidStorageDelegate extends CustomFluidTank {

        public FluidStorageDelegate() {
            super(0);
        }

        @Override
        public int getCapacity() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void setFluid(FluidStack fluid) {}

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.getAmount() <= 0) return 0;
            var key = AEFluidKey.of(resource.getFluid(), resource.getTag());
            int amount = resource.getAmount();
            int oldValue = GTMath.saturatedCast(internalBuffer.storage.getOrDefault(key, 0));
            int changeValue = Math.min(Integer.MAX_VALUE - oldValue, amount);
            if (changeValue > 0 && action.execute()) {
                internalBuffer.storage.put(key, oldValue + changeValue);
                internalBuffer.onChanged();
            }
            return changeValue;
        }

        @Override
        public boolean supportsFill(int tank) {
            return false;
        }

        @Override
        public boolean supportsDrain(int tank) {
            return false;
        }
    }
}
