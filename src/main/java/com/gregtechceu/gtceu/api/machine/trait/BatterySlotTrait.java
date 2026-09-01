package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.editor.EditableUI;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.FancyInvConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.feature.IAttachConfiguratorsTrait;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/** Owns a machine's battery charger slot, transfer subscription, persistence, and UI integration. */
public class BatterySlotTrait extends MachineTrait implements IAttachConfiguratorsTrait {

    @Getter
    @Persisted
    private final CustomItemStackHandler storage;
    private final NotifiableEnergyContainer energyContainer;
    private final int tier;
    private @Nullable TickableSubscription batterySubscription;
    private @Nullable ISubscription energySubscription;

    public BatterySlotTrait(MetaMachine machine, NotifiableEnergyContainer energyContainer) {
        super(machine);
        if (!(machine instanceof ITieredMachine tieredMachine)) {
            throw new IllegalArgumentException("Battery slots require a tiered machine");
        }
        this.energyContainer = energyContainer;
        this.tier = tieredMachine.getTier();
        this.storage = new CustomItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        storage.setFilter(item -> GTCapabilityHelper.getElectricItem(item) != null ||
                ConfigHolder.INSTANCE.compat.energy.nativeEUToFE &&
                        GTCapabilityHelper.getForgeEnergyItem(item) != null);
    }

    @Override
    public void onMachineLoad() {
        if (getMachine().isRemote()) return;
        updateSubscription();
        energySubscription = energyContainer.addChangedListener(this::updateSubscription);
        storage.setOnContentsChanged(this::updateSubscription);
    }

    @Override
    public void onMachineUnload() {
        if (energySubscription != null) {
            energySubscription.unsubscribe();
            energySubscription = null;
        }
        if (batterySubscription != null) {
            batterySubscription.unsubscribe();
            batterySubscription = null;
        }
    }

    private void updateSubscription() {
        if (energyContainer.dischargeOrRechargeEnergyContainers(storage, 0, true)) {
            batterySubscription = getMachine().subscribeServerTick(batterySubscription, this::transferEnergy);
        } else if (batterySubscription != null) {
            batterySubscription.unsubscribe();
            batterySubscription = null;
        }
    }

    private void transferEnergy() {
        if (!energyContainer.dischargeOrRechargeEnergyContainers(storage, 0, false)) {
            updateSubscription();
        }
    }

    @Override
    public void onMachineDestroyed() {
        getMachine().clearInventory(storage);
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel left, ConfiguratorPanel right) {
        var configurator = new FancyInvConfigurator(storage,
                Component.translatable("gtceu.gui.charger_slot.tooltip.0"));
        configurator.setTooltips(new ArrayList<>(LangHandler.getMultiLang("gtceu.gui.charger_slot.tooltip",
                GTValues.VNF[tier], GTValues.VNF[tier])));
        right.attachConfigurators(configurator);
    }

    public static <M extends MetaMachine & ITieredMachine> EditableUI<SlotWidget, M> createBatterySlot() {
        return new EditableUI<>("battery_slot", SlotWidget.class, () -> {
            var slotWidget = new SlotWidget();
            slotWidget.setBackground(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY);
            return slotWidget;
        }, (slotWidget, machine) -> {
            var batterySlot = machine.getTraitOrThrow(BatterySlotTrait.class);
            slotWidget.setHandlerSlot(batterySlot.storage, 0);
            slotWidget.setCanPutItems(true);
            slotWidget.setCanTakeItems(true);
            slotWidget.setHoverTooltips(LangHandler.getMultiLang("gtceu.gui.charger_slot.tooltip",
                    GTValues.VNF[machine.getTier()], GTValues.VNF[machine.getTier()]).toArray(Component[]::new));
        });
    }
}
