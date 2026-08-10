package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.ingredient.fluid.FluidIngredient;
import com.gregtechceu.gtceu.common.network.GTNetwork;

import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;

import com.lowdragmc.lowdraglib.jei.IngredientIO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;

public class CPacketEmiFluidTransfer implements GTNetwork.INetPacket {

    private final int containerId;
    private final List<Integer> sourceSlots;
    private final List<FluidIngredient> ingredients;
    private final int multiplier;

    public CPacketEmiFluidTransfer(int containerId, List<Integer> sourceSlots, List<FluidIngredient> ingredients,
                                   int multiplier) {
        this.containerId = containerId;
        this.sourceSlots = List.copyOf(sourceSlots);
        this.ingredients = List.copyOf(ingredients);
        this.multiplier = multiplier;
    }

    public CPacketEmiFluidTransfer(FriendlyByteBuf buffer) {
        containerId = buffer.readVarInt();
        sourceSlots = buffer.readList(FriendlyByteBuf::readVarInt);
        ingredients = buffer.readList(FluidIngredient::fromNetwork);
        multiplier = buffer.readVarInt();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeCollection(sourceSlots, FriendlyByteBuf::writeVarInt);
        buffer.writeCollection(ingredients, (buf, ingredient) -> ingredient.toNetwork(buf));
        buffer.writeVarInt(multiplier);
    }

    @Override
    public void execute(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || multiplier < 1 || player.containerMenu.containerId != containerId ||
                !(player.containerMenu instanceof ModularUIContainer menu)) {
            GTCEu.LOGGER.debug("[EMI Fluid Transfer] rejected: invalid menu or machine, container={}", containerId);
            return;
        }

        List<Slot> sources = new ArrayList<>();
        for (int index : sourceSlots) {
            if (index < 0 || index >= menu.slots.size()) {
                GTCEu.LOGGER.debug("[EMI Fluid Transfer] rejected: invalid source slot {}", index);
                return;
            }
            Slot slot = menu.slots.get(index);
            if (slot.container != player.getInventory()) {
                GTCEu.LOGGER.debug("[EMI Fluid Transfer] rejected: source slot {} is not player inventory", index);
                return;
            }
            sources.add(slot);
        }
//
        List<IFluidHandler> targets = menu.getModularUI().getFlatWidgetCollection().stream()
                .filter(w -> w instanceof TankWidget)
                .map(TankWidget.class::cast)
                .filter(t -> t.getIngredientIO() == IngredientIO.INPUT)
                .map(TankWidget::getFluidTank)
                .toList();

//        if (!(menu.getModularUI().holder instanceof IRecipeLogicMachine machine)) return;
//        List<IFluidHandler> targets = machine.getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP).stream()
//                .filter(IFluidHandler.class::isInstance)
//                .map(IFluidHandler.class::cast)
//                .toList();

        if (targets.isEmpty()) {
            GTCEu.LOGGER.debug("[EMI Fluid Transfer] rejected: machine has no fluid input handlers");
            return;
        }

        GTCEu.LOGGER.debug("[EMI Fluid Transfer] received: container={}, sources={}, ingredients={}, multiplier={}",
                containerId, sourceSlots, ingredients.size(), multiplier);
        for (FluidIngredient ingredient : ingredients) {
            long requested = (long) ingredient.getAmount() * multiplier;
            int remaining = (int) Math.min(Integer.MAX_VALUE, requested);
            for (Slot source : sources) {
                if (remaining <= 0) break;
                while (remaining > 0) {
                    ItemStack stack = source.getItem();
                    var container = FluidUtil.getFluidHandler(stack).resolve().orElse(null);
                    if (container == null || !containsMatchingFluid(container, ingredient)) break;

                    boolean transferred = false;
                    for (IFluidHandler target : targets) {
                        int before = getFluidAmount(target);
                        FluidActionResult result = FluidUtil.tryEmptyContainer(stack.copyWithCount(1), target, remaining,
                                null, true);
                        int moved = getFluidAmount(target) - before;
                        GTCEu.LOGGER.debug("[EMI Fluid Transfer] source={}, requested={}, moved={}, success={}",
                                source.index, remaining, moved, result.isSuccess());
                        if (!result.isSuccess() || moved <= 0) continue;

                        stack.shrink(1);
                        if (stack.isEmpty()) {
                            source.setByPlayer(result.getResult());
                        } else {
                            player.getInventory().placeItemBackInInventory(result.getResult());
                        }
                        source.setChanged();
                        remaining -= moved;
                        transferred = true;
                        break;
                    }
                    if (!transferred) break;
                }
            }
            GTCEu.LOGGER.debug("[EMI Fluid Transfer] ingredient complete: required={}, remaining={}",
                    requested, remaining);
        }
        menu.broadcastChanges();
    }

    private static boolean containsMatchingFluid(IFluidHandler handler, FluidIngredient ingredient) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (ingredient.test(handler.getFluidInTank(tank))) return true;
        }
        return false;
    }

    private static int getFluidAmount(IFluidHandler handler) {
        int amount = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            amount += handler.getFluidInTank(tank).getAmount();
        }
        return amount;
    }
}
