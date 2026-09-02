package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** The sole Jade provider for {@link com.gregtechceu.gtceu.api.machine.MetaMachine} instances. */
public final class MachineJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) return;
        var machine = blockEntity.getMetaMachine();
        CompoundTag machineData = new CompoundTag();
        machine.writeJadeData(machineData, accessor);
        for (var trait : machine.getAllTraits()) {
            CompoundTag section = new CompoundTag();
            trait.writeJadeData(section, accessor);
            if (!section.isEmpty()) machineData.put(trait.getClass().getName(), section);
        }
        data.put(getUid().toString(), machineData);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)) return;
        var machine = blockEntity.getMetaMachine();
        CompoundTag machineData = accessor.getServerData().getCompound(getUid().toString());
        machine.appendJadeTooltip(machineData, tooltip, accessor, config);
        machine.getAllTraits().stream().sorted(java.util.Comparator.comparingInt(MachineTrait::jadePriority).reversed())
                .forEach(trait -> trait.appendJadeTooltip(machineData.getCompound(trait.getClass().getName()), tooltip,
                        accessor, config));
    }

    @Override
    public ResourceLocation getUid() {
        return GTCEu.id("machine");
    }
}
