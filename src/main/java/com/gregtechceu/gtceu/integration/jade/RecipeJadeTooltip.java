package com.gregtechceu.gtceu.integration.jade;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentListMap;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

/** Jade presentation for a running recipe. */
public final class RecipeJadeTooltip {

    private RecipeJadeTooltip() {}

    public static void appendRunningRecipe(RecipeLogic logic, GTRecipe recipe, ITooltip tooltip,
                                           BlockAccessor accessor, IPluginConfig config) {
        appendRecipeContents(logic, recipe.inputs, IO.IN, false, tooltip, accessor, config);
        appendRecipeContents(logic, recipe.tickInputs, IO.IN, true, tooltip, accessor, config);
        appendRecipeContents(logic, recipe.outputs, IO.OUT, false, tooltip, accessor, config);
        appendRecipeContents(logic, recipe.tickOutputs, IO.OUT, true, tooltip, accessor, config);
        appendParallelInfo(recipe, tooltip);
    }

    private static void appendRecipeContents(RecipeLogic logic, ContentListMap contents, IO io, boolean tick,
                                             ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        contents.forEachEntry(new ContentListMap.EntryConsumer() {

            @Override
            public <T> void accept(RecipeCapability<T> capability, List<T> values) {
                if (!values.isEmpty()) {
                    capability.appendJadeRecipeTooltip(io, tick, values, logic, tooltip, accessor, config);
                }
            }
        });
    }

    private static void appendParallelInfo(GTRecipe recipe, ITooltip tooltip) {
        int parallel = recipe.parallels;
        int batch = recipe.batchParallels;
        int subtick = recipe.subtickParallels;
        int totalRuns = parallel * batch * subtick;
        if (totalRuns > 1) addRunsTooltip(tooltip, "gtceu.multiblock.total_runs", totalRuns);
        if (parallel > 1) addRunsTooltip(tooltip, "gtceu.multiblock.parallel.exact", parallel);
        if (batch > 1) addRunsTooltip(tooltip, "gtceu.multiblock.batch_enabled", batch);
        if (subtick > 1) addRunsTooltip(tooltip, "gtceu.multiblock.subtick_parallels", subtick);
    }

    private static void addRunsTooltip(ITooltip tooltip, String key, int amount) {
        tooltip.add(Component.translatable(key, Component.literal(FormattingUtil.formatNumbers(amount))
                .withStyle(ChatFormatting.DARK_PURPLE)));
    }
}
