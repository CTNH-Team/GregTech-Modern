package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.data.worldgen.*;
import com.gregtechceu.gtceu.api.data.worldgen.generator.veins.NoopVeinGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;

import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class GTOres {

    /**
     * The size of the largest registered vein.
     * This becomes available after all veins have been loaded.
     */
    @Getter
    private static int largestVeinSize = 0;

    @Getter
    private static int largestIndicatorOffset = 0;

    private static final Map<ResourceLocation, GTOreDefinition> toReRegister = new HashMap<>();

    public static GTOreDefinition create(ResourceLocation name, Consumer<GTOreDefinition> config) {
        GTOreDefinition def = blankOreDefinition();
        config.accept(def);

        def.register(name);
        toReRegister.put(name, def);

        return def;
    }

    public static String getTranslationKey(ResourceLocation id) {
        return "%s.ore_vein.%s".formatted(id.getNamespace(), id.getPath());
    }

    public static void init() {
        toReRegister.forEach(GTRegistries.ORE_VEINS::registerOrOverride);
    }

    public static void updateLargestVeinSize() {
        // map to average of min & max values.
        GTOres.largestVeinSize = GTRegistries.ORE_VEINS.values().stream()
                .map(GTOreDefinition::clusterSize)
                .mapToInt(intProvider -> (intProvider.getMinValue() + intProvider.getMaxValue()) / 2)
                .max()
                .orElse(0);

        GTOres.largestIndicatorOffset = GTRegistries.ORE_VEINS.values().stream()
                .flatMapToInt(definition -> definition.indicatorGenerators().stream()
                        .mapToInt(indicatorGenerator -> indicatorGenerator.getSearchRadiusModifier(
                                (int) Math.ceil(definition.clusterSize().getMinValue() / 2.0))))
                .max()
                .orElse(0);
    }

    public static GTOreDefinition blankOreDefinition() {
        return new GTOreDefinition(
                ConstantInt.ZERO, 0, 0, IWorldGenLayer.NOWHERE, Set.of(),
                HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(0)),
                0, HolderSet::direct, BiomeWeightModifier.EMPTY, NoopVeinGenerator.INSTANCE,
                new ArrayList<>());
    }
}
