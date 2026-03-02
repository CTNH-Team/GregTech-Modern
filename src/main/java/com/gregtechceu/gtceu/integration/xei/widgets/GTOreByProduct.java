package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.OreProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidStackList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidTagList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemStackList;
import com.gregtechceu.gtceu.integration.xei.entry.item.ItemTagList;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GTOreByProduct {

    private static final List<TagPrefix> ORES = new ArrayList<>();

    public static void addOreByProductPrefix(TagPrefix orePrefix) {
        if (!ORES.contains(orePrefix)) {
            ORES.add(orePrefix);
        }
    }

    private static ImmutableList<TagPrefix> IN_PROCESSING_STEPS;

    private static ImmutableList<ItemStack> ALWAYS_MACHINES;

    private final Int2ObjectMap<Content> chances = new Int2ObjectOpenHashMap<>();
    protected final List<ItemEntryList> itemInputs = new ArrayList<>();
    protected final NonNullList<ItemStack> itemOutputs = NonNullList.create();
    protected final List<FluidEntryList> fluidInputs = new ArrayList<>();
    private boolean hasDirectSmelt = false;
    private boolean hasChemBath = false;
    private boolean hasSeparator = false;
    private boolean hasSifter = false;
    private int currentSlot;

    public GTOreByProduct(Material material) {
        if (IN_PROCESSING_STEPS == null) {
            IN_PROCESSING_STEPS = ImmutableList.of(
                    TagPrefix.crushed,
                    TagPrefix.crushedPurified,
                    TagPrefix.dustImpure,
                    TagPrefix.dustPure,
                    TagPrefix.crushedRefined);
        }
        if (ALWAYS_MACHINES == null) {
            ALWAYS_MACHINES = ImmutableList.of(
                    GTMachines.MACERATOR[GTValues.LV].asStack(),
                    GTMachines.MACERATOR[GTValues.LV].asStack(),
                    GTMachines.CENTRIFUGE[GTValues.LV].asStack(),
                    GTMachines.ORE_WASHER[GTValues.LV].asStack(),
                    GTMachines.THERMAL_CENTRIFUGE[GTValues.LV].asStack(),
                    GTMachines.MACERATOR[GTValues.LV].asStack(),
                    GTMachines.MACERATOR[GTValues.LV].asStack(),
                    GTMachines.CENTRIFUGE[GTValues.LV].asStack());
        }
        OreProperty property = material.getProperty(PropertyKey.ORE);
        int oreMultiplier = property.getOreMultiplier();
        int byproductMultiplier = property.getByProductMultiplier();
        currentSlot = 0;
        Material[] byproducts = new Material[] {
                property.getOreByProduct(0, material),
                property.getOreByProduct(1, material),
                property.getOreByProduct(2, material),
                property.getOreByProduct(3, material)
        };

        // "INPUTS"

        ObjectIntPair<Material> washedIn = property.getWashedIn();
        List<Material> separatedInto = property.getSeparatedInto();

        ItemTagList oreStacks = new ItemTagList();
        for (TagPrefix prefix : ORES) {
            // get all ores with the relevant oredicts instead of just the first unified ore
            oreStacks.add(ChemicalHelper.getTag(prefix, material), 1, null);
        }
        oreStacks.add(ChemicalHelper.getTag(TagPrefix.rawOre, material), 1, null);
        itemInputs.add(oreStacks);

        // set up machines as inputs
        List<ItemStack> simpleWashers = new ArrayList<>();
        simpleWashers.add(new ItemStack(Items.CAULDRON));
        simpleWashers.add(GTMachines.ORE_WASHER[GTValues.LV].asStack());

        if (!material.hasProperty(PropertyKey.BLAST)) {
            addToInputs(new ItemStack(Blocks.FURNACE));
            hasDirectSmelt = true;
        } else {
            addToInputs(ItemStack.EMPTY);
        }

        for (ItemStack stack : ALWAYS_MACHINES) {
            addToInputs(stack);
        }
        // same amount of lines as a for loop :trol:
        itemInputs.add(ItemStackList.of(simpleWashers));
        itemInputs.add(ItemStackList.of(simpleWashers));
        itemInputs.add(ItemStackList.of(simpleWashers));

        if (!washedIn.first().isNull()) {
            hasChemBath = true;
            addToInputs(GTMachines.CHEMICAL_BATH[GTValues.LV].asStack());
        } else {
            addToInputs(ItemStack.EMPTY);
        }
        if (separatedInto != null && !separatedInto.isEmpty()) {
            hasSeparator = true;
            addToInputs(GTMachines.ELECTROMAGNETIC_SEPARATOR[GTValues.LV].asStack());
        } else {
            addToInputs(ItemStack.EMPTY);
        }
        if (material.hasProperty(PropertyKey.GEM)) {
            hasSifter = true;
            addToInputs(GTMachines.SIFTER[GTValues.LV].asStack());
        } else {
            addToInputs(ItemStack.EMPTY);
        }

        // add prefixes that should count as inputs to input lists (they will not be displayed in actual page)
        for (TagPrefix prefix : IN_PROCESSING_STEPS) {
            itemInputs.add(ItemTagList.of(ChemicalHelper.getTag(prefix, material), 1, null));
        }

        // total number of inputs added
        currentSlot += 21;

        // BASIC PROCESSING

        // begin lots of logic duplication from OreRecipeHandler
        // direct smelt
        if (hasDirectSmelt) {
            ItemStack smeltingResult;
            Material smeltingMaterial = property.getDirectSmeltResult().isNull() ? material :
                    property.getDirectSmeltResult();
            if (smeltingMaterial.hasProperty(PropertyKey.INGOT)) {
                smeltingResult = ChemicalHelper.get(TagPrefix.ingot, smeltingMaterial);
            } else if (smeltingMaterial.hasProperty(PropertyKey.GEM)) {
                smeltingResult = ChemicalHelper.get(TagPrefix.gem, smeltingMaterial);
            } else {
                smeltingResult = ChemicalHelper.get(TagPrefix.dust, smeltingMaterial);
            }
            smeltingResult.setCount(smeltingResult.getCount() * oreMultiplier);
            addToOutputs(smeltingResult);
        } else {
            addEmptyOutputs(1);
        }

        // macerate ore -> crushed
        addToOutputs(material, TagPrefix.crushed, 2 * oreMultiplier);
        if (!ChemicalHelper.get(TagPrefix.gem, byproducts[0]).isEmpty()) {
            addToOutputs(byproducts[0], TagPrefix.gem, 1);
        } else {
            addToOutputs(byproducts[0], TagPrefix.dust, 1);
        }
        addChance(1400, 0);

        // macerate crushed -> impure
        addToOutputs(material, TagPrefix.dustImpure, 1);
        addToOutputs(byproducts[0], TagPrefix.dust, byproductMultiplier);
        addChance(1400, 0);

        // centrifuge impure -> dust
        addToOutputs(material, TagPrefix.dust, 1);
        addToOutputs(byproducts[0], TagPrefix.dust, 1);
        addChance(1111, 0);

        // ore wash crushed -> crushed purified
        addToOutputs(material, TagPrefix.crushedPurified, 1);
        addToOutputs(byproducts[0], TagPrefix.dust, 1);
        addChance(3333, 0);
        FluidTagList tagList = new FluidTagList();
        tagList.add(GTMaterials.Water.getFluidTag(), 1000, null);
        tagList.add(GTMaterials.DistilledWater.getFluidTag(), 100, null);
        fluidInputs.add(tagList);

        // TC crushed/crushed purified -> centrifuged
        addToOutputs(material, TagPrefix.crushedRefined, 1);
        addToOutputs(byproducts[1], TagPrefix.dust, byproductMultiplier);
        addChance(3333, 0);

        // macerate centrifuged -> dust
        addToOutputs(material, TagPrefix.dust, 1);
        addToOutputs(byproducts[2], TagPrefix.dust, 1);
        addChance(1400, 0);

        // macerate crushed purified -> purified
        addToOutputs(material, TagPrefix.dustPure, 1);
        addToOutputs(byproducts[1], TagPrefix.dust, 1);
        addChance(1400, 0);

        // centrifuge purified -> dust
        addToOutputs(material, TagPrefix.dust, 1);
        addToOutputs(byproducts[1], TagPrefix.dust, 1);
        addChance(1111, 0);

        // cauldron/simple washer
        addToOutputs(material, TagPrefix.crushed, 1);
        addToOutputs(material, TagPrefix.crushedPurified, 1);
        addToOutputs(material, TagPrefix.dustImpure, 1);
        addToOutputs(material, TagPrefix.dust, 1);
        addToOutputs(material, TagPrefix.dustPure, 1);
        addToOutputs(material, TagPrefix.dust, 1);

        // ADVANCED PROCESSING

        // chem bath
        if (hasChemBath) {
            addToOutputs(material, TagPrefix.crushedPurified, 1);
            addToOutputs(byproducts[3], TagPrefix.dust, byproductMultiplier);
            addChance(7000, 0);
            fluidInputs.add(FluidTagList.of(washedIn.first().getFluidTag(), washedIn.secondInt(), null));
        } else {
            addEmptyOutputs(2);
            fluidInputs.add(new FluidStackList());
        }

        // electromagnetic separator
        if (hasSeparator) {
            // noinspection DataFlowIssue
            TagPrefix prefix = (separatedInto.get(separatedInto.size() - 1).getBlastTemperature() == 0 &&
                    separatedInto.get(separatedInto.size() - 1).hasProperty(PropertyKey.INGOT)) ? TagPrefix.nugget :
                            TagPrefix.dust;
            ItemStack separatedStack2 = ChemicalHelper.get(prefix, separatedInto.get(separatedInto.size() - 1),
                    prefix == TagPrefix.nugget ? 2 : 1);

            addToOutputs(material, TagPrefix.dust, 1);
            addToOutputs(separatedInto.get(0), TagPrefix.dust, 1);
            addChance(1000, 0);
            addToOutputs(separatedStack2);
            addChance(prefix == TagPrefix.dust ? 500 : 2000, 0);
        } else {
            addEmptyOutputs(3);
        }

        // sifter
        if (hasSifter) {
            boolean highOutput = material.hasFlag(MaterialFlags.HIGH_SIFTER_OUTPUT);
            ItemStack flawedStack = ChemicalHelper.get(TagPrefix.gemFlawed, material);
            ItemStack chippedStack = ChemicalHelper.get(TagPrefix.gemChipped, material);

            addToOutputs(material, TagPrefix.gemExquisite, 1);
            addGemChance(300, 0, 500, 0, highOutput);
            addToOutputs(material, TagPrefix.gemFlawless, 1);
            addGemChance(1000, 0, 1500, 0, highOutput);
            addToOutputs(material, TagPrefix.gem, 1);
            addGemChance(3500, 0, 5000, 0, highOutput);
            addToOutputs(material, TagPrefix.dustPure, 1);
            addGemChance(5000, 0, 2500, 0, highOutput);

            if (!flawedStack.isEmpty()) {
                addToOutputs(flawedStack);
                addGemChance(2500, 0, 2000, 0, highOutput);
            } else {
                addEmptyOutputs(1);
            }
            if (!chippedStack.isEmpty()) {
                addToOutputs(chippedStack);
                addGemChance(3500, 0, 3000, 0, highOutput);
            } else {
                addEmptyOutputs(1);
            }
        } else {
            addEmptyOutputs(6);
        }

        applyRuntimeRecipeOverrides(material);
    }

    private void applyRuntimeRecipeOverrides(Material material) {
        String name = material.getName();

        applyMainAndByproducts(findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.MACERATOR_RECIPES,
                recipeInputCandidates(material, ORES, TagPrefix.rawOre),
                recipeOutputCandidates(material, TagPrefix.crushed, TagPrefix.gem),
                "macerate_" + name + "_ore_to_crushed_ore"), 1, 2, false);
        applyMainAndByproducts(findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.MACERATOR_RECIPES,
                recipeInputCandidates(material, TagPrefix.crushed),
                recipeOutputCandidates(material, TagPrefix.dustImpure),
                "macerate_" + name + "_crushed_ore_to_impure_dust"), 3, 2, false);
        applyMainAndByproducts(findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.CENTRIFUGE_RECIPES,
                recipeInputCandidates(material, TagPrefix.dustImpure),
                recipeOutputCandidates(material, TagPrefix.dust),
                "centrifuge_" + name + "_dirty_dust_to_dust"), 5, 2, false);

        GTRecipe washer = findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.ORE_WASHER_RECIPES,
                recipeInputCandidates(material, TagPrefix.crushed),
                recipeOutputCandidates(material, TagPrefix.crushedPurified, TagPrefix.dust),
                "wash_" + name + "_crushed_ore_to_purified_ore",
                "wash_" + name + "_crushed_ore_to_purified_ore_distilled",
                "wash_" + name + "_crushed_ore_to_purified_ore_fast");
        applyMainAndByproducts(washer, 7, 2, false);
        applyFluidInput(washer, 0);

        applyMainAndByproducts(findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES,
                recipeInputCandidates(material, TagPrefix.crushed, TagPrefix.crushedPurified),
                recipeOutputCandidates(material, TagPrefix.crushedRefined, TagPrefix.dust),
                "centrifuge_" + name + "_crushed_ore_to_refined_ore",
                "centrifuge_" + name + "_purified_ore_to_refined_ore"), 9, 2, false);
        applyMainAndByproducts(findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.MACERATOR_RECIPES,
                recipeInputCandidates(material, TagPrefix.crushedRefined),
                recipeOutputCandidates(material, TagPrefix.dust),
                "macerate_" + name + "_refined_ore_to_dust"), 11, 2, false);
        applyMainAndByproducts(findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.MACERATOR_RECIPES,
                recipeInputCandidates(material, TagPrefix.crushedPurified),
                recipeOutputCandidates(material, TagPrefix.dustPure),
                "macerate_" + name + "_crushed_ore_to_dust"), 13, 2, false);
        applyMainAndByproducts(findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.CENTRIFUGE_RECIPES,
                recipeInputCandidates(material, TagPrefix.dustPure),
                recipeOutputCandidates(material, TagPrefix.dust),
                "centrifuge_" + name + "_pure_dust_to_dust"), 15, 2, false);

        GTRecipe chemBath = findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.CHEMICAL_BATH_RECIPES,
                recipeInputCandidates(material, TagPrefix.crushed),
                recipeOutputCandidates(material, TagPrefix.crushedPurified, TagPrefix.dust),
                "bathe_" + name + "_crushed_ore_to_purified_ore");
        hasChemBath = chemBath != null;
        setMachineInput(13, hasChemBath ? GTMachines.CHEMICAL_BATH[GTValues.LV].asStack() : ItemStack.EMPTY);
        applyMainAndByproducts(chemBath, 23, 2, true);
        applyFluidInput(chemBath, 1);

        GTRecipe separator = findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.ELECTROMAGNETIC_SEPARATOR_RECIPES,
                recipeInputCandidates(material, TagPrefix.dustPure),
                recipeOutputCandidates(material, TagPrefix.dust),
                "separate_" + name + "_pure_dust_to_dust");
        hasSeparator = separator != null;
        setMachineInput(14,
                hasSeparator ? GTMachines.ELECTROMAGNETIC_SEPARATOR[GTValues.LV].asStack() : ItemStack.EMPTY);
        applyMainAndByproducts(separator, 25, 3, true);

        GTRecipe sifter = findFirstRecipeByIdOrInputOutput(
                GTRecipeTypes.SIFTER_RECIPES,
                recipeInputCandidates(material, TagPrefix.crushedPurified),
                recipeOutputCandidates(material, TagPrefix.gemExquisite, TagPrefix.gemFlawless, TagPrefix.gem,
                        TagPrefix.dustPure, TagPrefix.gemFlawed, TagPrefix.gemChipped),
                "sift_" + name + "_purified_ore_to_gems");
        hasSifter = sifter != null;
        setMachineInput(15, hasSifter ? GTMachines.SIFTER[GTValues.LV].asStack() : ItemStack.EMPTY);
        applyMainAndByproducts(sifter, 28, 6, true);
    }

    private GTRecipe findFirstRecipeByIdOrInputOutput(GTRecipeType type, List<ItemStack> inputCandidates,
                                                      List<ItemStack> outputCandidates, String... paths) {
        for (String path : paths) {
            for (var category : type.getCategories()) {
                for (GTRecipe recipe : type.getRecipesInCategory(category)) {
                    if (recipe.getId() != null && path.equals(recipe.getId().getPath())) {
                        return recipe;
                    }
                }
            }
        }
        if (inputCandidates.isEmpty()) return null;

        for (var category : type.getCategories()) {
            for (GTRecipe recipe : type.getRecipesInCategory(category)) {
                if (matchesAnyInput(recipe, inputCandidates) && matchesAnyOutput(recipe, outputCandidates)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    private List<ItemStack> recipeInputCandidates(Material material, TagPrefix... prefixes) {
        List<ItemStack> candidates = new ArrayList<>();
        for (TagPrefix prefix : prefixes) {
            addCandidate(candidates, ChemicalHelper.get(prefix, material));
        }
        return candidates;
    }

    private List<ItemStack> recipeInputCandidates(Material material, List<TagPrefix> dynamicPrefixes,
                                                  TagPrefix... extraPrefixes) {
        List<ItemStack> candidates = new ArrayList<>();
        for (TagPrefix prefix : dynamicPrefixes) {
            addCandidate(candidates, ChemicalHelper.get(prefix, material));
        }
        for (TagPrefix prefix : extraPrefixes) {
            addCandidate(candidates, ChemicalHelper.get(prefix, material));
        }
        return candidates;
    }

    private List<ItemStack> recipeOutputCandidates(Material material, TagPrefix... prefixes) {
        List<ItemStack> candidates = new ArrayList<>();
        for (TagPrefix prefix : prefixes) {
            addCandidate(candidates, ChemicalHelper.get(prefix, material));
        }
        return candidates;
    }

    private void addCandidate(List<ItemStack> candidates, ItemStack stack) {
        if (!stack.isEmpty()) {
            candidates.add(stack);
        }
    }

    private boolean matchesAnyInput(GTRecipe recipe, List<ItemStack> candidates) {
        for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content);
            for (ItemStack candidate : candidates) {
                if (ingredient.test(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAnyOutput(GTRecipe recipe, List<ItemStack> candidates) {
        if (candidates.isEmpty()) {
            return true;
        }
        for (Content content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content);
            for (ItemStack candidate : candidates) {
                if (ingredient.test(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void applyMainAndByproducts(GTRecipe recipe, int outputStartIndex, int outputCount,
                                        boolean clearWhenMissing) {
        if (recipe == null) {
            if (clearWhenMissing) {
                clearOutputRange(outputStartIndex, outputCount);
            }
            return;
        }

        var itemOutputs = extractDisplayOutputs(recipe);
        for (int i = 0; i < outputCount; i++) {
            int outputIndex = outputStartIndex + i;
            if (i < itemOutputs.size()) {
                RecipeDisplayOutput output = itemOutputs.get(i);
                this.itemOutputs.set(outputIndex, output.stack());
                setChanceForOutput(outputIndex, output.chanceContent());
            } else {
                this.itemOutputs.set(outputIndex, ItemStack.EMPTY);
                setChanceForOutput(outputIndex, null);
            }
        }
    }

    private List<RecipeDisplayOutput> extractDisplayOutputs(GTRecipe recipe) {
        List<RecipeDisplayOutput> guaranteed = new ArrayList<>();
        List<RecipeDisplayOutput> chanced = new ArrayList<>();

        for (Content content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content);
            ItemStack[] stacks = ingredient.getItems();
            ItemStack stack = Arrays.stream(stacks)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .map(ItemStack::copy)
                    .orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) continue;

            RecipeDisplayOutput output = new RecipeDisplayOutput(stack, content.isChanced() ? content : null);
            if (content.isChanced()) {
                chanced.add(output);
            } else {
                guaranteed.add(output);
            }
        }

        List<RecipeDisplayOutput> result = new ArrayList<>();
        if (!guaranteed.isEmpty()) {
            result.add(guaranteed.get(0));
        }
        result.addAll(chanced);
        if (guaranteed.size() > 1) {
            result.addAll(guaranteed.subList(1, guaranteed.size()));
        }
        return result;
    }

    private void applyFluidInput(GTRecipe recipe, int fluidInputSlot) {
        if (fluidInputSlot < 0 || fluidInputSlot >= fluidInputs.size()) return;
        if (recipe == null) {
            fluidInputs.set(fluidInputSlot, new FluidStackList());
            return;
        }

        var fluidContents = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (fluidContents.isEmpty()) {
            fluidInputs.set(fluidInputSlot, new FluidStackList());
            return;
        }

        var ingredient = (FluidIngredient) FluidRecipeCapability.CAP.of(fluidContents.get(0).content);
        var stacks = ingredient.getStacks();
        if (stacks.length == 0) {
            fluidInputs.set(fluidInputSlot, new FluidStackList());
        } else {
            fluidInputs.set(fluidInputSlot, FluidStackList.of(Arrays.asList(stacks)));
        }
    }

    private void setMachineInput(int inputIndex, ItemStack stack) {
        if (inputIndex < 0 || inputIndex >= itemInputs.size()) return;
        itemInputs.set(inputIndex, ItemStackList.of(stack));
    }

    private void clearOutputRange(int startIndex, int count) {
        for (int i = 0; i < count; i++) {
            int outputIndex = startIndex + i;
            itemOutputs.set(outputIndex, ItemStack.EMPTY);
            setChanceForOutput(outputIndex, null);
        }
    }

    private void setChanceForOutput(int outputIndex, Content content) {
        int absoluteSlot = itemInputs.size() + outputIndex;
        if (content != null && content.isChanced()) {
            chances.put(absoluteSlot, content);
        } else {
            chances.remove(absoluteSlot);
        }
    }

    private record RecipeDisplayOutput(ItemStack stack, Content chanceContent) {}

    public void getTooltip(int slotIndex, List<Component> tooltips) {
        if (chances.containsKey(slotIndex)) {
            Content entry = chances.get(slotIndex);
            float chance = 100 * (float) entry.chance / entry.maxChance;
            if (entry.tierChanceBoost != 0) {
                float boost = entry.tierChanceBoost / 100.0f;
                tooltips.add(FormattingUtil.formatPercentage2Places("gtceu.gui.content.chance_base", chance));
                tooltips.add(FormattingUtil.formatPercentage2Places("gtceu.gui.content.chance_tier_boost_plus", boost));
            } else {
                tooltips.add(FormattingUtil.formatPercentage2Places("gtceu.gui.content.chance_no_boost", chance));
            }
        }
    }

    public Content getChance(int slot) {
        return chances.get(slot);
    }

    public boolean hasSifter() {
        return hasSifter;
    }

    public boolean hasSeparator() {
        return hasSeparator;
    }

    public boolean hasChemBath() {
        return hasChemBath;
    }

    public boolean hasDirectSmelt() {
        return hasDirectSmelt;
    }

    private void addToOutputs(Material material, TagPrefix prefix, int size) {
        addToOutputs(ChemicalHelper.get(prefix, material, size));
    }

    private void addToOutputs(ItemStack stack) {
        itemOutputs.add(stack);
        currentSlot++;
    }

    private void addEmptyOutputs(int amount) {
        for (int i = 0; i < amount; i++) {
            addToOutputs(ItemStack.EMPTY);
        }
    }

    private void addToInputs(ItemStack stack) {
        itemInputs.add(ItemStackList.of(stack));
    }

    private void addChance(int base, int tier) {
        // this is solely for the chance overlay and tooltip, neither of which care about the ItemStack
        chances.put(currentSlot - 1,
                new Content(ItemStack.EMPTY, base, ChanceLogic.getMaxChancedValue(), tier));
    }

    // make the code less :weary:
    private void addGemChance(int baseLow, int tierLow, int baseHigh, int tierHigh, boolean high) {
        if (high) {
            addChance(baseHigh, tierHigh);
        } else {
            addChance(baseLow, tierLow);
        }
    }
}
