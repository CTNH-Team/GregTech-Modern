package com.gregtechceu.gtceu.api.registry.registrate;

import com.gregtechceu.gtceu.api.item.IItemModelModifier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CustomItemModelRegistry {

    private static final Map<Item, List<IItemModelModifier>> EXACT_MODIFIERS = new HashMap<>();
    private static final Map<Predicate<Item>, List<IItemModelModifier>> PREDICATE_MODIFIERS = new HashMap<>();
    private static final Map<ResourceLocation, List<IItemModelModifier>> ID_MODIFIERS = new HashMap<>();

    public static void register(Item item, IItemModelModifier modifier) {
        EXACT_MODIFIERS.computeIfAbsent(item, k -> new ArrayList<>()).add(modifier);
    }

    public static void register(Predicate<Item> condition, IItemModelModifier modifier) {
        PREDICATE_MODIFIERS.computeIfAbsent(condition, k -> new ArrayList<>()).add(modifier);
    }

    public static void register(ResourceLocation itemId, IItemModelModifier modifier) {
        ID_MODIFIERS.computeIfAbsent(itemId, k -> new ArrayList<>()).add(modifier);
    }

    public static void register(String itemIdStr, IItemModelModifier modifier) {
        ResourceLocation id = ResourceLocation.tryParse(itemIdStr);
        if (id != null) {
            register(id, modifier);
        } else {
            System.err.println("Invalid ResourceLocation format: " + itemIdStr);
        }
    }

    public static void modifyOnTheFly(ResourceLocation id, JsonObject modelJson) {
        if (ID_MODIFIERS.containsKey(id)) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            for (IItemModelModifier modifier : ID_MODIFIERS.get(id)) {
                modifier.modify(item, id, modelJson);
            }
        }

        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item != null && item != Items.AIR) {

            if (EXACT_MODIFIERS.containsKey(item)) {
                for (IItemModelModifier modifier : EXACT_MODIFIERS.get(item)) {
                    modifier.modify(item, id, modelJson);
                }
            }
            for (Map.Entry<Predicate<Item>, List<IItemModelModifier>> predEntry : PREDICATE_MODIFIERS.entrySet()) {
                if (predEntry.getKey().test(item)) {
                    for (IItemModelModifier modifier : predEntry.getValue()) {
                        modifier.modify(item, id, modelJson);
                    }
                }
            }
        }
    }
}
