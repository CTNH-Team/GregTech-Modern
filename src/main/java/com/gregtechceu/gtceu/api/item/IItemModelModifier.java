package com.gregtechceu.gtceu.api.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface IItemModelModifier {

    void modify(Item item, ResourceLocation itemId, JsonObject modelJson);
}
