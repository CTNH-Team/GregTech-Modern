package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib.syncdata.accessor.IManagedAccessor;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** Owns the immutable-after-load set of traits attached to a machine. */
public final class MachineTraitHolder {

    /**
     * A Trait class has the same type hierarchy for every machine instance. ClassValue keeps this cache scoped to
     * the defining class loader and releases it when that loader is no longer reachable.
     */
    private static final ClassValue<List<Class<?>>> TRAIT_TYPES = new ClassValue<>() {

        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            var result = new ArrayList<Class<?>>();
            collectTypes(type, result, new HashSet<>());
            return List.copyOf(result);
        }
    };

    private final MetaMachine machine;
    private final List<MachineTrait> traits = new ArrayList<>();
    private final Map<Class<?>, List<MachineTrait>> byType = new HashMap<>();
    private final Map<String, MachineTrait> persistent = new LinkedHashMap<>();
    private boolean open = true;

    public MachineTraitHolder(MetaMachine machine) {
        this.machine = machine;
    }

    public <T extends MachineTrait> T attach(T trait) {
        if (!open) throw new IllegalStateException("Traits must be attached before the machine is loaded");
        if (trait.getMachine() != machine)
            throw new IllegalStateException("Machine trait is already attached to another machine");
        if (!trait.validMachineClasses().isEmpty() && trait.validMachineClasses().stream()
                .noneMatch(type -> type.isAssignableFrom(machine.getClass())))
            throw new IllegalArgumentException(
                    "Trait " + trait.getClass().getName() + " cannot attach to " + machine.getClass().getName());
        if (!traits.contains(trait)) {
            traits.add(trait);
            index(trait, trait.getClass());
            traits.sort(Comparator.comparingInt(MachineTrait::getTraitPriority).reversed());
        }
        return trait;
    }

    private void index(MachineTrait trait, Class<?> type) {
        for (Class<?> traitType : TRAIT_TYPES.get(type)) {
            List<MachineTrait> typed = byType.computeIfAbsent(traitType, ignored -> new ArrayList<>());
            if (!typed.contains(trait)) {
                typed.add(trait);
                typed.sort(Comparator.comparingInt(MachineTrait::getTraitPriority).reversed());
            }
        }
    }

    private static void collectTypes(Class<?> type, List<Class<?>> result, Set<Class<?>> seen) {
        if (!seen.add(type)) return;
        result.add(type);

        Class<?> parent = type.getSuperclass();
        if (parent != null && MachineTrait.class.isAssignableFrom(parent)) {
            collectTypes(parent, result, seen);
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectTypes(iface, result, seen);
        }
    }

    public void attachPersistent(String name, MachineTrait trait) {
        attachPersistent(name, trait, 1);
    }

    public void attachPersistent(String name, MachineTrait trait, int priority) {
        if (persistent.containsKey(name)) throw new IllegalArgumentException("Duplicate persistent trait: " + name);
        trait.setTraitPriority(priority);
        attach(trait);
        persistent.put(name, trait);
    }

    public List<MachineTrait> all() {
        return Collections.unmodifiableList(traits);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> byType(Class<T> type) {
        List<MachineTrait> result = byType.get(type);
        if (result == null) return List.of();
        return (List<T>) (List<?>) Collections.unmodifiableList(result);
    }

    public <T> List<T> getTraitsByInterface(Class<T> type) {
        return byType(type);
    }

    public List<MachineTrait> traitsByType(Class<?> type) {
        return byType.getOrDefault(type, List.of());
    }

    public <T extends MachineTrait> T first(Class<T> type) {
        List<T> result = byType(type);
        return result.isEmpty() ? null : result.get(0);
    }

    public <T extends MachineTrait> T persistent(String name) {
        return typeCast(persistent.get(name));
    }

    public void savePersistentData(CompoundTag machineTag, boolean forDrop) {
        if (persistent.isEmpty()) return;
        CompoundTag traitsTag = new CompoundTag();
        persistent.forEach((name, trait) -> {
            CompoundTag traitTag = new CompoundTag();
            for (IRef ref : trait.getSyncStorage().getPersistedFields()) {
                if (forDrop && !ref.getKey().isDrop()) continue;
                Tag value = ref.getKey().readPersistedField(ref);
                if (value != null) traitTag.put(ref.getPersistedKey(), value);
            }
            traitsTag.put(name, traitTag);
        });
        machineTag.put("traits", traitsTag);
    }

    public void loadPersistentData(CompoundTag machineTag) {
        if (!machineTag.contains("traits", Tag.TAG_COMPOUND)) return;
        CompoundTag traitsTag = machineTag.getCompound("traits");
        persistent.forEach((name, trait) -> {
            if (traitsTag.contains(name, Tag.TAG_COMPOUND)) {
                IManagedAccessor.writePersistedFields(traitsTag.getCompound(name),
                        trait.getSyncStorage().getPersistedFields());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T typeCast(Object value) {
        return (T) value;
    }

    public void seal() {
        open = false;
    }
}
