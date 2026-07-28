package com.gregtechceu.gtceu.api;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTValuesTest {

    @GameTest(template = "empty", batch = "GTValues")
    public static void voltageTierTablesStayAligned(GameTestHelper helper) {
        helper.assertTrue(GTValues.TIER_COUNT == GTValues.ALL_TIERS.length, "Tier count does not match the tier table");
        helper.assertTrue(GTValues.TIER_COUNT == GTValues.V.length, "Voltage table does not match the tier table");
        helper.assertTrue(GTValues.TIER_COUNT == GTValues.VH.length,
                "Half-voltage table does not match the tier table");
        helper.assertTrue(GTValues.TIER_COUNT == GTValues.VA.length,
                "Amperage voltage table does not match the tier table");
        helper.assertTrue(GTValues.TIER_COUNT == GTValues.VN.length,
                "Voltage-name table does not match the tier table");

        for (int tier : GTValues.ALL_TIERS) {
            helper.assertTrue(GTValues.V[tier] == (long) GTValues.VH[tier] * 2,
                    "Half voltage is incorrect for tier " + tier);
            helper.assertTrue(GTValues.VA[tier] == GTValues.V[tier] - GTValues.V[tier] / 16,
                    "Cable-loss voltage is incorrect for tier " + tier);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "GTValues")
    public static void tiersBetweenIsInclusiveAndBounded(GameTestHelper helper) {
        helper.assertTrue(Arrays.equals(new int[] { GTValues.MV, GTValues.HV, GTValues.EV },
                GTValues.tiersBetween(GTValues.MV, GTValues.EV)), "Expected an inclusive voltage-tier range");
        helper.assertTrue(Arrays.equals(new int[] { GTValues.MAX },
                GTValues.tiersBetween(GTValues.MAX, GTValues.MAX)), "Expected a single-tier range");
        helper.assertTrue(GTValues.tiersBetween(GTValues.EV, GTValues.HV).length == 0,
                "An inverted voltage-tier range must be empty");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "GTValues")
    public static void timeConstantsUseMinecraftTicks(GameTestHelper helper) {
        helper.assertTrue(GTValues.SECONDS == 20, "A second must contain 20 Minecraft ticks");
        helper.assertTrue(GTValues.MINUTES == 60 * GTValues.SECONDS, "Minute constant is inconsistent");
        helper.assertTrue(GTValues.HOURS == 60 * GTValues.MINUTES, "Hour constant is inconsistent");
        helper.assertTrue(GTValues.DAYS == 24 * GTValues.HOURS, "Day constant is inconsistent");
        helper.succeed();
    }
}
