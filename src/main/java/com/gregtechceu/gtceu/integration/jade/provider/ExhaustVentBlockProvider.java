package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.trait.ExhaustVentTrait;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class ExhaustVentBlockProvider extends BlockInfoProvider<ExhaustVentTrait> {

    public ExhaustVentBlockProvider() {
        super(GTCEu.id("exhaust_vent_info"));
    }

    @Nullable
    @Override
    protected ExhaustVentTrait getCapability(Level level, BlockPos blockPos) {
        MetaMachine machine = MetaMachine.getMachine(level, blockPos);
        return machine == null ? null : machine.getTrait(ExhaustVentTrait.class);
    }

    @Override
    protected boolean allowDisplaying(ExhaustVentTrait capability) {
        return super.allowDisplaying(capability);
    }

    @Override
    protected void write(CompoundTag compoundTag, ExhaustVentTrait exhaustVent,
                         BlockAccessor blockAccessor) {
        var direction = exhaustVent.getVentingDirection();
        compoundTag.putString("ventDirection", direction.getName());
        var level = blockAccessor.getLevel();
        var pos = blockAccessor.getPosition().relative(direction);
        if (level != null) {
            var key = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            compoundTag.putString("ventBlock", key.toString());
        }
        compoundTag.putBoolean("ventBlocked", exhaustVent.isVentingBlocked());
        compoundTag.putBoolean("needsVenting", exhaustVent.isNeedsVenting());
    }

    @Override
    protected void addTooltip(CompoundTag compoundTag, ITooltip iTooltip, Player player, BlockAccessor blockAccessor,
                              BlockEntity blockEntity, IPluginConfig iPluginConfig) {
        var direction = Direction.byName(compoundTag.getString("ventDirection"));
        if (direction != null) {
            iTooltip.add(Component.translatable("gtceu.top.exhaust_vent_direction",
                    StringUtils.capitalize(direction.getName())));
            if (!compoundTag.getBoolean("ventBlocked")) return;

            if (blockAccessor.showDetails()) {
                var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(compoundTag.getString("ventBlock")))
                        .asItem().getDefaultInstance();
                iTooltip.append(iTooltip.getElementHelper().smallItem(block));
            }

            if (compoundTag.getBoolean("needsVenting")) {
                iTooltip.append(Component.literal(" ("));
                iTooltip.append(Component.translatable("gtceu.top.exhaust_vent_blocked").withStyle(ChatFormatting.RED)
                        .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));
            }
        }
    }
}
