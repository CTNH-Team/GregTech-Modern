package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;

public class StockingConfigHandler extends GenericStackHandler implements IStorageWatcherNode {

    private @UnknownNullability IStackWatcher storageWatcher;
    private final Runnable changeListener;

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

        // Notify the listener that the configuration has been updated
        changeListener.run();

        if (storageWatcher == null) {
            return;
        }

        // Remove watcher entry for the previous stack
        if (oldKey != null) {
            storageWatcher.remove(oldKey);
        }

        // Register watcher entry for the new stack
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
     * Auto-refresh the configuration list by selecting the keys with the highest amounts from the ME system.
     * Only keys passing the provided filter are considered.
     *
     * @param source The ME system to pull keys from
     * @param filter A predicate to filter which keys should be included
     */
    public void autoPull(KeyCounter source, StackFilter filter) {
        int slots = getSlots();
        if (slots == 0) return;

        // Top K algorithm: Use a Min-heap to keep the top 'slotCount' entries with the highest amount
        var topEntries = new PriorityQueue<>(Comparator.comparingLong(Object2LongMap.Entry<AEKey>::getLongValue));

        for (var entry : source) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();

            if (!filter.test(key, amount)) {
                continue;
            }

            topEntries.offer(entry);
            if (topEntries.size() > slots) {
                topEntries.poll();
            }
        }

        // Fill configuration slots from highest to lowest amount
        for (int i = slots - 1; !topEntries.isEmpty(); i--) {
            var entry = topEntries.poll();
            setKeyInSlot(i, entry.getKey());
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

    public interface StackFilter {
        boolean test(AEKey key, long amount);
    }
}
