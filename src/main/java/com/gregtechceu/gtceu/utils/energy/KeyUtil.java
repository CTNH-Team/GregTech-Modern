package com.gregtechceu.gtceu.utils.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class KeyUtil {

    private KeyUtil() {}

    /** Pack a BlockPos into the upper bits used by {@link #packPosSide(BlockPos, Direction)}. */
    public static long packPos(BlockPos pos) {
        return (pos.asLong() << 3);
    }

    /** Pack a BlockPos + Direction into a single long key. */
    public static long packPosSide(BlockPos pos, Direction side) {
        // BlockPos.asLong uses 26/12/26 bits; shift left by 3 to store side (0-5).
        return (pos.asLong() << 3) | (side.ordinal() & 7L);
    }

    /** Mix two longs into one reasonably well-distributed long key. */
    public static long mix(long a, long b) {
        long x = a ^ (b + 0x9E3779B97F4A7C15L + (a << 6) + (a >> 2));
        // final avalanching
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }
}
