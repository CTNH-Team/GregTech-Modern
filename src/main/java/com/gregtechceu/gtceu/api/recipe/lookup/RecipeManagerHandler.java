package com.gregtechceu.gtceu.api.recipe.lookup;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.core.mixins.RecipeManagerAccessor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Internal class handling adding recipes to GT's lookup system.
 * <p>
 * Intended for use by {@link com.gregtechceu.gtceu.core.mixins.RecipeManagerMixin}
 */
@ApiStatus.Internal
public final class RecipeManagerHandler {

    /**
     * Rebuilds the client-side category index after recipes are received from a server.
     * The client does not need the recipe lookup database, but XEI integrations need this index.
     */
    public static void rebuildCategoryMaps(@NotNull RecipeManager recipeManager) {
        for (GTRecipeType gtRecipeType : GTRegistries.RECIPE_TYPES) {
            gtRecipeType.getCategoryMap().clear();
            gtRecipeType.getProxyRecipes().forEach((proxyType, ignored) -> {
                for (var recipe : ((RecipeManagerAccessor) recipeManager).getRecipes().get(proxyType).values()) {
                    GTRecipeDefinition proxyRecipe = gtRecipeType.toGTrecipe(recipe.getId(), recipe);
                    if (proxyRecipe != null) {
                        gtRecipeType.addToCategoryMap(proxyRecipe.category, proxyRecipe);
                    }
                }
            });
            for (GTRecipeDefinition recipe : recipeManager.getAllRecipesFor(gtRecipeType)) {
                gtRecipeType.addToCategoryMap(recipe.category, recipe);
            }
        }
    }

    /**
     * Rebuilds the client-side index used to resolve researched Data Items to their recipes.
     */
    public static void rebuildResearchEntries(@NotNull RecipeManager recipeManager) {
        for (GTRecipeType gtRecipeType : GTRegistries.RECIPE_TYPES) {
            gtRecipeType.clearDataStickEntries();
            for (GTRecipeDefinition recipe : recipeManager.getAllRecipesFor(gtRecipeType)) {
                recipe.conditions.stream()
                        .filter(ResearchCondition.class::isInstance)
                        .map(ResearchCondition.class::cast)
                        .findAny()
                        .ifPresent(condition -> condition.data
                                .forEach(entry -> gtRecipeType.addDataStickEntry(entry.getResearchId(), recipe)));
            }
        }
    }

    /**
     * Adds proxy recipes to an {@link GTRecipeType}'s {@link RecipeAdditionHandler} and adds them to a list.
     *
     * @param recipesByID  the recipes stored by their ID
     * @param gtRecipeType the recipe type to add the recipes to, which owns the proxy recipes
     * @param proxyRecipes the list of proxy recipes to populate
     */
    public static void addProxyRecipesToLookup(@NotNull Map<ResourceLocation, Recipe<?>> recipesByID,
                                               @NotNull GTRecipeType gtRecipeType, @NotNull RecipeType<?> proxyType,
                                               @NotNull List<GTRecipeDefinition> proxyRecipes) {
        var lookup = gtRecipeType.getAdditionHandler();
        proxyRecipes.clear();
        recipesByID.forEach((id, recipe) -> {
            if (recipe.getType() != proxyType) {
                // do not add recipes of incompatible type
                return;
            }
            GTRecipeDefinition gtRecipe = gtRecipeType.toGTrecipe(id, recipe);
            if (gtRecipe != null) {
                proxyRecipes.add(gtRecipe);
                lookup.addStaging(gtRecipe);
            }
        });
    }

    /**
     * Adds recipes to an {@link GTRecipeType}'s {@link RecipeAdditionHandler}
     *
     * @param recipesByID  the recipes stored by their ID
     * @param gtRecipeType the recipe type to add recipes to
     */
    public static void addRecipesToLookup(@NotNull Map<ResourceLocation, Recipe<?>> recipesByID,
                                          @NotNull GTRecipeType gtRecipeType) {
        var lookup = gtRecipeType.getAdditionHandler();
        for (var r : recipesByID.values()) {
            if (r.getType() != gtRecipeType) {
                // do not add recipes of incompatible type
                continue;
            }
            if (r instanceof GTRecipeDefinition recipe) {
                lookup.addStaging(recipe);
            }
        }
    }
}
