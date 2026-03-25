package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
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
public class AEKeyStorage implements Iterable<Object2IntMap.Entry<AEKey>>, ITagSerializable<ListTag>, IContentChangeAware {

    private final Object2IntMap<AEKey> storage = new Object2IntLinkedOpenHashMap<>();

    @Getter
    @Setter
    private Runnable onContentsChanged = () -> {
    };

    public void add(AEKey key, int amount) {
        if (amount <= 0) return;
        storage.mergeInt(key, amount, Integer::sum);
        onContentsChanged.run();
    }

    public int get(AEKey key) {
        return storage.getInt(key);
    }

    public void remove(AEKey key) {
        if (storage.removeInt(key) != 0) {
            onContentsChanged.run();
        }
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

    public ObjectSet<AEKey> keySet() {
        return storage.keySet();
    }

    public IntCollection values() {
        return storage.values();
    }

    public Stream<Object2IntMap.Entry<AEKey>> stream() {
        return storage.object2IntEntrySet().stream();
    }

    /**
     * Transfers stored stacks into the given inventory.
     * Removes fully transferred entries and updates partial transfers.
     */
    public void transferTo(MEStorage inventory, IActionSource source) {
        if (storage.isEmpty()) return;

        var it = storage.object2IntEntrySet().iterator();
        boolean changed = false;

        while (it.hasNext()) {
            var entry = it.next();
            int transferred = Math.toIntExact(
                    inventory.insert(entry.getKey(), entry.getIntValue(), Actionable.MODULATE, source)
            );
            if (transferred > 0) {
                changed = true;
                int remaining = entry.getIntValue() - transferred;
                if (remaining <= 0) {
                    it.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }

        if (changed) {
            onContentsChanged.run();
        }
    }

    @Override
    public Iterator<Object2IntMap.Entry<AEKey>> iterator() {
        return storage.object2IntEntrySet().iterator();
    }

    @Override
    public ListTag serializeNBT() {
        var list = new ListTag();
        for (var entry : storage.object2IntEntrySet()) {
            var tag = new CompoundTag();
            tag.put("key", entry.getKey().toTagGeneric());
            tag.putInt("amount", entry.getIntValue());
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
