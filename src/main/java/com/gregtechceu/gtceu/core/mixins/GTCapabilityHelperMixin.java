package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.utils.energy.FeEnergyContainerWrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends GTCEu's energy container lookup to treat Forge Energy neighbors as endpoints.
 * If GTCEu's native IEnergyContainer is absent, we wrap ForgeCapabilities.ENERGY (IEnergyStorage)
 * into an IEnergyContainer so the existing EnergyNet delivery code can interact with FE sinks.
 */
@Mixin(value = GTCapabilityHelper.class, remap = false)
public class GTCapabilityHelperMixin {

    @Inject(
            method = "getEnergyContainer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lcom/gregtechceu/gtceu/api/capability/IEnergyContainer;",
            at = @At("RETURN"),
            cancellable = true)
    private static void gtceuEutofe_wrapForgeEnergy(Level level, BlockPos pos, Direction side,
                                                    CallbackInfoReturnable<IEnergyContainer> cir) {
        if (cir.getReturnValue() != null) return;
        if (level == null || pos == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        IEnergyStorage fe = be.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);

        // Optional unsided fallback for mods that expose FE unsided (kept conservative elsewhere via output gating).
        if (fe == null) {
            fe = be.getCapability(ForgeCapabilities.ENERGY, null).orElse(null);
        }

        if (fe == null || !fe.canReceive()) return;

        cir.setReturnValue(new FeEnergyContainerWrapper(fe));
    }
}
