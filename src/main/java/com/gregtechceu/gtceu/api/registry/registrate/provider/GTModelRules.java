package com.gregtechceu.gtceu.api.registry.registrate.provider;

public class GTModelRules {

    public static void init() {
        // 演示：
        // CustomItemModelRegistry.register("gtceu:copper_bolt", (item, id, json) -> {
        //
        // json.addProperty("loader", "avaritia:halo");
        //
        // JsonObject haloJson = new JsonObject();
        // haloJson.addProperty("texture", "#halo");
        // haloJson.addProperty("color", 1308622847);
        // haloJson.addProperty("size", 6);
        // haloJson.addProperty("pulse", false);
        // json.add("halo", haloJson);
        //
        // JsonObject textures = json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject();
        // textures.addProperty("halo", "avaritia:misc/halo_noise");
        // json.add("textures", textures);
        // });
    }
}
