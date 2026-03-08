package com.gregtechceu.gtceu.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Supplier;

public class ExtendedModelWithLoader implements Supplier<JsonElement> {
    private final ResourceLocation parent;
    private final JsonObject extra;

    public ExtendedModelWithLoader(ResourceLocation parent, JsonObject extra) {
        this.parent = parent;
        this.extra = extra;
    }

    public JsonElement get() {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("parent", this.parent.toString());
        for (Map.Entry<String, JsonElement> entry : extra.entrySet()) {
            String key = entry.getKey();
            if (key.equals("textures") && jsonobject.has("textures")) {
                JsonObject merged = jsonobject.getAsJsonObject("textures");
                JsonObject add = entry.getValue().getAsJsonObject();

                for (Map.Entry<String, JsonElement> tex : add.entrySet()) {
                    merged.add(tex.getKey(), tex.getValue());
                }
            } else {
                jsonobject.add(key, entry.getValue());
            }
        }
        return jsonobject;
    }
}
