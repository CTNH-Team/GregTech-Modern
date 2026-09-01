package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.feature.IAttachConfiguratorsTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IFrontFacingTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IInteractionTrait;
import com.gregtechceu.gtceu.api.machine.trait.feature.IRenderingTrait;
import com.gregtechceu.gtceu.common.item.tool.behavior.ToolModeSwitchBehavior;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared auto-output behavior for machines exposing item and/or fluid output handlers. */
public class AutoOutputTrait extends MachineTrait
                             implements IAttachConfiguratorsTrait, IFrontFacingTrait, IInteractionTrait,
                             IRenderingTrait {

    private final List<IItemHandler> itemHandlers;
    private final List<IFluidHandler> fluidHandlers;
    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    private boolean autoOutputItems;
    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    private boolean autoOutputFluids;
    @Setter
    @Getter
    @Persisted
    private boolean allowInputFromOutputSideItems;
    @Setter
    @Getter
    @Persisted
    private boolean allowInputFromOutputSideFluids;
    @Getter
    @Setter
    private int ticksPerCycle = 5;
    @Persisted
    @DescSynced
    @RequireRerender
    private @Nullable Direction outputFacingItems, outputFacingFluids;
    private @Nullable TickableSubscription itemOutputSubscription, fluidOutputSubscription;
    private final List<ISubscription> itemSubscriptions = new ArrayList<>();
    private final List<ISubscription> fluidSubscriptions = new ArrayList<>();
    private Predicate<@Nullable Direction> itemOutputValidator = side -> true;
    private Predicate<@Nullable Direction> fluidOutputValidator = side -> true;
    @Getter
    private final boolean useDefaultToolHandlers;

    public AutoOutputTrait(MetaMachine machine, List<IItemHandler> itemHandlers, List<IFluidHandler> fluidHandlers) {
        this(machine, itemHandlers, fluidHandlers, true);
    }

    public AutoOutputTrait(MetaMachine machine, List<IItemHandler> itemHandlers, List<IFluidHandler> fluidHandlers,
                           boolean useDefaultToolHandlers) {
        super(machine);
        this.itemHandlers = itemHandlers.stream().filter(handler -> handler.getSlots() > 0).toList();
        this.fluidHandlers = fluidHandlers.stream().filter(handler -> handler.getTanks() > 0).toList();
        this.useDefaultToolHandlers = useDefaultToolHandlers;
        this.outputFacingItems = machine.hasFrontFacing() ? machine.getFrontFacing().getOpposite() : Direction.UP;
        this.outputFacingFluids = outputFacingItems;
    }

    public AutoOutputTrait(MetaMachine machine, IItemHandler itemHandler, IFluidHandler fluidHandler) {
        this(machine, List.of(itemHandler), List.of(fluidHandler));
    }

    public static AutoOutputTrait ofItems(MetaMachine machine, IItemHandler... handlers) {
        return new AutoOutputTrait(machine, Arrays.asList(handlers), List.of());
    }

    public static AutoOutputTrait ofFluids(MetaMachine machine, IFluidHandler... handlers) {
        return new AutoOutputTrait(machine, List.of(), Arrays.asList(handlers));
    }

    public AutoOutputTrait setItemOutputValidator(Predicate<@Nullable Direction> validator) {
        this.itemOutputValidator = validator;
        return this;
    }

    public AutoOutputTrait setFluidOutputValidator(Predicate<@Nullable Direction> validator) {
        this.fluidOutputValidator = validator;
        return this;
    }

    @Override
    public void onMachineLoad() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateItemOutputSubscription));
            serverLevel.getServer().tell(new TickTask(0, this::updateFluidOutputSubscription));
            for (var handler : itemHandlers) if (handler instanceof NotifiableItemStackHandler notifiable)
                itemSubscriptions.add(notifiable.addChangedListener(this::updateItemOutputSubscription));
            for (var handler : fluidHandlers) if (handler instanceof NotifiableFluidTank notifiable)
                fluidSubscriptions.add(notifiable.addChangedListener(this::updateFluidOutputSubscription));
        }
    }

    @Override
    public void onMachineUnload() {
        if (itemOutputSubscription != null) itemOutputSubscription.unsubscribe();
        if (fluidOutputSubscription != null) fluidOutputSubscription.unsubscribe();
        itemOutputSubscription = fluidOutputSubscription = null;
        itemSubscriptions.forEach(ISubscription::unsubscribe);
        fluidSubscriptions.forEach(ISubscription::unsubscribe);
        itemSubscriptions.clear();
        fluidSubscriptions.clear();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        updateItemOutputSubscription();
        updateFluidOutputSubscription();
    }

    public boolean hasAutoOutputItem() {
        return !itemHandlers.isEmpty();
    }

    public boolean hasAutoOutputFluid() {
        return !fluidHandlers.isEmpty();
    }

    public @Nullable Direction getOutputFacingItems() {
        return hasAutoOutputItem() ? outputFacingItems : null;
    }

    public @Nullable Direction getOutputFacingFluids() {
        return hasAutoOutputFluid() ? outputFacingFluids : null;
    }

    public void setOutputFacingItems(@Nullable Direction facing) {
        if (!hasAutoOutputItem() || !itemOutputValidator.test(facing) ||
                getMachine().hasFrontFacing() && facing == getMachine().getFrontFacing())
            return;
        outputFacingItems = facing;
        updateItemOutputSubscription();
    }

    public void setOutputFacingFluids(@Nullable Direction facing) {
        if (!hasAutoOutputFluid() || !fluidOutputValidator.test(facing) ||
                getMachine().hasFrontFacing() && facing == getMachine().getFrontFacing())
            return;
        outputFacingFluids = facing;
        updateFluidOutputSubscription();
    }

    public void setAutoOutputItems(boolean value) {
        if (!hasAutoOutputItem()) return;
        autoOutputItems = value;
        updateItemOutputSubscription();
    }

    public void setAutoOutputFluids(boolean value) {
        if (!hasAutoOutputFluid()) return;
        autoOutputFluids = value;
        updateFluidOutputSubscription();
    }

    private void updateItemOutputSubscription() {
        if (getMachine().getLevel() == null || getMachine().getLevel().isClientSide) return;
        boolean itemReady = autoOutputItems && !itemHandlers.isEmpty() && outputFacingItems != null &&
                GTTransferUtils.hasAdjacentItemHandler(getMachine().getLevel(), getMachine().getPos(),
                        outputFacingItems);
        if (itemReady) itemOutputSubscription = getMachine().subscribeServerTick(itemOutputSubscription,
                this::tickItemOutput);
        else if (itemOutputSubscription != null) {
            itemOutputSubscription.unsubscribe();
            itemOutputSubscription = null;
        }
    }

    private void updateFluidOutputSubscription() {
        if (getMachine().getLevel() == null || getMachine().getLevel().isClientSide) return;
        boolean fluidReady = autoOutputFluids && !fluidHandlers.isEmpty() && outputFacingFluids != null &&
                GTTransferUtils.hasAdjacentFluidHandler(getMachine().getLevel(), getMachine().getPos(),
                        outputFacingFluids);
        if (fluidReady) fluidOutputSubscription = getMachine().subscribeServerTick(fluidOutputSubscription,
                this::tickFluidOutput);
        else if (fluidOutputSubscription != null) {
            fluidOutputSubscription.unsubscribe();
            fluidOutputSubscription = null;
        }
    }

    private void tickItemOutput() {
        if (getMachine().getOffsetTimer() % Math.max(1, ticksPerCycle) == 0) {
            if (outputFacingItems != null) exportItems(outputFacingItems);
        }
        updateItemOutputSubscription();
    }

    private void tickFluidOutput() {
        if (getMachine().getOffsetTimer() % Math.max(1, ticksPerCycle) == 0 && outputFacingFluids != null) {
            exportFluids(outputFacingFluids);
        }
        updateFluidOutputSubscription();
    }

    @Override
    public Pair<@Nullable GTToolType, InteractionResult> onToolClick(Set<GTToolType> toolTypes, ItemStack itemStack,
                                                                     UseOnContext context, Direction gridSide) {
        if (!useDefaultToolHandlers) return IInteractionTrait.super.onToolClick(toolTypes, itemStack, context,
                gridSide);
        if (toolTypes.contains(GTToolType.WRENCH)) {
            var tagCompound = com.gregtechceu.gtceu.api.item.tool.ToolHelper.getBehaviorsTag(itemStack);
            var modes = ToolModeSwitchBehavior.WrenchModeType.values();
            var mode = modes[Math.min(Byte.toUnsignedInt(tagCompound.getByte("Mode")), modes.length - 1)];
            boolean changed = false;
            if (!getMachine().hasFrontFacing() || gridSide != getMachine().getFrontFacing()) {
                if (mode.isItem() && hasAutoOutputItem() && itemOutputValidator.test(gridSide)) {
                    setOutputFacingItems(gridSide);
                    changed = true;
                }
                if (mode.isFluid() && hasAutoOutputFluid() && fluidOutputValidator.test(gridSide)) {
                    setOutputFacingFluids(gridSide);
                    changed = true;
                }
            }
            return Pair.of(GTToolType.WRENCH,
                    changed ? InteractionResult.sidedSuccess(getMachine().isRemote()) : InteractionResult.PASS);
        }
        if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            return Pair.of(GTToolType.SCREWDRIVER, onScrewdriverClick(context.getPlayer(), gridSide));
        }
        return IInteractionTrait.super.onToolClick(toolTypes, itemStack, context, gridSide);
    }

    private InteractionResult onScrewdriverClick(Player player, Direction side) {
        boolean changed = false;
        if (side == getOutputFacingItems()) {
            if (player.isShiftKeyDown()) {
                setAllowInputFromOutputSideItems(!isAllowInputFromOutputSideItems());
                player.displayClientMessage(Component.translatable("gtceu.machine.basic.input_from_output_side." +
                        (isAllowInputFromOutputSideItems() ? "allow" : "disallow"))
                        .append(Component.translatable("gtceu.creative.chest.item")), true);
            } else setAutoOutputItems(!isAutoOutputItems());
            changed = true;
        }
        if (side == getOutputFacingFluids()) {
            if (player.isShiftKeyDown()) {
                setAllowInputFromOutputSideFluids(!isAllowInputFromOutputSideFluids());
                player.displayClientMessage(Component.translatable("gtceu.machine.basic.input_from_output_side." +
                        (isAllowInputFromOutputSideFluids() ? "allow" : "disallow"))
                        .append(Component.translatable("gtceu.creative.tank.fluid")), true);
            } else setAutoOutputFluids(!isAutoOutputFluids());
            changed = true;
        }
        return changed ? InteractionResult.sidedSuccess(player.level().isClientSide) : InteractionResult.PASS;
    }

    @Override
    public boolean isValidFrontFace(Direction direction) {
        return direction != getOutputFacingItems() && direction != getOutputFacingFluids();
    }

    @Override
    public boolean shouldRenderGridOverlay(Player player, BlockPos pos, BlockState state, ItemStack held,
                                           Set<GTToolType> toolTypes) {
        return toolTypes.contains(GTToolType.WRENCH) || toolTypes.contains(GTToolType.SCREWDRIVER);
    }

    @Override
    public @Nullable ResourceTexture getGridOverlayIcon(Player player, BlockPos pos, BlockState state,
                                                        Set<GTToolType> toolTypes, Direction side) {
        if (toolTypes.contains(GTToolType.WRENCH) && !player.isShiftKeyDown() &&
                (!getMachine().hasFrontFacing() || side != getMachine().getFrontFacing())) {
            if (hasAutoOutputItem() && itemOutputValidator.test(side) && side != getOutputFacingItems() ||
                    hasAutoOutputFluid() && fluidOutputValidator.test(side) && side != getOutputFacingFluids()) {
                return GuiTextures.TOOL_IO_FACING_ROTATION;
            }
        }
        if (toolTypes.contains(GTToolType.SCREWDRIVER) &&
                (side == getOutputFacingItems() || side == getOutputFacingFluids())) {
            return player.isShiftKeyDown() ? GuiTextures.TOOL_ALLOW_INPUT : GuiTextures.TOOL_AUTO_OUTPUT;
        }
        return null;
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel left, ConfiguratorPanel right) {
        if (hasAutoOutputItem()) {
            left.attachConfigurators(createAutoOutputToggle(
                    GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON,
                    "gtceu.gui.item_auto_output",
                    this::isAutoOutputItems,
                    this::setAutoOutputItems));
        }
        if (hasAutoOutputFluid()) {
            left.attachConfigurators(createAutoOutputToggle(
                    GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON,
                    "gtceu.gui.fluid_auto_output",
                    this::isAutoOutputFluids,
                    this::setAutoOutputFluids));
        }
    }

    private IFancyConfiguratorButton.Toggle createAutoOutputToggle(ResourceTexture modes, String tooltipKey,
                                                                   BooleanSupplier state, Consumer<Boolean> setter) {
        var toggle = new IFancyConfiguratorButton.Toggle(
                new GuiTextureGroup(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5),
                        modes.getSubTexture(0, 1 / 3f, 1, 1 / 3f)),
                new GuiTextureGroup(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5),
                        modes.getSubTexture(0, 2 / 3f, 1, 1 / 3f)),
                state, (clickData, pressed) -> setter.accept(pressed));
        return withStateTooltip(toggle, tooltipKey);
    }

    private IFancyConfiguratorButton.Toggle createAllowInputToggle(ResourceTexture icon, String tooltipKey,
                                                                   BooleanSupplier state, Consumer<Boolean> setter) {
        var toggle = new IFancyConfiguratorButton.Toggle(
                new GuiTextureGroup(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5), icon),
                new GuiTextureGroup(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5), icon),
                state, (clickData, pressed) -> setter.accept(pressed));
        return withStateTooltip(toggle, tooltipKey);
    }

    private IFancyConfiguratorButton.Toggle withStateTooltip(IFancyConfiguratorButton.Toggle toggle,
                                                             String tooltipKey) {
        return toggle.setTooltipsSupplier(pressed -> List.of(
                Component.translatable(tooltipKey + '.' + (pressed ? "enabled" : "disabled"))));
    }

    private void exportItems(Direction facing) {
        GTTransferUtils.getAdjacentItemHandler(getMachine().getLevel(), getMachine().getPos(), facing)
                .ifPresent(destination -> itemHandlers
                        .forEach(source -> GTTransferUtils.transferItemsFiltered(source, destination,
                                getMachine().getItemCapFilter(facing, IO.OUT))));
    }

    private void exportFluids(Direction facing) {
        GTTransferUtils.getAdjacentFluidHandler(getMachine().getLevel(), getMachine().getPos(), facing)
                .ifPresent(destination -> fluidHandlers
                        .forEach(source -> GTTransferUtils.transferFluidsFiltered(source, destination,
                                getMachine().getFluidCapFilter(facing, IO.OUT))));
    }
}
