package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Storage for AE2 keys with associated amounts.
 * Supports serialization, change tracking, and inventory operations.
 */
public class AEKeyStorage implements Iterable<Object2LongMap.Entry<AEKey>>, ITagSerializable<ListTag>, IContentChangeAware {

    private final Object2LongMap<AEKey> storage = new Object2LongOpenHashMap<>();

    @Getter
    @Setter
    private Runnable onContentsChanged = () -> {
    };

    /**
     * Adds a specified amount of an {@link AEKey} to the storage.
     * The stored amount will not exceed {@link Long#MAX_VALUE}.
     *
     * @param key    The key to add.
     * @param amount The quantity to add (ignored if <= 0).
     * @return The actual amount added to the storage.
     */
    public long add(AEKey key, int amount, boolean simulate) {
        if (amount <= 0) return 0;

        long existing = storage.getLong(key);
        long inserted = Math.min(amount, Long.MAX_VALUE - existing);
        if (inserted > 0 && !simulate) {
            storage.put(key, existing + inserted);
            // Notify that the storage contents have changed
            onContentsChanged.run();
        }
        return inserted;
    }

    public long get(AEKey key) {
        return storage.getLong(key);
    }

    public long remove(AEKey key, boolean simulate) {
        long amount = storage.getLong(key);
        if (amount != 0 && !simulate) {
            storage.removeLong(key);
            onContentsChanged.run();
        }
        return amount;
    }

    public void clear() {
        if (!storage.isEmpty()) {
            storage.clear();
            onContentsChanged.run();
        }
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    public int size() {
        return storage.size();
    }

    public Stream<Object2LongMap.Entry<AEKey>> stream() {
        return storage.object2LongEntrySet().stream();
    }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        return storage.object2LongEntrySet().iterator();
    }

    /**
     * Transfers stored stacks into the given inventory.
     * Removes fully transferred entries and updates partial transfers.
     */
    public void transferTo(MEStorage inventory, IActionSource source) {
        if (storage.isEmpty()) return;

        boolean changed = false;

        for (var it = iterator(); it.hasNext(); ) {
            var entry = it.next();
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            long inserted = inventory.insert(key, amount, Actionable.MODULATE, source);
            if (inserted > 0) {
                changed = true;
                long remaining = amount - inserted;
                if (remaining <= 0) {
                    it.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }

        if (changed) onContentsChanged.run();
    }

    @Override
    public ListTag serializeNBT() {
        var list = new ListTag();
        for (var entry : storage.object2LongEntrySet()) {
            var tag = new CompoundTag();
            tag.put("key", entry.getKey().toTagGeneric());
            tag.putLong("amount", entry.getLongValue());
            list.add(tag);
        }
        return list;
    }

    @Override
    public void deserializeNBT(ListTag tags) {
        storage.clear();
        for (int i = 0; i < tags.size(); i++) {
            var tag = tags.getCompound(i);
            var key = AEKey.fromTagGeneric(tag.getCompound("key"));
            if (key != null) {
                storage.put(key, tag.getInt("amount"));
            }
        }
    }
}
