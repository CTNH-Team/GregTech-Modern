package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.DropSaved;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class DrumMachine extends MetaMachine implements IDropSaveMachine, IInteractedMachine {

    @Getter
    protected final AutoOutputTrait autoOutputTrait;
    @Getter
    private final int maxStoredFluids;
    @Persisted
    protected final NotifiableFluidTank cache;
    @Nullable
    protected ISubscription exportFluidSubs;
    @Persisted(key = "Fluid")
    @DescSynced
    @Getter
    @DropSaved // rename "Fluid" for Item capability
    protected FluidStack stored = FluidStack.EMPTY;
    @Getter
    protected final Material material;

    public DrumMachine(IMachineBlockEntity holder, Material material, int maxStoredFluids, Object... args) {
        super(holder);
        this.material = material;
        this.maxStoredFluids = maxStoredFluids;
        this.cache = createCacheFluidHandler(args);
        this.autoOutputTrait = new AutoOutputTrait(this, java.util.List.of(), java.util.List.of(cache), false)
                .setFluidOutputValidator(side -> side == Direction.DOWN);
        this.autoOutputTrait.setOutputFacingFluids(Direction.DOWN);
        attachPersistentTrait("auto_output", autoOutputTrait);
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////
    protected NotifiableFluidTank createCacheFluidHandler(Object... args) {
        return new NotifiableFluidTank(this, 1, maxStoredFluids, IO.BOTH)
                .setFilter(material.getProperty(PropertyKey.FLUID_PIPE));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateStoredFluidFromCache();
        this.exportFluidSubs = cache.addChangedListener(this::onFluidChanged);
    }

    private void onFluidChanged() {
        if (!isRemote()) {
            updateStoredFluidFromCache();
        }
    }

    private void updateStoredFluidFromCache() {
        FluidStack cachedFluid = cache.getFluidInTank(0);
        this.stored = cachedFluid.isEmpty() ? FluidStack.EMPTY : cachedFluid;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (exportFluidSubs != null) {
            exportFluidSubs.unsubscribe();
            exportFluidSubs = null;
        }
    }

    //////////////////////////////////////
    // ****** Fluid Logic *******//
    //////////////////////////////////////

    @Override
    public void loadFromItem(CompoundTag tag) {
        IDropSaveMachine.super.loadFromItem(tag);
        if (!tag.contains("Fluid")) {
            stored = FluidStack.EMPTY;
        }
        // "stored" may not be same as cache (due to item's fluid cap). we should update it.
        cache.getStorages()[0].setFluid(stored.copy());
    }

    @Override
    public boolean savePickClone() {
        return false;
    }

    private static boolean canInputFluidsFromOutputSide() {
        return ConfigHolder.INSTANCE.machines.allowDrumsInputFluidsFromOutputSide;
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        if (!isRemote()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, cache)) {
                return InteractionResult.SUCCESS;
            }
        }
        return world.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean saveBreak() {
        return !stored.isEmpty();
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (!isRemote()) {
            if (canInputFluidsFromOutputSide()) {
                autoOutputTrait.setAllowInputFromOutputSideFluids(
                        !autoOutputTrait.isAllowInputFromOutputSideFluids());
                playerIn.sendSystemMessage(
                        Component
                                .translatable("gtceu.machine.basic.input_from_output_side." +
                                        (autoOutputTrait.isAllowInputFromOutputSideFluids() ? "allow" : "disallow"))
                                .append(Component.translatable("gtceu.creative.tank.fluid")));
            } else if (!playerIn.isShiftKeyDown()) {
                autoOutputTrait.setAutoOutputFluids(!autoOutputTrait.isAutoOutputFluids());
                playerIn.sendSystemMessage(Component
                        .translatable("gtceu.machine.drum." +
                                (autoOutputTrait.isAutoOutputFluids() ? "enable" : "disable") + "_output"));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        return super.onScrewdriverClick(playerIn, hand, gridSide, hitResult);
    }

    @Override
    protected InteractionResult onSoftMalletClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                  BlockHitResult hitResult) {
        if (!isRemote()) {
            if (!playerIn.isShiftKeyDown()) {
                autoOutputTrait.setAutoOutputFluids(!autoOutputTrait.isAutoOutputFluids());
                playerIn.sendSystemMessage(
                        Component.translatable(
                                "gtceu.machine.drum." +
                                        (autoOutputTrait.isAutoOutputFluids() ? "enable" : "disable") + "_output"));
                return InteractionResult.SUCCESS;
            }
        }
        return super.onSoftMalletClick(playerIn, hand, gridSide, hitResult);
    }

    //////////////////////////////////////
    // ******* Rendering ********//
    //////////////////////////////////////

    @Override
    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack held,
                                    Set<GTToolType> toolTypes) {
        return super.shouldRenderGrid(player, pos, state, held, toolTypes) ||
                toolTypes.contains(GTToolType.SOFT_MALLET) || toolTypes.contains(GTToolType.SCREWDRIVER);
    }

    @Override
    public @Nullable ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes,
                                              Direction side) {
        if (toolTypes.contains(GTToolType.SOFT_MALLET) ||
                (!canInputFluidsFromOutputSide() && toolTypes.contains(GTToolType.SCREWDRIVER))) {
            if (side == autoOutputTrait.getOutputFacingFluids()) {
                return autoOutputTrait.isAutoOutputFluids() ? GuiTextures.TOOL_DISABLE_AUTO_OUTPUT :
                        GuiTextures.TOOL_AUTO_OUTPUT;
            }
        }
        if (canInputFluidsFromOutputSide() && toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (side == autoOutputTrait.getOutputFacingFluids()) {
                return GuiTextures.TOOL_ALLOW_INPUT;
            }
        }

        return super.sideTips(player, pos, state, toolTypes, side);
    }
}
