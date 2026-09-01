package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Handles the persistent state and world effects of a machine exhaust vent. */
public class ExhaustVentTrait extends MachineTrait {

    private final Supplier<Direction> directionSupplier;
    private final DoubleSupplier damageSupplier;
    private final Runnable ventStateChanged;
    @Persisted
    private boolean needsVenting;

    public ExhaustVentTrait(MetaMachine machine, Supplier<Direction> directionSupplier, DoubleSupplier damageSupplier,
                            Runnable ventStateChanged) {
        super(machine);
        this.directionSupplier = directionSupplier;
        this.damageSupplier = damageSupplier;
        this.ventStateChanged = ventStateChanged;
    }

    public Direction getVentingDirection() {
        return directionSupplier.get();
    }

    public boolean isNeedsVenting() {
        return needsVenting;
    }

    public void afterWorking() {
        needsVenting = true;
        checkVenting();
    }

    public boolean checkVenting() {
        if (!needsVenting) return true;
        if (!isVentingBlocked()) {
            performVenting();
            return true;
        }

        var ventingPos = getMachine().getPos().relative(getVentingDirection());
        if (GTUtil.tryBreakSnow(getMachine().getLevel(), ventingPos,
                getMachine().getLevel().getBlockState(ventingPos), false)) {
            performVenting();
            return true;
        }
        return false;
    }

    public boolean isVentingBlocked() {
        var machine = getMachine();
        BlockPos ventingPos = machine.getPos().relative(getVentingDirection());
        BlockState state = machine.getLevel().getBlockState(ventingPos);
        return state.canOcclude() || Shapes.blockOccudes(state.getCollisionShape(machine.getLevel(), ventingPos),
                Shapes.block(), getVentingDirection().getOpposite());
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        if (getMachine().getPos().relative(getVentingDirection()).equals(fromPos)) {
            ventStateChanged.run();
        }
    }

    private void performVenting() {
        var machine = getMachine();
        var level = machine.getLevel();
        var direction = getVentingDirection();
        var pos = machine.getPos();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.relative(direction)),
                entity -> !(entity instanceof Player player) || !player.isSpectator() && !player.isCreative())) {
            entity.hurt(GTDamageTypes.HEAT.source(level), (float) damageSupplier.getAsDouble());
        }

        double x = pos.getX() + 0.5 + direction.getStepX() * 0.6;
        double y = pos.getY() + 0.5 + direction.getStepY() * 0.6;
        double z = pos.getZ() + 0.5 + direction.getStepZ() * 0.6;
        int count = 7 + level.random.nextInt(3);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, count, direction.getStepX() / 2.0,
                    direction.getStepY() / 2.0, direction.getStepZ() / 2.0, 0.1);
        } else {
            for (int i = 0; i < count; i++) {
                try {
                    level.addParticle(ParticleTypes.CLOUD,
                            x + level.random.nextGaussian() * direction.getStepX() / 2.0,
                            y + level.random.nextGaussian() * direction.getStepY() / 2.0,
                            z + level.random.nextGaussian() * direction.getStepZ() / 2.0,
                            level.random.nextGaussian() * 0.1, level.random.nextGaussian() * 0.1,
                            level.random.nextGaussian() * 0.1);
                } catch (Throwable throwable) {
                    GTCEu.LOGGER.warn("Could not spawn particle effect {}", ParticleTypes.CLOUD);
                    break;
                }
            }
        }
        if (ConfigHolder.INSTANCE.machines.machineSounds) {
            level.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1F, 1F);
        }
        needsVenting = false;
    }
}
