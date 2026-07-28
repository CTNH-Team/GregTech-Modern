package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.fluids.FluidState;
import com.gregtechceu.gtceu.api.fluids.attribute.FluidAttributes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MaterialPropertiesTest {

    @GameTest(template = "empty", batch = "MaterialProperties")
    public static void fluidPipeRespectsFluidStateProofing(GameTestHelper helper) {
        var pipe = new FluidPipeProperties(1_000, 120, false, true, false, false, 2);

        helper.assertTrue(pipe.canContain(FluidState.LIQUID), "Fluid pipes must always contain liquids");
        helper.assertFalse(pipe.canContain(FluidState.GAS), "Non-gas-proof pipe accepted gas");
        helper.assertFalse(pipe.canContain(FluidState.PLASMA), "Non-plasma-proof pipe accepted plasma");
        helper.assertTrue(pipe.isAcidProof(), "Acid-proof constructor argument was ignored");

        pipe.setGasProof(true);
        pipe.setPlasmaProof(true);
        helper.assertTrue(pipe.canContain(FluidState.GAS), "Gas-proof pipe rejected gas");
        helper.assertTrue(pipe.canContain(FluidState.PLASMA), "Plasma-proof pipe rejected plasma");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "MaterialProperties")
    public static void fluidPipeAttributesCanBeChanged(GameTestHelper helper) {
        var pipe = new FluidPipeProperties(1_000, 120, true, false, false, false);

        helper.assertFalse(pipe.isAcidProof(), "Non-acid-proof pipe accepted acid");
        pipe.setCanContain(FluidAttributes.ACID, true);
        helper.assertTrue(pipe.isAcidProof(), "Acid attribute was not added");
        helper.assertTrue(pipe.getContainedAttributes().contains(FluidAttributes.ACID),
                "Added fluid attribute is not exposed");
        pipe.setCanContain(FluidAttributes.ACID, false);
        helper.assertFalse(pipe.isAcidProof(), "Acid attribute was not removed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "MaterialProperties")
    public static void itemAndFluidPipesCannotShareAMaterial(GameTestHelper helper) {
        var properties = new MaterialProperties();
        properties.setProperty(PropertyKey.FLUID_PIPE, new FluidPipeProperties(1_000, 120, true, false, false, false));
        properties.setProperty(PropertyKey.ITEM_PIPE, new ItemPipeProperties());

        try {
            properties.verify();
            helper.fail("A material accepted both item and fluid pipe properties");
        } catch (IllegalStateException ignored) {
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "MaterialProperties")
    public static void wirePropertiesEnsureRequiredDustProperty(GameTestHelper helper) {
        var properties = new MaterialProperties();
        var wire = new WireProperties(GTValues.V[GTValues.IV], 2, 1);
        properties.setProperty(PropertyKey.WIRE, wire);

        wire.verifyProperty(properties);
        helper.assertTrue(properties.hasProperty(PropertyKey.DUST), "Wire properties did not add the required dust property");
        helper.assertFalse(properties.hasProperty(PropertyKey.INGOT), "Wire properties unexpectedly added an ingot property");
        helper.succeed();
    }
}
