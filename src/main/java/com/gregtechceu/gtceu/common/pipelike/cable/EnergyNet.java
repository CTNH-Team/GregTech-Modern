package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.Node;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.utils.energy.EndpointChangeTracker;
import com.gregtechceu.gtceu.utils.energy.HandlerCache;
import com.gregtechceu.gtceu.utils.energy.SinkCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EnergyNet extends PipeNet<WireProperties> {

    private final Map<BlockPos, List<EnergyRoutePath>> NET_DATA = new HashMap<>();

    private long lastEnergyFluxPerSec;
    private long energyFluxPerSec;
    private long lastTime;
    private boolean gtceuHotfixDirty = false;
    private long gtceuHotfixLastGlobalClearTick = Long.MIN_VALUE;

    protected EnergyNet(LevelPipeNet<WireProperties, ? extends EnergyNet> world) {
        super(world);
    }

    public List<EnergyRoutePath> getNetData(BlockPos pipePos) {
        EnergyNet self = (EnergyNet) (Object) this;
        Level level = self.getLevel();
        if (level != null) {
            long tick = level.getGameTime();
            if (gtceuHotfixDirty && gtceuHotfixLastGlobalClearTick != tick) {
                // One global invalidation per tick per net, then rebuild lazily per pipePos.
                NET_DATA.clear();
                HandlerCache.clear(self);
                SinkCache.clear(self);
                gtceuHotfixLastGlobalClearTick = tick;
                gtceuHotfixDirty = false;
            }
        }
        return getEnergyRoutePaths(pipePos);
    }

    @NotNull
    private List<EnergyRoutePath> getEnergyRoutePaths(BlockPos pipePos) {
        List<EnergyRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = EnergyNetWalker.createNetData(this, pipePos);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(EnergyRoutePath::getDistance));
            NET_DATA.put(pipePos, data);
        }
        return data;
    }

    private boolean gtceuHotfixIsNearCable(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (level.getBlockEntity(pos) instanceof CableBlockEntity) return true;
        for (Direction d : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(d));
            if (be instanceof CableBlockEntity) return true;
        }
        return false;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        if (fromPos == null) return;

        EnergyNet self = (EnergyNet) (Object) this;
        Level level = self.getLevel();
        if (level == null) return;

        // (A) Near-cable filter: ignore unrelated updates
        if (!gtceuHotfixIsNearCable(level, fromPos)) {
            return;
        }

        // Always perform local invalidation around the update position (cheap).
        NET_DATA.remove(fromPos);
        for (Direction dir : Direction.values()) {
            NET_DATA.remove(fromPos.relative(dir));
        }

        // Invalidate cached endpoint handlers around this update position.
        HandlerCache.invalidateAround(self, fromPos);

        // Mark dirty ONLY when the BlockEntity identity at fromPos actually changes.
        // This captures real endpoint add/remove/replace without reacting to noisy neighbor updates.
        BlockEntity be = level.getBlockEntity(fromPos);
        if (EndpointChangeTracker.didBlockEntityChange(self, fromPos, be)) {
            gtceuHotfixDirty = true;
        }

        // Also mark dirty when a cable block entity is the source of the update.
        // Connection toggles (e.g. wire cutters) mutate topology without changing BE identity,
        // but routes/sinks must be rebuilt so newly attached endpoints (including FE sinks)
        // are discovered.
        if (be instanceof CableBlockEntity) {
            gtceuHotfixDirty = true;
        }
    }

    @Override
    public void onPipeConnectionsUpdate() {
        NET_DATA.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<WireProperties>> transferredNodes,
                                    PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((EnergyNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(WireProperties nodeData, CompoundTag tagCompound) {
        tagCompound.putLong("voltage", nodeData.getVoltage());
        tagCompound.putInt("amperage", nodeData.getAmperage());
        tagCompound.putInt("loss", nodeData.getLossPerBlock());
    }

    @Override
    protected WireProperties readNodeData(CompoundTag tagCompound) {
        long voltage = tagCompound.getLong("voltage");
        int amperage = tagCompound.getInt("amperage");
        int lossPerBlock = tagCompound.getInt("loss");
        return new WireProperties(voltage, amperage, lossPerBlock);
    }

    //////////////////////////////////////
    // ******* Pipe Status *******//
    //////////////////////////////////////

    public long getEnergyFluxPerSec() {
        Level world = getLevel();
        if (world != null && !world.isClientSide && (world.getGameTime() - lastTime) >= 20) {
            lastTime = world.getGameTime();
            clearCache();
        }
        return lastEnergyFluxPerSec;
    }

    public void addEnergyFluxPerSec(long energy) {
        energyFluxPerSec += energy;
    }

    public void clearCache() {
        lastEnergyFluxPerSec = energyFluxPerSec;
        energyFluxPerSec = 0;
    }
}
