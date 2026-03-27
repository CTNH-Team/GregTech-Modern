package com.gregtechceu.gtceu.integration.ae2.machine.trait;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.integration.ae2.utils.AEKeyStorage;
import com.gregtechceu.gtceu.utils.GTMath;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class KeyStorageBackedHandler extends NotifiableItemStackHandler {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            KeyStorageBackedHandler.class,
            NotifiableItemStackHandler.MANAGED_FIELD_HOLDER
    );

    protected final AEKeyStorage keyStorage;

    public KeyStorageBackedHandler(MetaMachine machine, AEKeyStorage keyStorage) {
        super(machine, 0, IO.OUT, IO.NONE);
        this.keyStorage = keyStorage;
        keyStorage.setOnContentsChanged(this::onContentsChanged);
    }

    public static @Nullable List<Ingredient> handleRecipe(
            IO io,
            GTRecipe recipe,
            List<Ingredient> left,
            boolean simulate,
            IO handlerIO,
            AEKeyStorage storage
    ) {
        // Only handle the intended IO type
        if (io != handlerIO || (io != IO.IN && io != IO.OUT)) {
            return left.isEmpty() ? null : left;
        }

        // Temporarily suppress keyStorage listener to batch notifications
        Runnable originalListener = storage.getOnContentsChanged();
        MutableBoolean changed = new MutableBoolean(false);
        storage.setOnContentsChanged(changed::setTrue);

        ListIterator<Ingredient> it = left.listIterator();
        while (it.hasNext()) {
            Ingredient ingredient = it.next();

            // Remove empty ingredients
            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }

            ItemStack output;
            int amount;
            // Handle IntProviderIngredient separately
            if (ingredient instanceof IntProviderIngredient provider) {
                provider.setItemStacks(null);
                provider.setSampledCount(-1);
                output = simulate ? provider.getMaxSizeStack() : getFirstStack(provider.getItems());
                amount = output.getCount();
            } else {
                output = getFirstStack(ingredient.getItems());
                // Preserve the ingredient amount if it's sized
                if (ingredient instanceof SizedIngredient si) {
                    amount = si.getAmount();
                } else {
                    amount = output.getCount();
                }
            }

            // Insert into keyStorage and compute remaining amount
            AEItemKey key = AEItemKey.of(output);
            if (key == null) continue;

            int inserted = Math.toIntExact(storage.add(key, amount, simulate));
            int remaining = amount - inserted;

            if (remaining <= 0) {
                it.remove();
                continue;
            }

            // Update ingredient with remaining amount
            if (ingredient instanceof SizedIngredient si) {
                si.setAmount(remaining);
            } else {
                output.setCount(remaining);
            }
        }

        // Restore listener and trigger if changes occurred and not simulating
        storage.setOnContentsChanged(originalListener);
        if (changed.booleanValue() && !simulate) {
            originalListener.run();
        }

        return left.isEmpty() ? null : left;
    }

    private static ItemStack getFirstStack(ItemStack[] items) {
        return (items.length == 0) ? ItemStack.EMPTY : items[0];
    }

    @Override
    public @Nullable List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate) {
        return handleRecipe(io, recipe, left, simulate, IO.OUT, keyStorage);
    }

    @Override
    public List<Object> getContents() {
        List<Object> result = new ArrayList<>(keyStorage.size());
        for (var entry : keyStorage) {
            AEItemKey key = (AEItemKey) entry.getKey();
            long amount = entry.getLongValue();
            result.addAll(GTMath.splitStacks(key.toStack(), amount));
        }
        return result;
    }

    @Override
    public double getTotalContentAmount() {
        return keyStorage.stream().mapToLong(Object2LongMap.Entry::getLongValue).sum();
    }

    @Override
    public int getSize() {
        return keyStorage.size();
    }

    @Override
    public boolean isEmpty() {
        return keyStorage.isEmpty();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
