package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IComputationProgressMachine;
import com.gregtechceu.gtceu.api.machine.feature.IWorkLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.feature.IMultiblockMachineTrait;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkLogic extends MachineTrait implements IFancyTooltip, IMultiblockMachineTrait {

    public enum Status implements StringRepresentable {

        IDLE("idle"),
        WORKING("working"),
        WAITING("waiting"),
        SUSPEND("suspend");

        @Getter
        private final String serializedName;

        Status(String name) {
            this.serializedName = name;
        }
    }

    public static final EnumProperty<WorkLogic.Status> STATUS_PROPERTY = GTMachineModelProperties.RECIPE_LOGIC_STATUS;

    @Getter
    public final IWorkLogicMachine workMachine;

    @Getter
    @Persisted
    @DescSynced
    private Status status = Status.IDLE;

    @Getter
    @Setter
    @Persisted
    protected boolean suspendAfterFinish = false;

    @Getter
    @Nullable
    @Persisted
    @DescSynced
    protected Component waitingReason = null;

    protected TickableSubscription subscription;

    @Getter
    protected final List<ISubscription> traitSubscriptions = new ArrayList<>();

    public WorkLogic(IWorkLogicMachine machine) {
        super(machine.self());
        this.workMachine = machine;
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        updateTickSubscription();
    }

    public void updateTickSubscription() {
        if (isSuspend() || !workMachine.isWorkLogicAvailable()) {
            unsubscribeTick();
        } else {
            subscription = getMachine().subscribeServerTick(subscription, this::serverTick);
        }
    }

    public void unsubscribeTick() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    public void serverTick() {
        if (!workMachine.isWorkLogicAvailable()) {
            unsubscribeTick();
            return;
        }
        if (!isSuspend()) {
            workMachine.serverRunningTick();
        }
        if ((isSuspend() || (isIdle() && !workMachine.keepSubscribing()))) {
            unsubscribeTick();
        }
    }

    public void setStatus(Status status) {
        if (this.status != status) {
            Status oldStatus = this.status;
            this.status = status;
            workMachine.notifyWorkStatusChanged(oldStatus, status);
            if (this.status != Status.WAITING) {
                waitingReason = null;
            }
            updateTickSubscription();
        }
    }

    public void setWaiting(@Nullable Component reason) {
        setStatus(Status.WAITING);
        waitingReason = reason;
        onWaiting();
    }

    protected void onWaiting() {}

    public final boolean isWorking() {
        return status == Status.WORKING && isStructureOperational();
    }

    public final boolean isIdle() {
        return status == Status.IDLE;
    }

    public final boolean isWaiting() {
        return status == Status.WAITING;
    }

    public final boolean isSuspend() {
        return status == Status.SUSPEND;
    }

    public boolean isWorkingEnabled() {
        return !isSuspend();
    }

    public void setWorkingEnabled(boolean isWorkingAllowed) {
        setStatus(isWorkingAllowed ? Status.IDLE : Status.SUSPEND);
        workMachine.notifyWorkingEnabledChanged(!isWorkingAllowed, isWorkingAllowed);
        updateTickSubscription();
    }

    public boolean isActive() {
        return isStructureOperational() && (status == Status.WORKING || status == Status.WAITING);
    }

    private boolean isStructureOperational() {
        return !(workMachine instanceof IMultiController controller) || controller.isStructureOperational();
    }

    @Override
    public int jadePriority() {
        return 800;
    }

    @Override
    public void writeJadeData(CompoundTag data, BlockAccessor accessor) {
        data.putBoolean("active", isActive());
        data.putInt("progress", workMachine.getProgress());
        data.putInt("maxProgress", workMachine.getMaxProgress());
        data.putBoolean("workingEnabled", isWorkingEnabled());
        data.putBoolean("suspendAfter", isSuspendAfterFinish());
        if (isWaiting() && waitingReason != null) {
            data.putString("waitingReason", Component.Serializer.toJson(waitingReason));
        }
    }

    @Override
    public void appendJadeTooltip(CompoundTag data, ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (data.getBoolean("suspendAfter")) {
            tooltip.add(Component.translatable("behaviour.soft_hammer.disabled_cycle")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else if (!data.getBoolean("workingEnabled")) {
            tooltip.add(Component.translatable("behaviour.soft_hammer.disabled")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        if (!data.getBoolean("active")) return;
        if (data.contains("waitingReason")) {
            Component reason = Component.Serializer.fromJson(data.getString("waitingReason"));
            if (reason != null) tooltip.add(reason.copy().withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        int progress = data.getInt("progress");
        int maxProgress = data.getInt("maxProgress");
        if (maxProgress <= 0) return;
        if (workMachine instanceof IComputationProgressMachine) return;
        Component text = Component.translatable(
                maxProgress < 20 ? "gtceu.jade.progress_tick" : "gtceu.jade.progress_sec",
                maxProgress < 20 ? progress : Math.round(progress / 20.0F),
                maxProgress < 20 ? maxProgress : Math.round(maxProgress / 20.0F));
        var helper = tooltip.getElementHelper();
        tooltip.add(helper.progress((float) progress / maxProgress, text,
                helper.progressStyle().color(data.getBoolean("workingEnabled") ? 0xFF4CBB17 : 0xFFBB1C28)
                        .textColor(-1),
                Util.make(BoxStyle.DEFAULT, style -> style.borderColor = 0xFF555555), true));
    }

    public void reset() {
        if (!isSuspend()) {
            setStatus(Status.IDLE);
        }
        updateTickSubscription();
    }

    @OnlyIn(Dist.CLIENT)
    public void updateSound() {
        // TODO : add sound for non-recipe machine
    }

    @Override
    public IGuiTexture getFancyTooltipIcon() {
        if (showFancyTooltip()) {
            return GuiTextures.INSUFFICIENT_INPUT;
        }
        return IGuiTexture.EMPTY;
    }

    @Override
    public List<Component> getFancyTooltip() {
        if (isWaiting() && waitingReason != null) {
            return List.of(waitingReason);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean showFancyTooltip() {
        return waitingReason != null;
    }

    public void addNotifier(Notifier notifier) {
        traitSubscriptions.add(notifier.addListener(this::updateTickSubscription));
    }

    @Override
    public void onMachineUnload() {
        super.onMachineUnload();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    @Override
    public void onStructureFormed() {
        IMultiblockMachineTrait.super.onStructureFormed();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    @Override
    public void onStructureInvalid() {
        IMultiblockMachineTrait.super.onStructureInvalid();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    @Override
    public void onPartUnload() {
        IMultiblockMachineTrait.super.onPartUnload();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
    }

    @FunctionalInterface
    public interface Notifier {

        ISubscription addListener(Runnable runnable);
    }
}
