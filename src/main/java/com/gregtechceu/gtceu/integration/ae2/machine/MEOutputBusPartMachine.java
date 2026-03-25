package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.gui.list.AEListGridWidget;
import com.gregtechceu.gtceu.integration.ae2.utils.AEKeyStorage;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Setter;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class MEOutputBusPartMachine extends MEBusPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEOutputBusPartMachine.class,
            MEBusPartMachine.MANAGED_FIELD_HOLDER
    );

    @Persisted
    private final AEKeyStorage storage = new AEKeyStorage();

    public MEOutputBusPartMachine(IMachineBlockEntity holder) {
        this(holder, holder.getDefinition().getTier());
    }

    public MEOutputBusPartMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, IO.OUT);
        this.inventory = new InaccessibleInfiniteHandler(this);
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
        group.addWidget(new AEListGridWidget.Item(5, 20, 3, this.internalBuffer));
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
            inventory.onContentsChanged();
            updateSubscription();
        });
    }

    static class Adapter extends NotifiableItemStackHandler {

        private static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
                Adapter.class,
                NotifiableItemStackHandler.MANAGED_FIELD_HOLDER
        );

        private final AEKeyStorage storage;
        @Setter
        private Predicate<ItemStack> filter = stack -> true;

        public Adapter(MetaMachine machine, AEKeyStorage storage) {
            super(machine, 0, IO.OUT, IO.NONE);
            this.storage = storage;
            this.storage.setOnContentsChanged(this::onContentsChanged);
        }

        @Override
        public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate) {
            if (io != handlerIO) return left;
            if (io != IO.IN && io != IO.OUT) return left.isEmpty() ? null : left;

            // Temporarily remove listener so that we can broadcast the entire set of transactions once
            Runnable listener = storage.getOnContentsChanged();
            storage.setOnContentsChanged(() -> {});
            boolean changed = false;

            // Store the ItemStack in each slot after an operation
            // Necessary for simulation since we don't actually modify the slot's contents
            // Doesn't hurt for execution, and definitely cheaper than copying the entire storage
            ItemStack[] visited = new ItemStack[storage.getSlots()];
            for (var it = left.listIterator(); it.hasNext();) {
                var ingredient = it.next();
                if (ingredient.isEmpty()) {
                    it.remove();
                    continue;
                }

                ItemStack[] items;
                int amount;
                if (ingredient instanceof IntProviderIngredient provider) {
                    provider.setItemStacks(null);
                    provider.setSampledCount(-1);

                    ItemStack output;
                    if (simulate) {
                        output = provider.getMaxSizeStack();
                        items = new ItemStack[] { output };
                    } else {
                        items = provider.getItems();
                        if (items.length == 0 || items[0].isEmpty()) {
                            it.remove();
                            continue;
                        }
                        output = items[0];
                    }
                    amount = output.getCount();
                } else {
                    items = ingredient.getItems();
                    if (items.length == 0 || items[0].isEmpty()) {
                        it.remove();
                        continue;
                    }
                    if (ingredient instanceof SizedIngredient si) amount = si.getAmount();
                    else amount = items[0].getCount();
                }

                for (int slot = 0; slot < storage.getSlots(); ++slot) {
                    ItemStack current = visited[slot] == null ? storage.getStackInSlot(slot) : visited[slot];
                    int count = current.getCount();

                    ItemStack output = items[0].copyWithCount(amount);
                    // Only try this slot if not visited or if visited with the same type of item
                    if (visited[slot] == null || GTUtil.isSameItemSameTags(visited[slot], output)) {
                        if (count < output.getMaxStackSize() && count < storage.getSlotLimit(slot)) {
                            var remainder = getActioned(storage, slot, recipe.ingredientActions);
                            if (remainder == null) remainder = storage.insertItem(slot, output, simulate);
                            if (remainder.getCount() < amount) {
                                changed = true;
                                visited[slot] = output.copyWithCount(count + amount - remainder.getCount());
                            }
                            amount = remainder.getCount();
                        }
                    }

                    if (amount <= 0) {
                        it.remove();
                        break;
                    }
                }
                // Modify ingredient if we didn't finish it off
                if (amount > 0) {
                    if (ingredient instanceof SizedIngredient si) {
                        si.setAmount(amount);
                    } else {
                        items[0].setCount(amount);
                    }
                }
            }

            storage.setOnContentsChanged(listener);
            if (changed && !simulate) listener.run();

            return left.isEmpty() ? null : left;
        }

        @Override
        public List<Object> getContents() {
            return storage.stream()
                    .<Object>map(entry -> {
                        AEItemKey key = (AEItemKey) entry.getKey();
                        int amount = entry.getIntValue();
                        return key.toStack(amount);
                    })
                    .toList();
        }

        @Override
        public double getTotalContentAmount() {
            return storage.values().intStream().sum();
        }

        @Override
        public int getSize() {
            return storage.size();
        }

        @Override
        public boolean isEmpty() {
            return storage.isEmpty();
        }

        @Override
        public ManagedFieldHolder getFieldHolder() {
            return MANAGED_FIELD_HOLDER;
        }
    }
}
