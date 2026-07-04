package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.utils.energy.FeEnergyContainerWrapper;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = com.gregtechceu.gtceu.common.pipelike.cable.EnergyNetWalker.class, remap = false)
public abstract class EnergyNetWorkMixin {

    @Redirect(
              method = "checkNeighbour",
              at = @At(
                       value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private LazyOptional<?> gtceuEutofe_redirectEnergyContainerCapability(BlockEntity be, Capability<?> cap,
                                                                          Direction side) {
        // Preserve original behavior for all other capabilities.
        LazyOptional<?> original = be.getCapability(cap, side);

        // Only intervene when GTCEu is probing for the GT energy container and it is absent.
        if (cap == GTCapability.CAPABILITY_ENERGY_CONTAINER && (original == null || !original.isPresent())) {
            // Sided FE first
            IEnergyStorage storage = be.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);

            // Unsided fallback if sided is absent (some mods expose FE unsided only)
            if (storage == null) {
                storage = be.getCapability(ForgeCapabilities.ENERGY, null).orElse(null);
            }

            if (storage != null) {
                IEnergyContainer wrapped = new FeEnergyContainerWrapper(storage);
                return LazyOptional.of(() -> wrapped);
            }
        }

        return original;
    }
}
