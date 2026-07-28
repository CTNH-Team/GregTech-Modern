package com.gregtechceu.gtceu.utils.energy;

import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Accumulates per-cable amperage updates during EnergyNet delivery and applies them once per server tick.
 *
 * Performance notes:
 * - Uses primitive fastutil maps to avoid allocation-heavy java.util.HashMap hot paths.
 * - Packs (sumAmps,maxVoltage) into a single long to keep one map lookup per cable.
 */
@Mod.EventBusSubscriber(modid = "gtceuenergynethotfix", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CableAmperageAccumulator {

    private CableAmperageAccumulator() {}

    /**
     * Per-level accumulator: cablePosLong -> packed(sumAmps, maxVoltage).
     *
     * packed = (sumAmps << 32) | (maxVoltage & 0xFFFFFFFF)
     */
    private static final Object2ObjectOpenHashMap<ServerLevel, Long2LongOpenHashMap> PER_LEVEL = new Object2ObjectOpenHashMap<>();

    /**
     * Record flow through a cable segment for this tick.
     */
    public static void record(ServerLevel level, long cablePosLong, long amperage, long voltage) {
        if (amperage <= 0) return;

        // Lazily create and reuse the per-level map.
        Long2LongOpenHashMap map = PER_LEVEL.get(level);
        if (map == null) {
            map = new Long2LongOpenHashMap(4096);
            map.defaultReturnValue(0L);
            PER_LEVEL.put(level, map);
        }

        final long key = cablePosLong;
        final long packed = map.get(key);

        // Unpack current values.
        int sumAmps = (int) (packed >>> 32);
        int maxV = (int) packed;

        // Update.
        // Amperage is small in GTCEu (typically <= 64), but keep it as int to avoid long math here.
        sumAmps += (int) amperage;

        final int v = (int) voltage;
        if (v > maxV) maxV = v;

        map.put(key, (((long) sumAmps) << 32) | (maxV & 0xFFFFFFFFL));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (PER_LEVEL.isEmpty()) return;

        // Apply and clear each level map in-place (reuse allocations).
        for (var entry : PER_LEVEL.object2ObjectEntrySet()) {
            final ServerLevel level = entry.getKey();
            final Long2LongOpenHashMap map = entry.getValue();
            if (map == null || map.isEmpty()) continue;

            final var it = map.long2LongEntrySet().fastIterator();
            while (it.hasNext()) {
                final var e = it.next();
                final long posLong = e.getLongKey();
                final long packed = e.getLongValue();
                final int sumAmps = (int) (packed >>> 32);
                final int maxV = (int) packed;

                if (sumAmps <= 0) continue;

                final var be = level.getBlockEntity(net.minecraft.core.BlockPos.of(posLong));
                if (!(be instanceof CableBlockEntity cable)) continue;

                // GTCEu 7.4.0:
                // boolean incrementAmperage(long amperage, long voltage)
                // NOTE: CableBlockEntity.incrementAmperage() already applies over-amp heat internally
                // when the cable exceeds its max amperage for the tick. Calling applyHeat() again here
                // would double-apply heat and make cables burn incorrectly.
                cable.incrementAmperage((long) sumAmps, (long) maxV);
            }
            map.clear();
        }
    }
}
