package com.gregtechceu.gtceu.integration.ae2.machine.trait;

import appeng.api.stacks.AEFluidKey;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.integration.ae2.utils.AEKeyStorage;
import com.gregtechceu.gtceu.utils.GTMath;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class KeyStorageBakedTank extends NotifiableFluidTank {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            KeyStorageBakedTank.class,
            NotifiableFluidTank.MANAGED_FIELD_HOLDER
    );

    protected final AEKeyStorage keyStorage;

    public KeyStorageBakedTank(MetaMachine machine, AEKeyStorage keyStorage) {
        super(machine, 0, 0, IO.OUT, IO.NONE);
        this.keyStorage = keyStorage;
        keyStorage.setOnContentsChanged(this::onContentsChanged);
    }

    public static @Nullable List<FluidIngredient> handleRecipe(
            IO io,
            GTRecipe recipe,
            List<FluidIngredient> left,
            boolean simulate,
            IO handlerIO,
            AEKeyStorage storage
    ) {
        if (io != handlerIO || (io != IO.IN && io != IO.OUT)) {
            return left.isEmpty() ? null : left;
        }

        Runnable originalListener = storage.getOnContentsChanged();
        MutableBoolean changed = new MutableBoolean(false);
        storage.setOnContentsChanged(changed::setTrue);

        ListIterator<FluidIngredient> it = left.listIterator();
        while (it.hasNext()) {
            FluidIngredient ingredient = it.next();

            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }

            FluidStack output;
            int amount;
            if (ingredient instanceof IntProviderFluidIngredient provider) {
                provider.setFluidStacks(null);
                provider.setSampledCount(-1);
                output = simulate ? provider.getMaxSizeStack() : getFirstStack(provider.getStacks());
            } else {
                output = getFirstStack(ingredient.getStacks());
            }

            // Insert into keyStorage and compute remaining amount
            AEFluidKey key = AEFluidKey.of(output);
            if (key == null) continue;
            amount = output.getAmount();

            int inserted = Math.toIntExact(storage.add(key, amount, simulate));
            int remaining = amount - inserted;

            if (remaining <= 0) {
                it.remove();
                continue;
            }

            ingredient.setAmount(remaining);
        }

        storage.setOnContentsChanged(originalListener);
        if (changed.booleanValue() && !simulate) {
            originalListener.run();
        }

        return left.isEmpty() ? null : left;
    }

    private static FluidStack getFirstStack(FluidStack[] stacks) {
        return (stacks.length == 0) ? FluidStack.EMPTY : stacks[0];
    }

    @Override
    public @Nullable List<FluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<FluidIngredient> left,
                                                             boolean simulate) {
        return handleRecipe(io, recipe, left, simulate, IO.OUT, keyStorage);
    }

    @Override
    public List<Object> getContents() {
        List<Object> result = new ArrayList<>(keyStorage.size());
        for (var entry : keyStorage) {
            AEFluidKey key = (AEFluidKey) entry.getKey();
            long amount = entry.getLongValue();
            result.addAll(GTMath.splitFluidStacks(key.toStack(1), amount));
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
