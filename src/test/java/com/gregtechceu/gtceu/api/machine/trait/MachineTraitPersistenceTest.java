package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MachineTraitPersistenceTest {

    @GameTest(template = "empty_5x5", batch = "MachineTraitPersistence")
    public static void autoOutputUsesNamespacedPersistence(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, GTMachines.CHEMICAL_REACTOR[GTValues.LV].getBlock());

        var holder = (MetaMachineBlockEntity) helper.getBlockEntity(pos);
        var autoOutput = holder.getMetaMachine().getTrait(AutoOutputTrait.class);
        autoOutput.setOutputFacingItems(Direction.UP);
        autoOutput.setOutputFacingFluids(Direction.DOWN);
        autoOutput.setAutoOutputItems(true);
        autoOutput.setAutoOutputFluids(true);
        autoOutput.setAllowInputFromOutputSideItems(true);
        autoOutput.setAllowInputFromOutputSideFluids(true);

        CompoundTag tag = new CompoundTag();
        holder.saveManagedPersistentData(tag, false);

        helper.assertTrue(tag.contains("traits", Tag.TAG_COMPOUND), "Missing trait persistence namespace");
        CompoundTag traits = tag.getCompound("traits");
        helper.assertTrue(traits.contains("auto_output", Tag.TAG_COMPOUND), "Missing auto-output trait data");
        CompoundTag autoOutputTag = traits.getCompound("auto_output");
        helper.assertTrue(autoOutputTag.getBoolean("autoOutputItems"), "Item auto-output state was not saved");
        helper.assertTrue(autoOutputTag.getBoolean("autoOutputFluids"), "Fluid auto-output state was not saved");
        helper.assertTrue(!tag.contains("autoOutputTrait"), "Auto-output trait was persisted twice");
        helper.assertTrue(!tag.contains("autoOutputItems"), "Auto-output fields leaked into the machine NBT root");
        helper.succeed();
    }
}
