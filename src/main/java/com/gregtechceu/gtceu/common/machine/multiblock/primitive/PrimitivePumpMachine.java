package com.gregtechceu.gtceu.common.machine.multiblock.primitive;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.ingredient.fluid.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraftforge.fluids.FluidType;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PrimitivePumpMachine extends MultiblockControllerMachine {

    private int biomeModifier = 0;
    private int hatchModifier = 0;
    private NotifiableFluidTank fluidTank;
    private TickableSubscription produceWaterSubscription;

    public PrimitivePumpMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    protected void writeMachineJadeData(CompoundTag data, BlockAccessor accessor) {
        super.writeMachineJadeData(data, accessor);
        data.putInt("production", getFluidProduction());
    }

    @Override
    protected void appendMachineJadeTooltip(CompoundTag data, ITooltip tooltip, BlockAccessor accessor,
                                            IPluginConfig config) {
        super.appendMachineJadeTooltip(data, tooltip, accessor, config);
        tooltip.add(Component.translatable("gtceu.top.primitive_pump_production",
                FormattingUtil.formatNumbers(data.getInt("production"))));
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        hatchModifier = 0;
        fluidTank = null;
        initializeTank();
        produceWaterSubscription = subscribeServerTick(produceWaterSubscription, this::produceWater);
    }

    private void initializeTank() {
        for (IMultiPart part : getParts()) {
            var handlerLists = part.getRecipeHandlers();

            for (var handlerList : handlerLists) {
                var recipeCap = handlerList.getCapability(FluidRecipeCapability.CAP);
                if (!recipeCap.isEmpty()) {
                    fluidTank = (NotifiableFluidTank) recipeCap.get(0);
                    long tankCapacity = fluidTank.getTankCapacity(0);
                    if (tankCapacity == FluidType.BUCKET_VOLUME) {
                        hatchModifier = 1;
                    } else if (tankCapacity == FluidType.BUCKET_VOLUME * 8) {
                        hatchModifier = 2;
                    } else {
                        hatchModifier = 4;
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        resetState();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        resetState();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        resetState();
    }

    private void resetState() {
        unsubscribe(produceWaterSubscription);
        produceWaterSubscription = null;
        hatchModifier = 0;
        fluidTank = null;
    }

    private void produceWater() {
        if (getOffsetTimer() % 20 == 0 && isStructureOperational() && !getMultiblockState().hasError()) {
            if (biomeModifier == 0) {
                biomeModifier = GTUtil.getPumpBiomeModifier(getLevel().getBiome(getPos()));
            } else if (biomeModifier > 0) {
                if (fluidTank == null) initializeTank();
                if (fluidTank != null) {
                    fluidTank.handleRecipe(IO.OUT, null,
                            List.of(FluidIngredient.of(GTMaterials.Water.getFluid(getFluidProduction()))), false);
                }
            }
        }
    }

    private boolean isRainingInBiome() {
        if (!getLevel().isRaining()) return false;
        return getBiomePrecipitation() != Precipitation.NONE;
    }

    private Precipitation getBiomePrecipitation() {
        return getLevel().getBiome(getPos()).value().getPrecipitationAt(getPos());
    }

    public int getFluidProduction() {
        int value = biomeModifier * hatchModifier;
        if (isRainingInBiome()) {
            value = value * 3 / 2;
        }
        return value;
    }
}
