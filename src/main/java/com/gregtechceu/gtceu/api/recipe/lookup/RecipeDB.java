package com.gregtechceu.gtceu.api.recipe.lookup;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerGroup;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * Data structure storing recipes by their input ingredients
 */
public final class RecipeDB {

    private final @NotNull Branch rootBranch = new Branch();

    /**
     * Clear the DB
     */
    @ApiStatus.Internal
    public void clear() {
        rootBranch.clear();
    }

    /**
     * Find a GT Recipe
     *
     * @param group the holder to search
     * @return the recipe
     */
    public @Nullable GTRecipeDefinition find(@NotNull RecipeHandlerGroup group) {
        return find(group, r -> RecipeHelper.matchRecipe(group, r.toRuntime()).isSuccess());
    }

    /**
     * Find a GT Recipe
     *
     * @param group     the holder to search
     * @param predicate the predicate to determine recipe validity
     * @return the recipe
     */
    public @Nullable GTRecipeDefinition find(@NotNull RecipeHandlerGroup group,
                                             @NotNull Predicate<GTRecipeDefinition> predicate) {
        List<AbstractMapIngredient> list = fromHolder(group);
        if (list == null) {
            return null;
        }
        return find(list, predicate);
    }

    /**
     * Find a GT Recipe
     *
     * @param list      the ingredients to search
     * @param predicate the predicate to determine recipe validity
     * @return the recipe
     */
    @ApiStatus.Internal
    @VisibleForTesting
    public @Nullable GTRecipeDefinition find(@NotNull List<AbstractMapIngredient> list,
                                             @NotNull Predicate<GTRecipeDefinition> predicate) {
        var iter = new RecipeIterator(this, list, predicate);
        return iter.hasNext() ? iter.next() : null;
    }

    /**
     * Create an iterator for a search space
     *
     * @param group     the group to search
     * @param predicate the predicate to determine recipe validity
     * @return an iterator
     */
    public @Nullable RecipeDB.RecipeIterator iterator(@NotNull RecipeHandlerGroup group,
                                                      @NotNull Predicate<GTRecipeDefinition> predicate) {
        List<AbstractMapIngredient> list = fromHolder(group);
        if (list == null) {
            return null;
        }
        return new RecipeIterator(this, list, predicate);
    }

