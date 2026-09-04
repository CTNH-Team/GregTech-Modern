package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.FluidTankProxyTrait;
import com.gregtechceu.gtceu.api.machine.trait.ItemHandlerProxyTrait;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CokeOvenHatch extends MultiblockPartMachine {

    public final ItemHandlerProxyTrait inputInventory, outputInventory;
    public final FluidTankProxyTrait tank;
    @Nullable
    protected TickableSubscription autoIOSubs;
    @Nullable
    protected ISubscription outputInventorySubs, outputTankSubs;

    public CokeOvenHatch(IMachineBlockEntity holder, Object... args) {
        super(holder);
        this.inputInventory = attachTrait(new ItemHandlerProxyTrait(this, IO.IN));
        this.outputInventory = attachTrait(new ItemHandlerProxyTrait(this, IO.OUT));
        this.tank = attachTrait(new FluidTankProxyTrait(this, IO.BOTH));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////
    @Override
    public void onUnload() {
        super.onUnload();
        clearControllerRuntime();
    }

    private void clearControllerRuntime() {
        inputInventory.setProxy(null);
        outputInventory.setProxy(null);
        tank.setProxy(null);
        unsubscribeChanges();
        if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    private void unsubscribeChanges() {
        if (outputInventorySubs != null) {
            outputInventorySubs.unsubscribe();
            outputInventorySubs = null;
        }
        if (outputTankSubs != null) {
            outputTankSubs.unsubscribe();
            outputTankSubs = null;
        }
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof CokeOvenMachine cokeOven) {
            unsubscribeChanges();
            outputInventorySubs = cokeOven.exportItems.addChangedListener(this::updateAutoIOSubscription);
            outputTankSubs = cokeOven.exportFluids.addChangedListener(this::updateAutoIOSubscription);
            inputInventory.setProxy(cokeOven.importItems);
            outputInventory.setProxy(cokeOven.exportItems);
            tank.setProxy(cokeOven.exportFluids);
            this.updateAutoIOSubscription();
        }
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        clearControllerRuntime();
    }

    @Override
    public void unloadedFromController(IMultiController controller) {
        super.unloadedFromController(controller);
        clearControllerRuntime();
    }

    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public boolean replacePartModelWhenFormed() {
        return false;
    }

    //////////////////////////////////////
    // ******** Auto IO *********//
    //////////////////////////////////////

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoIOSubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateAutoIOSubscription();
    }

    protected void updateAutoIOSubscription() {
        if (hasOperationalController() && ((!outputInventory.isEmpty() &&
                GTTransferUtils.hasAdjacentItemHandler(getLevel(), getPos(), getFrontFacing())) ||
                (!tank.isEmpty() && GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getPos(), getFrontFacing())))) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoIO() {
        if (!hasOperationalController()) {
            updateAutoIOSubscription();
            return;
        }
        if (getOffsetTimer() % 5 == 0) {
            outputInventory.exportToNearby(getFrontFacing());
            tank.exportToNearby(getFrontFacing());
            updateAutoIOSubscription();
        }
    }

    private boolean hasOperationalController() {
        return controllers.stream().anyMatch(IMultiController::isStructureOperational);
    }

    //////////////////////////////////////
    // ********* GUI *********//
    //////////////////////////////////////
    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }
}
