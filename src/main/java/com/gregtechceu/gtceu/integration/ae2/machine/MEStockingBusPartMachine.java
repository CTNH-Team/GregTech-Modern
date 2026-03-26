package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickConfigurable;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.config.ConfigHolder;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MEStockingBusPartMachine extends MEBusPartMachine implements IDataStickConfigurable {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEStockingBusPartMachine.class,
            MEBusPartMachine.MANAGED_FIELD_HOLDER
    );

    private final int slots;

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

    public MEStockingBusPartMachine(IMachineBlockEntity holder, int tier, int slots, Object... args) {
        super(holder, tier, IO.IN, args);
        this.slots = slots;
        this.configHandler = new StockingConfigHandler(slots, () -> getInventory().onContentsChanged());
        nodeHost.getMainNode().addService(IStorageWatcherNode.class, configHandler);
    }

    @Override
    protected NotifiableItemStackHandler createInventory(Object... args) {
        return new NotifiableItemStackHandler(
                this,
                getInventorySize(),
                IO.IN,
                IO.NONE,
                MEStorageBackedItemHandler::new
        );
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateInventorySubscription();
    }

    @Override
    protected boolean shouldUpdateSubscription(Direction newFacing) {
        IManagedGridNode node = nodeHost.getMainNode();
        return isWorkingEnabled() && node.isActive() && isAutoPull();
    }

    @Override
    public void autoIO() {
        IGrid grid = nodeHost.getMainNode().getGrid();
        if (grid == null) return;

        int updateInterval = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        if (getOffsetTimer() % updateInterval != 0) return;

        KeyCounter cachedInv = grid.getStorageService().getCachedInventory();
        configHandler.autoPull(cachedInv, (key, amount) -> amount >= minStackSize && key instanceof AEItemKey);
    }

    @Override
    protected int getInventorySize() {
        return slots;
    }

    public void setAutoPull(boolean autoPull) {
        this.autoPull = autoPull;
        updateInventorySubscription();
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        sideTabs.setMainTab(this); // removes the cover configurator, it's pointless and clashes with layout.
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
                "gtceu.gui.title.adv_stocking_config.min_item_count",
                "gtceu.gui.adv_stocking_config.min_item_count",
                this::getMinStackSize,
                this::setMinStackSize)
        );
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
    public String getConfigKey() {
        return MEInputBusPartMachine.CONFIG_KEY;
    }

    @Override
    public Component getConfigName() {
        return MEInputBusPartMachine.CONFIG_NAME;
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
            getInventory().onContentsChanged();
        }
        MEConfigUtil.readMinStackSize(tag, this::setMinStackSize);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    final class MEStorageBackedItemHandler extends CustomItemStackHandler {

        public MEStorageBackedItemHandler(int slots) {
            super(slots);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            AEItemKey key = getItemKey(slot);
            return key != null && key.matches(stack);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateSlotIndex(slot);

            IManagedGridNode mainNode = nodeHost.getMainNode();
            if (!mainNode.isActive()) return ItemStack.EMPTY;
            assert mainNode.getGrid() != null;

            AEItemKey key = getItemKey(slot);
            if (key == null) return ItemStack.EMPTY;

            KeyCounter cachedInv = mainNode.getGrid().getStorageService().getCachedInventory();
            long existing = cachedInv.get(key);

            return key.toStack(GTMath.saturatedCast(existing));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;

            validateSlotIndex(slot);

            IManagedGridNode mainNode = nodeHost.getMainNode();
            if (!mainNode.isActive()) return ItemStack.EMPTY;
            assert mainNode.getGrid() != null;

            AEItemKey key = getItemKey(slot);
            if (key == null) return ItemStack.EMPTY;

            MEStorage networkInv = mainNode.getGrid().getStorageService().getInventory();
            long extracted = networkInv.extract(
                    key,
                    amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                    actionSource
            );

            // do not call onContentsChanged() here, as it will be called by IStorageWatcherNode
            return key.toStack(Math.toIntExact(extracted));
        }

        private @Nullable AEItemKey getItemKey(int slot) {
            return (AEItemKey) configHandler.getKeyInSlot(slot);
        }
    }
}
