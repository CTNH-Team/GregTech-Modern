package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.utils.energy.IRouteSegmentData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;

public class EnergyRoutePath implements IRoutePath<IEnergyContainer>, IRouteSegmentData {

    private final CableBlockEntity targetPipe;
    @Getter
    private final BlockPos targetPipePos;
    @Getter
    private final Direction targetFacing;
    @Getter
    private final int distance;
    @Getter
    private final CableBlockEntity[] path;
    @Getter
    private final long maxLoss;
    @Unique
    private long[] gtceuHotfix$posLong;
    @Unique
    private long[] gtceuHotfix$maxVoltage;
    @Unique
    private int[] gtceuHotfix$lossPerBlock;

    public EnergyRoutePath(BlockPos targetPipePos, Direction targetFacing, CableBlockEntity[] path, int distance,
                           long maxLoss) {
        this.targetPipe = path[path.length - 1];
        this.targetPipePos = targetPipePos;
        this.targetFacing = targetFacing;
        this.path = path;
        this.distance = distance;
        this.maxLoss = maxLoss;
        final int n = (path == null) ? 0 : path.length;
        final long[] posLong = new long[n];
        final long[] maxV = new long[n];
        final int[] loss = new int[n];

        for (int i = 0; i < n; i++) {
            final CableBlockEntity cable = path[i];
            if (cable == null) continue;

            posLong[i] = cable.getBlockPos().asLong();

            // WireProperties holds both voltage rating and loss per block.
            final WireProperties props = (WireProperties) cable.getNodeData();
            if (props != null) {
                maxV[i] = props.getVoltage();
                loss[i] = props.getLossPerBlock();
            }
        }

        ((IRouteSegmentData) this).gtceuHotfix$setSegmentData(posLong, maxV, loss);
    }

    public void gtceuHotfix$setSegmentData(long[] posLong, long[] maxVoltage, int[] lossPerBlock) {
        this.gtceuHotfix$posLong = posLong;
        this.gtceuHotfix$maxVoltage = maxVoltage;
        this.gtceuHotfix$lossPerBlock = lossPerBlock;
    }

    @Override
    public long[] gtceuHotfix$getPosLong() {
        return gtceuHotfix$posLong;
    }

    @Override
    public long[] gtceuHotfix$getMaxVoltage() {
        return gtceuHotfix$maxVoltage;
    }

    @Override
    public int[] gtceuHotfix$getLossPerBlock() {
        return gtceuHotfix$lossPerBlock;
    }

    @Nullable
    public IEnergyContainer getHandler(Level world) {
        return GTCapabilityHelper.getEnergyContainer(world, getTargetPipePos().relative(targetFacing),
                targetFacing.getOpposite());
    }
}
