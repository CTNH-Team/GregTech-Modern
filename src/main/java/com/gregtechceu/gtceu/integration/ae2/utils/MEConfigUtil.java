package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import lombok.experimental.UtilityClass;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.function.Consumer;

@UtilityClass
public class MEConfigUtil {

    private final String TAG_GHOST_CIRCUIT = "GhostCircuit";
    private final String TAG_DISTINCT_BUSES = "DistinctBuses";
    private final String TAG_CONFIG_HANDLER = "ConfigHandler";
    private final String TAG_AUTO_PULL = "AutoPull";
    private final String TAG_MIN_STACK_SIZE = "MinStackSize";

    public void writeGhostCircuit(CompoundTag tag, IItemHandlerModifiable circuitInventory) {
        tag.putByte(
                TAG_GHOST_CIRCUIT,
                (byte) IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0))
        );
    }

    public void readGhostCircuit(CompoundTag tag, IItemHandlerModifiable circuitInventory) {
        if (tag.contains(TAG_GHOST_CIRCUIT)) {
            circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(tag.getByte(TAG_GHOST_CIRCUIT)));
        }
    }

    public void writeDistinctBuses(CompoundTag tag, boolean distinctBuses) {
        tag.putBoolean(TAG_DISTINCT_BUSES, distinctBuses);
    }

    public void readDistinctBuses(CompoundTag tag, Consumer<Boolean> setter) {
        if (tag.contains(TAG_DISTINCT_BUSES)) {
            setter.accept(tag.getBoolean(TAG_DISTINCT_BUSES));
        }
    }

    public void writeConfigHandler(CompoundTag tag, GenericStackHandler configHandler) {
        tag.put(TAG_CONFIG_HANDLER, configHandler.serializeNBT());
    }

    public void readConfigHandler(CompoundTag tag, GenericStackHandler configHandler) {
        if (tag.contains(TAG_CONFIG_HANDLER)) {
            configHandler.deserializeNBT(tag.getCompound(TAG_CONFIG_HANDLER));
        }
    }

    public void writeAutoPull(CompoundTag tag, boolean autoPull) {
        tag.putBoolean(TAG_AUTO_PULL, autoPull);
    }

    public void readAutoPull(CompoundTag tag, Consumer<Boolean> setter) {
        if (tag.contains(TAG_AUTO_PULL)) {
            setter.accept(false);
        }
    }

    public void writeMinStackSize(CompoundTag tag, int minStackSize) {
        tag.putInt(TAG_MIN_STACK_SIZE, minStackSize);
    }

    public void readMinStackSize(CompoundTag tag, Consumer<Integer> setter) {
        if (tag.contains(TAG_MIN_STACK_SIZE)) {
            setter.accept(tag.getInt(TAG_MIN_STACK_SIZE));
        }
    }
}
