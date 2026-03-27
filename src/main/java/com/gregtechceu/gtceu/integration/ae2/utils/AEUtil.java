package com.gregtechceu.gtceu.integration.ae2.utils;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.utils.GTMath;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

@UtilityClass
public class AEUtil {

    /**
     * Transfers AEKeyStorage entries to a MEStorage.
     *
     * @param from   The {@link AEKeyStorage} to transfer from.
     * @param to     The {@link MEStorage} to transfer to.
     * @param source The action source for the transfer.
     * @param notify Whether to notify the {@link AEKeyStorage} of changes.
     */
    public void transferTo(AEKeyStorage from, MEStorage to, IActionSource source, boolean notify) {
        if (from.isEmpty()) return;

        boolean changed = false;

        for (var it = from.iterator(); it.hasNext(); ) {
            var entry = it.next();
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            long inserted = to.insert(key, amount, Actionable.MODULATE, source);
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

        if (changed && notify) {
            from.getOnContentsChanged().run();
        }
    }

    public void dropAllItems(Level level, BlockPos pos, AEKeyStorage storage) {
        for (var iterator = storage.iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var key = entry.getKey();

            if (!(key instanceof AEItemKey itemKey)) {
                continue;
            }

            long count = entry.getLongValue();
            var stacks = GTMath.splitStacks(itemKey.toStack(), count);

            for (var stack : stacks) {
                Block.popResource(level, pos, stack);
            }

            iterator.remove();
        }
    }
}
