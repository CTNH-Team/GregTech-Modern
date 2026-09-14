package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerGroup;
import com.gregtechceu.gtceu.api.recipe.ingredient.IChancedIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.fluid.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.fluid.RangedFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.gregtechceu.gtceu.integration.jade.GTElementHelper;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidStackList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidTagList;
import com.gregtechceu.gtceu.integration.xei.handlers.fluid.CycleFluidEntryHandler;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.util.FluidTextHelper;

import java.util.*;
import java.util.stream.Collectors;

import static com.gregtechceu.gtceu.client.util.DrawUtil.drawChance;
import static com.gregtechceu.gtceu.client.util.DrawUtil.drawString;

public class FluidRecipeCapability extends RecipeCapability<FluidIngredient> {

    public final static FluidRecipeCapability CAP = new FluidRecipeCapability();

    protected FluidRecipeCapability() {
        super("fluid", 0xFF3C70EE, true, FluidIngredient.CODEC);
    }

    @Override
    public void appendJadeRecipeTooltip(IO io, boolean tick, List<FluidIngredient> contents, RecipeLogic logic,
                                        ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (io != IO.OUT || tick) return;
        var recipe = logic.getLastRecipe();
        if (recipe == null) return;
        var chanceFunction = recipe.getType().getChanceFunction();
        if (!contents.isEmpty()) {
            tooltip.add(Component.translatable("gtceu.top.fluid_auto_output", ""));
        }
        for (var ingredient : contents) {
            RangedFluidIngredient ranged = ingredient instanceof RangedFluidIngredient value ? value :
                    ingredient.isChanced() && ingredient.getInner() instanceof RangedFluidIngredient value ? value :
                            null;
            FluidStack stack = firstStack(ranged != null ? ranged.getInner().getFluids() : ingredient.getFluids());
            if (stack.isEmpty()) continue;
            MutableComponent text = CommonComponents.space();
            if (ranged != null) {
                text.append(Component.translatable("gtceu.gui.content.range",
                        FluidTextHelper.getUnicodeMillibuckets(ranged.getMinAmount(), true),
                        FluidTextHelper.getUnicodeMillibuckets(ranged.getAmount(), true)));
            } else {
                int amount = stack.getAmount();
                if (ingredient.isChanced()) amount = Math.max(1,
                        (int) Math.round((double) amount * recipe.getTotalRuns() *
                                chanceFunction.getBoostedChance(ingredient.getChance(), recipe.tier,
                                        recipe.tier + recipe.ocLevel) /
                                IChancedIngredient.MAX_CHANCE));
                text.append(FluidTextHelper.getUnicodeMillibuckets(amount, true));
            }
            text.append(CommonComponents.space())
                    .append(ComponentUtils.wrapInSquareBrackets(stack.getDisplayName()).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.WHITE);
            tooltip.add(GTElementHelper
                    .smallFluid(JadeFluidObject.of(stack.getFluid(), stack.getAmount(), stack.getTag())));
            tooltip.append(text);
        }
    }

