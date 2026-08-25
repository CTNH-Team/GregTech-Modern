package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IRotorHolderMachine;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.jetbrains.annotations.NotNull;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class RotorHolderTierDifferenceTest {

    @BeforeBatch(batch = "RotorHolderTierDifference")
    public static void prepare(ServerLevel level) {}

    private static class DummyRotorHolder implements IRotorHolderMachine {

        private final int tierDiff;

        public DummyRotorHolder(int tierDiff) {
            this.tierDiff = tierDiff;
        }

        @Override
        public int getTierDifference() {
            return tierDiff;
        }

        @Override
        public int getMaxRotorHolderSpeed() {
            return 6000;
        }

        @Override
        public int getRotorSpeed() {
            return 0;
        }

        @Override
        public void setRotorSpeed(int speed) {}

        @Override
        public @NotNull Material getRotorMaterial() {
            return GTMaterials.NULL;
        }

        @Override
        public ItemStack getRotorStack() {
            return ItemStack.EMPTY;
        }

        @Override
        public void setRotorStack(ItemStack rotorStack) {}

        @Override
        public MetaMachine self() {
            return null;
        }
    }

    @GameTest(template = "empty_5x5", batch = "RotorHolderTierDifference")
    public static void testInvalidTierDifference(GameTestHelper helper) {
        DummyRotorHolder holder = new DummyRotorHolder(IRotorHolderMachine.INVALID_TIER_DIFFERENCE);
        helper.assertTrue(holder.getHolderEfficiency() == -1, "Invalid tier difference should return -1 efficiency");
        helper.assertTrue(holder.getHolderPowerMultiplier() == -1,
                "Invalid tier difference should return -1 power multiplier");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5", batch = "RotorHolderTierDifference")
    public static void testMatchingAndPositiveTierDifference(GameTestHelper helper) {
        DummyRotorHolder holder0 = new DummyRotorHolder(0);
        helper.assertTrue(holder0.getHolderEfficiency() == 100, "Matching tier should have 100% efficiency");
        helper.assertTrue(holder0.getHolderPowerMultiplier() == 1, "Matching tier should have 1x power");

        DummyRotorHolder holderPlus1 = new DummyRotorHolder(1);
        helper.assertTrue(holderPlus1.getHolderEfficiency() == 110, "+1 tier should have 110% efficiency");
        helper.assertTrue(holderPlus1.getHolderPowerMultiplier() == 2, "+1 tier should have 2x power");

        DummyRotorHolder holderPlus2 = new DummyRotorHolder(2);
        helper.assertTrue(holderPlus2.getHolderEfficiency() == 120, "+2 tier should have 120% efficiency");
        helper.assertTrue(holderPlus2.getHolderPowerMultiplier() == 4, "+2 tier should have 4x power");

        helper.succeed();
    }

    @GameTest(template = "empty_5x5", batch = "RotorHolderTierDifference")
    public static void testNegativeTierDifference(GameTestHelper helper) {
        // -1 difference (e.g. HV Rotor Holder on EV Gas Turbine)
        DummyRotorHolder holderMinus1 = new DummyRotorHolder(-1);
        helper.assertTrue(holderMinus1.getHolderEfficiency() == 90, "-1 tier difference should have 90% efficiency");
        helper.assertTrue(holderMinus1.getHolderPowerMultiplier() == 1,
                "-1 tier difference should have 1x power multiplier");

        // -2 difference (e.g. MV Rotor Holder on EV Gas Turbine)
        DummyRotorHolder holderMinus2 = new DummyRotorHolder(-2);
        helper.assertTrue(holderMinus2.getHolderEfficiency() == 80,
                "-2 tier difference should have 80% efficiency (-20% decay)");
        helper.assertTrue(holderMinus2.getHolderPowerMultiplier() == 1,
                "-2 tier difference should have 1x power multiplier");

        // Extreme low tier difference floor (min 10%)
        DummyRotorHolder holderMinus10 = new DummyRotorHolder(-10);
        helper.assertTrue(holderMinus10.getHolderEfficiency() == 10,
                "Extreme low tier should be clamped to min 10% efficiency");
        helper.assertTrue(holderMinus10.getHolderPowerMultiplier() == 1,
                "Negative tier difference should keep 1x power multiplier");

        helper.succeed();
    }
}
