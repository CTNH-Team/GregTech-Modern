package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.nbt.CompoundTag;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;

public class StockingConfigHandler extends GenericStackHandler implements IStorageWatcherNode {

    private @UnknownNullability IStackWatcher storageWatcher;
    private Runnable changeListener;

    public StockingConfigHandler(int size, Runnable changeListener) {
        super(size);
        this.changeListener = changeListener;
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        storageWatcher = newWatcher;
        syncWatcher();
    }

    @Override
    public void onStackChange(AEKey what, long amount) {
        changeListener.run();
    }

    @Override
    public void setStackInSlot(int slot, @Nullable GenericStack newStack) {
        AEKey oldKey = stacks[slot] == null ? null : stacks[slot].what();
        AEKey newKey = newStack == null ? null : newStack.what();

        if (Objects.equals(oldKey, newKey)) return;

        super.setStackInSlot(slot, newStack);

        // Notify listener
        changeListener.run();

        // Update watcher
        if (storageWatcher == null) {
            return;
        }

        if (oldKey != null) {
            storageWatcher.remove(oldKey);
        }

        if (newKey != null) {
            storageWatcher.add(newKey);
        }
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        syncWatcher();
    }

    /**
     * Refresh configuration with Top-K keys (by amount) from the ME storage.
     * Only keys matching the filter are considered.
     *
     * @param source ME storage source
     * @param filter key filter
     */
    public void autoPull(KeyCounter source, Filter filter) {
        int slots = getSlots();
        if (slots == 0) return;

        // Top-K via PriorityQueue: keep the highest 'slotCount' entries by amount
        var topEntries = new PriorityQueue<>(Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue));

        for (var entry : source) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();

            if (!filter.test(key, amount)) {
                continue;
            }

            assert topEntries.peek() != null;
            if (topEntries.size() < slots) {
                // If the heap is not full, add the entry
                topEntries.offer(entry);
            } else if (topEntries.peek().getLongValue() < amount) {
                // If the heap is full but the current entry has a higher amount, replace the smallest entry
                topEntries.poll();
                topEntries.offer(entry);
            }
        }

        // Suppress listener; batch updates with a single notification
        Runnable original = this.changeListener;
        MutableBoolean changed = new MutableBoolean(false);
        this.changeListener = changed::setTrue;
        // Fill slots from highest to lowest
        for (int i = slots - 1; i >= 0; i--) {
            // Pad with null if no entry available
            if (i >= topEntries.size()) {
                setKeyInSlot(i, null);
                continue;
            }
            var entry = topEntries.poll();
            setKeyInSlot(i, entry.getKey());
        }
        // Restore listener and emit once if changed
        this.changeListener = original;
        if (changed.booleanValue()) {
            changeListener.run();
        }
    }

    private void syncWatcher() {
        if (storageWatcher == null) return;

        storageWatcher.reset();
        for (GenericStack stack : stacks) {
            if (stack != null) {
                storageWatcher.add(stack.what());
            }
        }
    }

    public interface Filter {
        boolean test(AEKey key, long amount);
    }
}
