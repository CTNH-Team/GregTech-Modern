package com.gregtechceu.gtceu.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.integration.jade.provider.*;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.tterrag.registrate.util.entry.ItemProviderEntry;
import snownee.jade.addon.harvest.HarvestToolProvider;
import snownee.jade.addon.harvest.SimpleToolHandler;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

import java.util.Objects;

@WailaPlugin
public class GTJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new MachineJadeProvider(), MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(new CableBlockProvider(), BlockEntity.class);
        registration.registerBlockDataProvider(new StainedColorProvider(), BlockEntity.class);

        registration.registerItemStorage(GTItemStorageProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerFluidStorage(GTFluidStorageProvider.INSTANCE, MetaMachineBlockEntity.class);
        registration.registerFluidStorage(FluidPipeStorageProvider.INSTANCE, FluidPipeBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new MachineJadeProvider(), Block.class);
        registration.registerBlockComponent(new CableBlockProvider(), Block.class);
        registration.registerBlockComponent(new StainedColorProvider(), Block.class);

        registration.registerItemStorageClient(GTItemStorageProvider.INSTANCE);
        registration.registerFluidStorageClient(GTFluidStorageProvider.INSTANCE);
        registration.registerFluidStorageClient(FluidPipeStorageProvider.INSTANCE);
    }

    static {
        GTMaterialItems.TOOL_ITEMS.columnMap().forEach((type, map) -> {
            if (type.harvestTags.isEmpty() || type.harvestTags.get(0).location().getNamespace().equals("minecraft"))
                return;
            HarvestToolProvider.registerHandler(new SimpleToolHandler(type.name, type.harvestTags.get(0),
                    map.values().stream().filter(Objects::nonNull).filter(ItemProviderEntry::isPresent)
                            .map(ItemProviderEntry::asItem).toArray(Item[]::new)));
        });
    }
}