    private static FluidStack firstStack(FluidStack[] stacks) {
        for (var stack : stacks) {
            if (!stack.isEmpty()) return stack.copy();
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidIngredient fromNetwork(FriendlyByteBuf friendlyByteBuf) {
        return FluidIngredient.fromNetwork(friendlyByteBuf);
    }

    @Override
    public void toNetwork(FluidIngredient ingredient, FriendlyByteBuf friendlyByteBuf) {
        ingredient.toNetwork(friendlyByteBuf);
    }

    @Override
    public FluidIngredient copyWithMultiplier(FluidIngredient content, float multiplier) {
        return content.copyWithMultiplier(multiplier);
    }

    @Override
    public boolean isChanced(FluidIngredient content) {
        return content.isChanced();
    }

    @Override
    public IGuiTexture createXEIOverlay(FluidIngredient content, boolean perTick) {
        return new IGuiTexture() {

            @Override
            public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
                drawChance(graphics, x, y, width, height, content.getChance());
                if (content instanceof RangedFluidIngredient ranged) {
                    drawString(graphics, x, y, width, height,
                            "%s-%s".formatted(ranged.getMinAmount(), ranged.getAmount()), 0xFFFFFF, false);
                } else if (content.getAmount() > 0) {
                    drawString(graphics, x, y, width, height, FormattingUtil.formatBuckets(content.getAmount()),
                            0xFFFFFF, false);
                }
                if (perTick) {
                    drawString(graphics, x, y, width, height,
                            LocalizationUtils.format("gtceu.gui.content.tips.per_tick_short"), 0xFFFF00, true);
                }
            }
        };
    }

    @Override
    public boolean isRecipeSearchFilter() {
        return true;
    }

    @Override
    public List<AbstractMapIngredient> getMapIngredients(FluidIngredient content) {
        return content.getMapIngredients();
    }

    @Override
    public int limitMaxParallelByOutput(RecipeHandlerGroup holder, GTRecipe recipe, int multiplier, boolean tick) {
        var outputContents = (tick ? recipe.tickOutputs : recipe.outputs).get(this);
        if (outputContents == null || outputContents.isEmpty()) return multiplier;

        var handlers = holder.getOutputHandlerMap().get(this);
        if (handlers == null || handlers.isEmpty()) return 0;

        int minMultiplier = 0;
        int maxMultiplier = multiplier;

        int maxAmount = 0;
        List<FluidIngredient> ingredients = new ArrayList<>(outputContents.size());
        for (var content : outputContents) {
            maxAmount = Math.max(maxAmount, content.getAmount());
            ingredients.add(content);
        }
        if (maxAmount == 0) return multiplier;
        if (multiplier > Integer.MAX_VALUE / maxAmount) {
            maxMultiplier = multiplier = Integer.MAX_VALUE / maxAmount;
        }

        while (minMultiplier != maxMultiplier) {
            List<FluidIngredient> copied = new ArrayList<>();
            for (final var ing : ingredients) {
                copied.add(copyWithMultiplier(ing, multiplier));
            }

            for (var handler : handlers) {
                if (handler.handleRecipe(IO.OUT, recipe, copied, true)) break;
            }
            int[] bin = ParallelLogic.adjustMultiplier(copied.isEmpty(), minMultiplier, multiplier, maxMultiplier);
            minMultiplier = bin[0];
            multiplier = bin[1];
            maxMultiplier = bin[2];
        }

        return multiplier;
    }

    @Override
    public int getMaxParallelByInput(RecipeHandlerGroup holder, GTRecipe recipe, int limit, boolean tick) {
        var inputs = (tick ? recipe.tickInputs : recipe.inputs).get(this);
        if (inputs == null || inputs.isEmpty()) return limit;

        // Find all the fluids in the combined Fluid Input inventories and create oversized FluidStacks
        Object2LongMap<FluidStack> inventory = getInputContents(holder);
        if (inventory.isEmpty()) return 0;

        var amountMap = new Object2LongOpenCustomHashMap<>(FluidIngredient.IGNORE_AMOUNT);
        for (FluidIngredient ing : inputs) {
            int amount = ing.getAmount();
            if (amount <= 0) continue;
            amountMap.addTo(ing, amount);
        }

        int maxMultiplier = Integer.MAX_VALUE;
        for (var cEntry : Object2LongMaps.fastIterable(amountMap)) {
            FluidIngredient ingredient = cEntry.getKey();
            final long needed = cEntry.getLongValue();
            final long maxNeeded = needed * limit;
            long available = 0;
            // Search stacks in our inventory, summing them up
            for (var stackEntry : Object2LongMaps.fastIterable(inventory)) {
                if (ingredient.test(stackEntry.getKey())) {
                    available += stackEntry.getLongValue();
                    // We can stop if we already have enough for max parallel
                    if (available >= maxNeeded) break;
                }
            }
            // ratio will equal 0 if available < needed
            int ratio = GTMath.saturatedCast(Math.min(limit, available / needed));
            maxMultiplier = Math.min(maxMultiplier, ratio);
            if (ratio == 0) break;
        }

        return maxMultiplier;
    }

    private static Object2LongMap<FluidStack> getInputContents(RecipeHandlerGroup holder) {
        var handlers = holder.getInputHandlerMap().get(FluidRecipeCapability.CAP);

        Object2LongOpenHashMap<FluidStack> inventory = new Object2LongOpenHashMap<>();
        if (handlers == null || handlers.isEmpty()) return inventory;
        for (var handler : handlers) {
            for (var content : handler.getContents()) {
                if (content instanceof FluidStack stack && !stack.isEmpty()) {
                    inventory.addTo(stack, stack.getAmount());
                }
            }
        }
        return inventory;
    }

    @Override
    public @NotNull List<FluidEntryList> createXEIContainerContents(List<FluidIngredient> contents,
                                                                    GTRecipeDefinition recipe, IO io) {
        return contents.stream()
                .map(FluidRecipeCapability::mapFluid)
                .collect(Collectors.toList());
    }

    public Object createXEIContainer(List<?> contents) {
        // cast is safe if you don't pass the wrong thing.
        // noinspection unchecked
        return new CycleFluidEntryHandler((List<FluidEntryList>) contents);
    }

    @NotNull
    @Override
    public Widget createWidget() {
        TankWidget tank = new TankWidget();
        tank.initTemplate();
        tank.setFillDirection(ProgressTexture.FillDirection.ALWAYS_FULL);
        return tank;
    }

    @NotNull
    @Override
    public Class<? extends Widget> getWidgetClass() {
        return TankWidget.class;
    }

    @Override
    public void applyWidgetInfo(@NotNull Widget widget,
                                int index,
                                boolean isXEI,
                                IO io,
                                GTRecipeTypeUI.@UnknownNullability("null when storage == null") RecipeHolder recipeHolder,
                                @NotNull GTRecipeType recipeType,
                                @UnknownNullability("null when content == null") GTRecipeDefinition recipe,
                                @Nullable FluidIngredient content,
                                @Nullable Object storage, int recipeTier, int chanceTier) {
        if (widget instanceof TankWidget tank) {
            if (storage instanceof IFluidHandler fluidHandler) {
                tank.setFluidTank(fluidHandler, index);
            }
            tank.setIngredientIO(io == IO.IN ? IngredientIO.INPUT : IngredientIO.OUTPUT);
            tank.setAllowClickFilled(!isXEI);
            tank.setAllowClickDrained(!isXEI && io.support(IO.IN));
            if (isXEI) tank.setShowAmount(false);
            if (content != null) {
                if (content.isChanced()) {
                    tank.setXEIChance(content.getRealChance());
                }
                tank.setOnAddedTooltips((w, tooltips) -> {
                    if (!isXEI && content.getFluids().length > 0) {
                        FluidStack stack = content.getFluids()[0];
                        TooltipsHandler.appendFluidTooltips(stack, tooltips::add, TooltipFlag.NORMAL);
                    }
                    if (content instanceof RangedFluidIngredient ranged) {
                        tooltips.add(Component.translatable("gtceu.gui.content.fluid_range",
                                ranged.getMinAmount(), ranged.getAmount())
                                .withStyle(ChatFormatting.GOLD));
                    }
                    if (isTickSlot(index, io, recipe)) {
                        tooltips.add(Component.translatable("gtceu.gui.content.per_tick"));
                    }
                });
                if (io == IO.IN && content.getChance() == 0) {
                    tank.setIngredientIO(IngredientIO.CATALYST);
                }
            }
        }
    }

    // Maps fluids to a FluidEntryList for XEI: either a FluidTagList or a FluidStackList
    public static FluidEntryList mapFluid(FluidIngredient ingredient) {
        int amount = ingredient.getAmount();
        FluidIngredient base = ingredient.getInner();
        FluidIngredient.Value value = base.getValue();
        FluidTagList tags = new FluidTagList();
        FluidStackList fluids = new FluidStackList();
        if (value instanceof FluidIngredient.TagValue tagValue) {
            tags.add(tagValue.getTag(), amount, value.nbt());
        } else {
            fluids.addAll(value.getStacks().stream()
                    .filter(fluidStack -> !fluidStack.isEmpty())
                    .map(stack -> {
                        FluidStack copy = stack.copy();
                        copy.setAmount(amount);
                        return copy;
                    }).toList());
        }
        if (!tags.isEmpty()) {
            return tags;
        } else {
            return fluids;
        }
    }

    // Fluids should be respected for distinct checks
    @Override
    public boolean shouldBypassDistinct() {
        return false;
    }

    @Override
    public List<?> getXEIIngredients(List<FluidIngredient> contents, GTRecipeDefinition recipe, IO io) {
        List<EmiIngredient> emiIngredients = new ArrayList<>();
        for (var content : contents) {
            var list = mapFluid(content).getStacks().stream()
                    .map(fluidStack -> EmiStack.of(fluidStack.getFluid(), fluidStack.getTag(), fluidStack.getAmount())
                            .setChance(content.getRealChance()))
                    .toList();
            emiIngredients.add(EmiIngredient.of(list));
        }
        return emiIngredients;
    }
}
