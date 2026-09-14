package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import lombok.Setter;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransformerMachine extends TieredEnergyMachine implements IControllable {

    public static final BooleanProperty TRANSFORM_UP_PROPERTY = GTMachineModelProperties.IS_TRANSFORM_UP;

    @Persisted
    @DescSynced
    @Getter
    @UpdateListener(methodName = "onTransformUpdated")
    private boolean isTransformUp;
    @Persisted
    @Getter
    @Setter
    private boolean isWorkingEnabled;
    @Getter
    private final int baseAmp;

    public TransformerMachine(IMachineBlockEntity holder, int tier, int baseAmp) {
        super(holder, tier, machine -> createEnergyContainer(machine, tier, baseAmp));
        this.isWorkingEnabled = true;
        this.baseAmp = baseAmp;
    }

    @Override
    protected void writeMachineJadeData(CompoundTag data, BlockAccessor accessor) {
        super.writeMachineJadeData(data, accessor);
        data.putBoolean("transformUp", isTransformUp());
        data.putInt("front", getFrontFacing().get3DDataValue());
        data.putInt("tier", getTier());
        data.putInt("amperage", baseAmp);
    }

    @Override
    protected void appendMachineJadeTooltip(CompoundTag data, ITooltip tooltip, BlockAccessor accessor,
                                            IPluginConfig config) {
        super.appendMachineJadeTooltip(data, tooltip, accessor, config);
        int tier = data.getInt("tier");
        int amperage = data.getInt("amperage");
        boolean transformUp = data.getBoolean("transformUp");
        tooltip.add(Component.translatable(transformUp ? "gtceu.top.transform_up" : "gtceu.top.transform_down",
                transformUp ? GTValues.VNF[tier] + " §r(" + amperage * 4 + "A) -> " + GTValues.VNF[tier + 1] +
                        " §r(" + amperage + "A)" :
                        GTValues.VNF[tier + 1] + " §r(" + amperage + "A) -> " + GTValues.VNF[tier] + " §r(" +
                                amperage * 4 + "A)"));
        boolean front = accessor.getHitResult().getDirection() == Direction.from3DDataValue(data.getInt("front"));
        boolean input = transformUp != front;
        int displayedTier = input == transformUp ? tier : tier + 1;
        int displayedAmperage = input == transformUp ? amperage * 4 : amperage;
        tooltip.add(Component.translatable(input ? "gtceu.top.transform_input" : "gtceu.top.transform_output",
                GTValues.VNF[displayedTier] + " §r(" + displayedAmperage + "A)"));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////
    @SuppressWarnings("unused")
    private void onTransformUpdated(boolean newValue, boolean oldValue) {
        updateEnergyContainer(newValue);
    }

    private static NotifiableEnergyContainer createEnergyContainer(MetaMachine machine,
                                                                   int tier, int amp) {
        NotifiableEnergyContainer energyContainer;
        long tierVoltage = GTValues.V[tier];
        energyContainer = new NotifiableEnergyContainer(machine, tierVoltage * 8L, tierVoltage * 4, amp, tierVoltage,
                4L * amp);
        energyContainer.setSideInputCondition(
                s -> s == machine.getFrontFacing() && ((TransformerMachine) machine).isWorkingEnabled());
        energyContainer.setSideOutputCondition(
                s -> s != machine.getFrontFacing() && ((TransformerMachine) machine).isWorkingEnabled());
        return energyContainer;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateEnergyContainer(isTransformUp);
    }

    public void updateEnergyContainer(boolean isTransformUp) {
        long tierVoltage = GTValues.V[getTier()];
        int lowAmperage = baseAmp * 4;
        if (isTransformUp) {
            // storage = n amp high; input = tier / 4; amperage = 4n; output = tier; amperage = n
            this.energyContainer.resetBasicInfo(tierVoltage * 8L * lowAmperage, tierVoltage, lowAmperage,
                    tierVoltage * 4, baseAmp);
            energyContainer.setSideInputCondition(s -> s != getFrontFacing() && isWorkingEnabled());
            energyContainer.setSideOutputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
        } else {
            // storage = n amp high; input = tier; amperage = n; output = tier / 4; amperage = 4n
            this.energyContainer.resetBasicInfo(tierVoltage * 8L * lowAmperage, tierVoltage * 4, baseAmp, tierVoltage,
                    lowAmperage);
            energyContainer.setSideInputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
            energyContainer.setSideOutputCondition(s -> s != getFrontFacing() && isWorkingEnabled());
        }
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) { // frontTexture
            return GTValues.VC[getTier() + 1];
        } else if (index == 3) { // otherTexture
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    //////////////////////////////////////
    // ****** Interaction *******//
    //////////////////////////////////////

    public void setTransformUp(boolean isTransformUp) {
        if (this.isTransformUp != isTransformUp && !isRemote()) {
            this.isTransformUp = isTransformUp;
            updateEnergyContainer(isTransformUp);
            setRenderState(getRenderState().setValue(GTMachineModelProperties.IS_TRANSFORM_UP, isTransformUp));
        }
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (!isRemote()) {
            setTransformUp(!isTransformUp());
            playerIn.sendSystemMessage(Component.translatable(
                    isTransformUp() ? "gtceu.machine.transformer.message_transform_up" :
                            "gtceu.machine.transformer.message_transform_down",
                    energyContainer.getInputVoltage(), energyContainer.getInputAmperage(),
                    energyContainer.getOutputVoltage(), energyContainer.getOutputAmperage()));
        }
        return InteractionResult.CONSUME;
    }
}
