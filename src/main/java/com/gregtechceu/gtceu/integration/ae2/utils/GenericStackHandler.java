package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Multi-slot GenericStack storage handler, similar to ItemStackHandler.
 */
public class GenericStackHandler implements ITagSerializable<CompoundTag>, IContentChangeAware {

    protected GenericStack[] stacks;

    @Getter
    @Setter
    private Runnable onContentsChanged = () -> {
    };

    public GenericStackHandler(int size) {
        this.stacks = new GenericStack[size];
    }

    public int getSlots() {
        return stacks.length;
    }

    public @Nullable GenericStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        return stacks[slot];
    }

    public @Nullable AEKey getKeyInSlot(int slot) {
        GenericStack stack = getStackInSlot(slot);
        return stack != null ? stack.what() : null;
    }

    public void setStackInSlot(int slot, @Nullable GenericStack stack) {
        validateSlotIndex(slot);
        stacks[slot] = stack;
        onContentsChanged(slot);
    }

    public void setKeyInSlot(int slot, @Nullable AEKey key) {
        setStackInSlot(slot, key != null ? new GenericStack(key, 1) : null);
    }

    protected void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= stacks.length)
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + stacks.length + ")");
    }

    protected void onContentsChanged(int slot) {
        onContentsChanged.run();
    }

    @Override
    public CompoundTag serializeNBT() {
        var nbt = new CompoundTag();
        var stacksTag = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            var stack = stacks[i];
            if (stack != null) {
                var stackTag = GenericStack.writeTag(stack);
                stackTag.putInt("Slot", i);
                stacksTag.add(stackTag);
            }
        }
        nbt.put("Stacks", stacksTag);
        nbt.putInt("Size", stacks.length);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        int size = nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : stacks.length;
        this.stacks = new GenericStack[size];

        var stacksTag = nbt.getList("Stacks", Tag.TAG_COMPOUND);
        for (int i = 0; i < stacksTag.size(); i++) {
            var stackTag = stacksTag.getCompound(i);
            int slot = stackTag.getInt("Slot");
            if (slot >= 0 && slot < stacks.length) {
                stacks[slot] = GenericStack.readTag(stackTag);
            }
        }
    }
}