    /**
     * Converts a Recipe Capability holder's handlers into a list of {@link AbstractMapIngredient}
     *
     * @param group the capability holder to query handlers from
     * @return a list of all the AbstractMapIngredients in the handlers
     */
    private @Nullable List<AbstractMapIngredient> fromHolder(@NotNull RecipeHandlerGroup group) {
        var handlerMap = group.getInputHandlerMap();
        if (handlerMap.isEmpty()) {
            return null;
        }

        // the initial capacity is a "feel-good" value because it's faster to just grow the list
        // than to calculate an accurate value.
        List<AbstractMapIngredient> list = new ObjectArrayList<>();
        for (var entry : handlerMap.entrySet()) {
            if (!entry.getKey().isRecipeSearchFilter()) {
                continue;
            }
            for (var handler : entry.getValue()) {
                list.addAll(handler.getMapIngredients());
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        return list;
    }

    /**
     * Determine the correct root nodes for an ingredient.
     *
     * @param ingredient the ingredient to check
     * @param branch     the branch containing the nodes
     * @return the nodes to search for the ingredient
     */
    private static @NotNull Map<AbstractMapIngredient, Branch> nodesForIngredient(
                                                                                  @NotNull AbstractMapIngredient ingredient,
                                                                                  @NotNull Branch branch) {
        if (ingredient.isSpecialIngredient()) {
            return branch.getSpecialNodes();
        }
        return branch.getNodes();
    }

    /**
     * Find the child branch for an ingredient, checking special nodes first if applicable.
     */
    private static @Nullable Branch findChildBranch(@NotNull Branch branch,
                                                    @NotNull AbstractMapIngredient ingredient) {
        if (ingredient.isSpecialIngredient()) {
            Branch special = branch.getSpecialNodes().get(ingredient);
            if (special != null) {
                return special;
            }
        }
        return branch.getNodes().get(ingredient);
    }

    /**
     * Add a recipe.
     *
     * @param recipe      the recipe to add
     * @param ingredients the ingredients in optimal order, comprising the recipe
     * @return if successful
     */
    boolean add(@NotNull GTRecipeDefinition recipe,
                @NotNull List<@Unmodifiable List<AbstractMapIngredient>> ingredients) {
        if (ingredients.isEmpty()) {
            return false;
        }
        if (addRecursive(recipe, ingredients, rootBranch, 0)) {
            recipe.category.addRecipe(recipe);
            return true;
        }
        return false;
    }

    /**
     * Recursively adds a recipe.
     *
     * @param recipe      the recipe to add
     * @param ingredients the ingredients to find the recipe with
     * @param branch      the branch to add ingredients to
     * @param index       the index of the ingredient list to check
     * @return if successful
     */
    private boolean addRecursive(@NotNull GTRecipeDefinition recipe,
                                 @NotNull List<@Unmodifiable List<AbstractMapIngredient>> ingredients,
                                 @NotNull Branch branch, int index) {
        if (index >= ingredients.size()) {
            List<GTRecipeDefinition> branchRecipes = branch.getRecipes();
            if (!branchRecipes.contains(recipe)) {
                branchRecipes.add(recipe);
            }
            return true;
        }
        var current = ingredients.get(index);
        boolean anyAdded = false;
        for (AbstractMapIngredient ingredient : current) {
            var nodes = nodesForIngredient(ingredient, branch);
            Branch childBranch = nodes.computeIfAbsent(ingredient, k -> new Branch());
            boolean added = addRecursive(recipe, ingredients, childBranch, index + 1);
            if (added) {
                anyAdded = true;
            } else if (childBranch.isEmptyBranch()) {
                nodes.remove(ingredient);
            }
        }
        return anyAdded;
    }

    private static class SearchFrame {

        final Branch branch;
        int recipeIndex = 0;
        int ingredientIndex = 0;

        public SearchFrame(@NotNull Branch branch) {
            this.branch = branch;
        }
    }

    public static class RecipeIterator implements Iterator<GTRecipeDefinition> {

        private final @NotNull RecipeDB db;
        private final @NotNull List<AbstractMapIngredient> ingredients;
        private final @NotNull Predicate<GTRecipeDefinition> predicate;

        private final Deque<SearchFrame> stack = new ArrayDeque<>();
        private final Set<GTRecipeDefinition> visited = new ObjectOpenHashSet<>();

        private @Nullable GTRecipeDefinition nextCached = null;
        private boolean hasCached = false;

        @VisibleForTesting
        public RecipeIterator(@NotNull RecipeDB db,
                              @NotNull List<AbstractMapIngredient> ingredients,
                              @NotNull Predicate<GTRecipeDefinition> predicate) {
            this.db = db;
            this.ingredients = ingredients;
            this.predicate = predicate;

            stack.push(new SearchFrame(db.rootBranch));
        }

        private @Nullable GTRecipeDefinition getNext() {
            while (!stack.isEmpty()) {
                SearchFrame frame = stack.peek();

                // Phase 1: Explore deeper child branches first (depth-first search for longest/most specific recipe
                // match)
                if (frame.ingredientIndex < ingredients.size()) {
                    AbstractMapIngredient ingredient = ingredients.get(frame.ingredientIndex++);
                    Branch childBranch = findChildBranch(frame.branch, ingredient);
                    if (childBranch != null) {
                        stack.push(new SearchFrame(childBranch));
                    }
                    continue;
                }

                // Phase 2: Once child branches from this frame have been explored, yield recipes terminating at this
                // branch
                if (frame.recipeIndex < frame.branch.getRecipes().size()) {
                    GTRecipeDefinition recipe = frame.branch.getRecipes().get(frame.recipeIndex++);
                    if (visited.add(recipe) && predicate.test(recipe)) {
                        return recipe;
                    }
                    continue;
                }

                // Frame exhausted
                stack.pop();
            }

            return null; // no more recipes
        }

        @Override
        public boolean hasNext() {
            if (!hasCached) {
                nextCached = getNext();
                hasCached = true;
            }
            return nextCached != null;
        }

        @Override
        public GTRecipeDefinition next() {
            if (!hasCached) nextCached = getNext();
            hasCached = false;
            if (nextCached == null) throw new NoSuchElementException();
            return nextCached;
        }

        /**
         * Reset the iterator
         */
        public void reset() {
            stack.clear();
            visited.clear();
            hasCached = false;
            nextCached = null;
            stack.push(new SearchFrame(db.rootBranch));
        }
    }
}
