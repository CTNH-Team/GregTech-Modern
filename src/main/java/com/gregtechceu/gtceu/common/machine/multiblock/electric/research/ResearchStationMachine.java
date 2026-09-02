package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.capability.IObjectHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IComputationProgressMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.RecipeElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NetworkedComputationContainer;
import com.gregtechceu.gtceu.common.computation.ComputationNetworkManager;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ResearchStationMachine extends RecipeElectricMultiblockMachine implements IComputationProgressMachine {

    private final NetworkedComputationContainer importComputation;

    public ResearchStationMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.importComputation = attachTrait(new NetworkedComputationContainer(this, IO.IN));
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        for (IMultiPart part : getParts()) {
            if (part instanceof IObjectHolder iObjectHolder) {
                if (iObjectHolder.getFrontFacing() != getFrontFacing().getOpposite()) {
                    onStructureInvalid();
                    return;
                }
            }
        }
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    @Override
    protected void writeMachineJadeData(CompoundTag data, BlockAccessor accessor) {
        super.writeMachineJadeData(data, accessor);
        data.putBoolean("research", true);
    }

    @Override
    protected void appendMachineJadeTooltip(CompoundTag data, ITooltip tooltip, BlockAccessor accessor,
                                            IPluginConfig config) {
        super.appendMachineJadeTooltip(data, tooltip, accessor, config);
        if (!data.getBoolean("research") || !recipeLogic.isActive()) return;
        int progress = recipeLogic.getProgress();
        int maxProgress = recipeLogic.getDuration();
        if (maxProgress <= 0) return;
        tooltip.add(tooltip.getElementHelper().progress((float) progress / maxProgress,
                Component.translatable("gtceu.jade.progress_computation", FormattingUtil.formatNumberReadable(progress),
                        FormattingUtil.formatNumberReadable(maxProgress)),
                tooltip.getElementHelper().progressStyle().color(0xFF006D6A).textColor(-1),
                Util.make(BoxStyle.DEFAULT, style -> style.borderColor = 0xFF555555), true));
    }

    private int getMaxComputation() {
        for (var part : getParts()) {
            if (part instanceof OpticalComputationHatchMachine opticalMachine) {
                return ComputationNetworkManager.get((ServerLevel) getLevel())
                        .getNetWorkAvailableCWUt(opticalMachine.getComputationPort());
            }
        }
        return 0;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        var builder = MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive())
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.work_paused",
                        "gtceu.multiblock.research_station.researching")
                .addEnergyUsageLine(energyContainer)
                .addEnergyTierLine(tier)
                .addComputationUsageLine(getMaxComputation())
                .addWorkingStatusLine();

        builder.addProgressLineOnlyPercent(recipeLogic.getProgressPercent());
    }
}
