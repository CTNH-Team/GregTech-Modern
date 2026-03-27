package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickConfigurable;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.integration.ae2.gui.fancyconfigurator.StockingFancyConfigurator;
import com.gregtechceu.gtceu.integration.ae2.utils.MEConfigUtil;
import com.gregtechceu.gtceu.integration.ae2.utils.StockingConfigHandler;
import com.gregtechceu.gtceu.utils.GTMath;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MEStockingHatchPartMachine extends MEHatchPartMachine implements IDataStickConfigurable {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEStockingHatchPartMachine.class,
            MEHatchPartMachine.MANAGED_FIELD_HOLDER
    );

    @Getter
    @DescSynced
    @Persisted
    private boolean autoPull;

    @Getter
    @Setter
    @Persisted
    private int minStackSize = 1;

    @Persisted
    protected final StockingConfigHandler configHandler;

    public MEStockingHatchPartMachine(IMachineBlockEntity holder, int tier, int slots, Object... args) {
        super(holder, tier, IO.IN, -1, slots, args);
        this.configHandler = new StockingConfigHandler(slots, tank::onContentsChanged);
        nodeHost.getMainNode().addService(IStorageWatcherNode.class, configHandler);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        var storages = new ArrayList<CustomFluidTank>(slots);
        for (int i = 0; i < slots; i++) {
            storages.add(new MEStorageBackedFluidStorage(i));
        }
        return new NotifiableFluidTank(this, storages, IO.IN, IO.NONE);
    }

    @Override
    protected boolean shouldUpdateSubscription(Direction newFacing) {
        IManagedGridNode node = nodeHost.getMainNode();
        return isWorkingEnabled() && node.isActive() && isAutoPull();
    }

    @Override
    protected void autoIO() {
        if (!isMESyncTick()) return;

        IGrid grid = nodeHost.getMainNode().getGrid();
        if (grid == null) return;

        KeyCounter cachedInv = grid.getStorageService().getCachedInventory();
        configHandler.autoPull(cachedInv, (key, amount) -> amount >= minStackSize && key instanceof AEFluidKey);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateTankSubscription();
    }

    public void setAutoPull(boolean autoPull) {
        this.autoPull = autoPull;
        updateTankSubscription();
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!isRemote()) {
            setAutoPull(!autoPull);
            playerIn.sendSystemMessage(autoPull
                    ? Component.translatable("gtceu.machine.me.stocking_auto_pull_enabled")
                    : Component.translatable("gtceu.machine.me.stocking_auto_pull_disabled")
            );
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        sideTabs.setMainTab(this);
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        super.attachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0, 1, 0.5),
                GuiTextures.BUTTON_AUTO_PULL.getSubTexture(0, 0.5, 1, 0.5),
                this::isAutoPull,
                (clickData, pressed) -> setAutoPull(pressed))
                .setTooltipsSupplier(pressed -> List.of(Component.translatable("gtceu.gui.me_bus.auto_pull_button"))));
        configuratorPanel.attachConfigurators(new StockingFancyConfigurator(
                "gtceu.gui.title.adv_stocking_config.min_fluid_count",
                "gtceu.gui.adv_stocking_config.min_fluid_count",
                this::getMinStackSize,
                this::setMinStackSize
        ));
    }

    @Override
    public String getConfigKey() {
        return MEInputHatchPartMachine.CONFIG_KEY;
    }

    @Override
    public Component getConfigName() {
        return MEInputHatchPartMachine.CONFIG_NAME;
    }

    @Override
    public void writeConfig(CompoundTag tag) {
        MEConfigUtil.writeGhostCircuit(tag, circuitInventory);
        MEConfigUtil.writeAutoPull(tag, autoPull);
        if (!autoPull) {
            MEConfigUtil.writeConfigHandler(tag, configHandler);
        }
        MEConfigUtil.writeMinStackSize(tag, minStackSize);
    }

    @Override
    public void readConfig(CompoundTag tag) {
        MEConfigUtil.readGhostCircuit(tag, circuitInventory);
        MEConfigUtil.readAutoPull(tag, this::setAutoPull);
        if (!autoPull) {
            MEConfigUtil.readConfigHandler(tag, configHandler);
            tank.onContentsChanged();
        }
        MEConfigUtil.readMinStackSize(tag, this::setMinStackSize);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private final class MEStorageBackedFluidStorage extends CustomFluidTank {

        private final int slot;

        private MEStorageBackedFluidStorage(int slot) {
            super(Integer.MAX_VALUE);
            this.slot = slot;
        }

        @Override
        public void setFluid(FluidStack stack) {
            // no-op
        }

        @Override
        public FluidStack getFluid() {
            IManagedGridNode mainNode = nodeHost.getMainNode();
            if (!mainNode.isActive()) return FluidStack.EMPTY;
            assert mainNode.getGrid() != null;

            AEFluidKey key = getFluidKey(slot);
            if (key == null) return FluidStack.EMPTY;

            long existing = mainNode.getGrid().getStorageService().getCachedInventory().get(key);
            if (existing <= 0) return FluidStack.EMPTY;

            return key.toStack(GTMath.saturatedCast(existing));
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            AEFluidKey key = getFluidKey(slot);
            return key != null && key.matches(stack);
        }

        @Override
        public boolean supportsFill(int tank) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;

            AEFluidKey key = getFluidKey(slot);
            if (key == null || !key.matches(resource)) return FluidStack.EMPTY;

            return extract(key, resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;

            AEFluidKey key = getFluidKey(slot);
            if (key == null) return FluidStack.EMPTY;

            return extract(key, maxDrain, action);
        }

        @Override
        public boolean isEmpty() {
            return getFluid().isEmpty();
        }

        @Override
        public int getSpace() {
            return Math.max(0, capacity - getFluid().getAmount());
        }

        private FluidStack extract(AEFluidKey key, int amount, FluidAction action) {
            IManagedGridNode mainNode = nodeHost.getMainNode();
            if (!mainNode.isActive()) return FluidStack.EMPTY;
            assert mainNode.getGrid() != null;

            MEStorage networkInv = mainNode.getGrid().getStorageService().getInventory();
            long extracted = networkInv.extract(
                    key,
                    amount,
                    action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                    actionSource
            );
            if (extracted <= 0) return FluidStack.EMPTY;

            // do not call onContentsChanged() here, as it will be called by IStorageWatcherNode
            return key.toStack(Math.toIntExact(extracted));
        }

        private @Nullable AEFluidKey getFluidKey(int slot) {
            return (AEFluidKey) configHandler.getKeyInSlot(slot);
        }
    }
}
