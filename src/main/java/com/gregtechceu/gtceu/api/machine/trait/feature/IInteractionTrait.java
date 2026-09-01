package com.gregtechceu.gtceu.api.machine.trait.feature;

import com.gregtechceu.gtceu.api.item.tool.GTToolType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** A machine trait that contributes player interaction behavior. */
public interface IInteractionTrait {

    default Pair<@Nullable GTToolType, InteractionResult> onToolClick(Set<GTToolType> toolTypes, ItemStack itemStack,
                                                                      UseOnContext context, Direction gridSide) {
        return Pair.of(null, InteractionResult.PASS);
    }

    default InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                    BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    default boolean onLeftClick(Player player, Level level, InteractionHand hand, BlockPos pos,
                                @Nullable Direction face) {
        return false;
    }
}
