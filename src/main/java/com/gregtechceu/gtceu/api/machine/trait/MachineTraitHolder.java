package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib.syncdata.IFieldUpdateListener;
import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.accessor.IManagedAccessor;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedKey;
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
            if (machine.getHolder().getRootStorage() != null) {
                machine.getHolder().getRootStorage().attach(new SyncOnlyStorage(trait.getSyncStorage()));
            }
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

    public <T extends MachineTrait> T attachPersistent(String name, T trait) {
        return attachPersistent(name, trait, 1);
    }

    public <T extends MachineTrait> T attachPersistent(String name, T trait, int priority) {
        if (persistent.containsKey(name)) throw new IllegalArgumentException("Duplicate persistent trait: " + name);
        if (persistent.containsValue(trait))
            throw new IllegalArgumentException("Trait is already persistent: " + trait.getClass().getName());
        trait.setTraitPriority(priority);
        attach(trait);
        persistent.put(name, trait);
        return trait;
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

    /** Keeps trait persistence namespaced under {@code traits} while exposing its synchronized fields to LDLib. */
    private record SyncOnlyStorage(IManagedStorage delegate) implements IManagedStorage {

        private static final IRef[] NO_FIELDS = new IRef[0];

        @Override
        public IManaged[] getManaged() {
            return delegate.getManaged();
        }

        @Override
        public IRef getFieldByKey(ManagedKey key) {
            return delegate.getFieldByKey(key);
        }

        @Override
        public IRef[] getNonLazyFields() {
            return delegate.getNonLazyFields();
        }

        @Override
        public boolean hasDirtySyncFields() {
            return delegate.hasDirtySyncFields();
        }

        @Override
        public boolean hasDirtyPersistedFields() {
            return false;
        }

        @Override
        public IRef[] getPersistedFields() {
            return NO_FIELDS;
        }

        @Override
        public IRef[] getSyncFields() {
            return delegate.getSyncFields();
        }

        @Override
        public <T> ISubscription addSyncUpdateListener(ManagedKey key, IFieldUpdateListener<T> listener) {
            return delegate.addSyncUpdateListener(key, listener);
        }

        @Override
        public void removeAllSyncUpdateListener(ManagedKey key) {
            delegate.removeAllSyncUpdateListener(key);
        }

        @Override
        public boolean hasSyncListener(ManagedKey key) {
            return delegate.hasSyncListener(key);
        }

        @Override
        public <T> void notifyFieldUpdate(ManagedKey key, T newValue, T oldValue) {
            delegate.notifyFieldUpdate(key, newValue, oldValue);
        }

        @Override
        public void init() {
            delegate.init();
        }
    }
}
