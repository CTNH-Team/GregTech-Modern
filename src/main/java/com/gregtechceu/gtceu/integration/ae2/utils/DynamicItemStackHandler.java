package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Objects;

public class DynamicItemStackHandler extends CustomItemStackHandler {

    private static final NonNullList<ItemStack> EMPTY_LIST = NonNullList.withSize(0, ItemStack.EMPTY);

    private final ArrayList<ItemStack> stacks;

    public DynamicItemStackHandler() {
        super(EMPTY_LIST);
        this.stacks = new ArrayList<>();
    }

    @Override
    public void setSize(int size) {
        // no op
    }

    @Override
    public int getSlots() {
        return stacks.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        return Objects.requireNonNullElse(stacks.get(slot), ItemStack.EMPTY);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        ensureCapacity(slot);
        validateSlotIndex(slot);
        stacks.set(slot, stack);
        onContentsChanged(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;

        ensureCapacity(slot);
        validateSlotIndex(slot);

        ItemStack existing = stacks.get(slot);
        int limit = getStackLimit(slot, stack);

        if (!existing.isEmpty()) {
            if (!ItemHandlerHelper.canItemStacksStack(stack, existing)) return stack;
            limit -= existing.getCount();
        }

        if (limit <= 0) return stack;

        boolean reachedLimit = stack.getCount() > limit;

        if (!simulate) {
            if (existing.isEmpty()) {
                stacks.set(slot, reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, limit) : stack);
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            onContentsChanged(slot);
        }

        return reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) return ItemStack.EMPTY;

        validateSlotIndex(slot);

        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int toExtract = Math.min(amount, existing.getMaxStackSize());

        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                stacks.set(slot, ItemStack.EMPTY);
                onContentsChanged(slot);
                return existing;
            }
            return existing.copy();
        }

        if (!simulate) {
            stacks.set(slot, ItemHandlerHelper.copyStackWithSize(existing, existing.getCount() - toExtract));
            onContentsChanged(slot);
        }

        return ItemHandlerHelper.copyStackWithSize(existing, toExtract);
    }

    @Override
    public void clear() {
        stacks.clear();
        onContentsChanged.run();
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= stacks.size()) {
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + stacks.size() + ")");
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        var nbt = new CompoundTag();
        var items = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            var stack = stacks.get(i);
            if (!stack.isEmpty()) {
                var itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stack.save(itemTag);
                items.add(itemTag);
            }
        }
        nbt.put("Items", items);
        nbt.putInt("Size", stacks.size());
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        int size = nbt.getInt("Size");
        stacks.clear();
        ensureCapacity(size);

        var items = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            var itemTag = items.getCompound(i);
            int slot = itemTag.getInt("Slot");
            if (slot >= 0 && slot < size) {
                stacks.set(slot, ItemStack.of(itemTag));
            }
        }
        onLoad();
    }

    protected void ensureCapacity(int slot) {
        while (stacks.size() <= slot) {
            stacks.add(ItemStack.EMPTY);
        }
    }
}
