package com.gregtechceu.gtceu.api.recipe.lookup;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@ApiStatus.Internal
final class Branch {

    // Keys on this have (should) have unique hashcodes.
    private Map<AbstractMapIngredient, Branch> nodes;
    // Keys on this have collisions, and must be differentiated by equality.
    private Map<AbstractMapIngredient, Branch> specialNodes;
    // Recipes terminating at this branch
    private List<GTRecipeDefinition> recipes;

    public boolean isEmptyBranch() {
        return (nodes == null || nodes.isEmpty()) &&
                (specialNodes == null || specialNodes.isEmpty()) &&
                (recipes == null || recipes.isEmpty());
    }

    @NotNull
    public Map<AbstractMapIngredient, Branch> getNodes() {
        if (nodes == null) {
            nodes = new Object2ObjectOpenHashMap<>(2);
        }
        return nodes;
    }

    @NotNull
    public Map<AbstractMapIngredient, Branch> getSpecialNodes() {
        if (specialNodes == null) {
            specialNodes = new Object2ObjectOpenHashMap<>(2);
        }
        return specialNodes;
    }

    @NotNull
    public List<GTRecipeDefinition> getRecipes() {
        if (recipes == null) {
            recipes = new ObjectArrayList<>(1);
        }
        return recipes;
    }

    public boolean hasRecipes() {
        return recipes != null && !recipes.isEmpty();
    }

    /**
     * Removes all nodes and recipes in the branch
     */
    public void clear() {
        this.specialNodes = null;
        this.nodes = null;
        this.recipes = null;
    }
}
