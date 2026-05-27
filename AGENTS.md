# GregTech-Modern KNOWLEDGE BASE

## OVERVIEW
`GregTech-Modern` is the vendored GTCEu upstream project used as CTNH's API/runtime dependency and reference implementation. Treat it as upstream code first, local code second.

## WHERE TO LOOK
- Addon API: `src/main/java/com/gregtechceu/gtceu/api/addon/GTAddon.java`, `IGTAddon.java`. CTNH modules implement addon hooks against these APIs.
- Material APIs: `src/main/java/com/gregtechceu/gtceu/api/data/chemical/`. Material registries, properties, tag prefixes.
- Machine APIs: `src/main/java/com/gregtechceu/gtceu/api/machine/`. Machine definitions and behavior base classes.
- Recipe APIs: `src/main/java/com/gregtechceu/gtceu/api/recipe/`. Recipe types, capabilities, modifiers.
- Datagen/reference data: `src/main/java/com/gregtechceu/gtceu/data/`, `src/generated/resources`. Upstream-generated resources and providers.
- Tests: `src/test/java`. Only test suite currently present in the workspace.
- Version catalogs: `gradle/libs.versions.toml`, `gradle/forge.versions.toml`. Root `settings.gradle` imports these catalogs.

## COMMON GTCEu API QUICK REFERENCE
- `IGTAddon`: `api/addon/IGTAddon.java`. Addon lifecycle hooks: `getRegistrate()`, `initializeAddon()`, `addonModId()`, `registerTagPrefixes()`, `registerElements()`, `registerRecipeCapabilities()`, `addRecipes()`, `removeRecipes()`, ore/fluid vein hooks, and KubeJS recipe key registration.
- `GTAddon`: `api/addon/GTAddon.java`. Annotation used by GTCEu to discover addon implementations.
- `MachineDefinition`: `api/machine/MachineDefinition.java`. Registered machine definition; exposes block/item/block entity suppliers, `createMetaMachine()`, `asStack()`, shape, tier, recipe modifier, UI, tooltip, and render state settings.
- `MultiblockMachineDefinition`: `api/machine/MultiblockMachineDefinition.java`. Multiblock-specific machine definition; adds `patternFactory`, preview shapes, generator flag, flip rules, recovery items, part sorting, part appearance, and additional display hooks.
- `MetaMachine` / `IMachineBlockEntity`: `api/machine/MetaMachine.java`, `api/machine/IMachineBlockEntity.java`. Runtime machine object and block-entity bridge used by machine behavior code.
- `WorkableElectricMultiblockMachine`: `api/machine/multiblock/WorkableElectricMultiblockMachine.java`. Common base for electric multiblocks with recipe logic.
- `PartAbility`: `api/machine/multiblock/PartAbility.java`. Multiblock part capability registry: item/fluid import/export, energy hatches, maintenance, muffler, parallel hatch, laser, computation/data abilities; maps tiers to valid part blocks.
- `GTRecipe` / `GTRecipeType`: `api/recipe/GTRecipe.java`, `api/recipe/GTRecipeType.java`. Runtime recipe object and recipe type definition; use when inspecting processing, UI limits, categories, and capability maps.
- `RecipeModifier`: `api/recipe/modifier/RecipeModifier.java`. Functional interface returning a `ModifierFunction` for a machine and recipe; `applyModifier()` returns a modified recipe copy or `null`.
- `RecipeCapability<T>`: `api/capability/recipe/RecipeCapability.java`. Capability abstraction for recipe inputs/outputs; controls KubeJS conversion, slot names, matching, parallel limits, XEI widgets, and distinct-bypass behavior.
- `Material`: `api/data/chemical/material/Material.java`. Material data model; check properties, flags, components, formula, color/icon set, and generated item/fluid/block behavior.
- `MaterialRegistry`: `api/data/chemical/material/registry/MaterialRegistry.java`. Registry container for materials; CTNH material registration should respect GTCEu event/addon lifecycle.
- `TagPrefix`: `api/data/tag/TagPrefix.java`. Material item/block prefix definition; controls generated tags/items/blocks, ignored materials, material amount overrides, and localization keys.
- `BlockPattern` / `TraceabilityPredicate`: `api/pattern/BlockPattern.java`, `api/pattern/TraceabilityPredicate.java`. Multiblock structure matching and predicate DSL; includes global/layer limits, preview count, IO marking, render masking, and NBT parser metadata.
- `MultiblockShapeInfo`: `api/pattern/MultiblockShapeInfo.java`. XEI/preview shape representation; used by multiblock definitions and pattern previews.

## REGISTRATION ENTRYPOINTS
- Core registries: `api/registry/GTRegistries.java`, `api/registry/GTRegistry.java`, `api/registry/registrate/GTRegistrate.java`.
- Upstream content registries: `common/data/GTItems.java`, `GTBlocks.java`, `GTMachines.java`, `GTRecipeTypes.java`, `GTMaterials.java`, `GTElements.java`, `GTFluids.java`, `GTCreativeModeTabs.java`, `GTEntityTypes.java`.
- Machine builders: `api/registry/registrate/MachineBuilder.java`, `MultiblockMachineBuilder.java`.
- Recipe infrastructure: `api/recipe/GTRecipe.java`, `GTRecipeType.java`, `api/recipe/modifier/`, `api/capability/recipe/`.
- Material/tag APIs: `api/data/chemical/material/`, `api/data/tag/TagPrefix.java`; generated material content is driven from these APIs and common data registries.
- Datagen providers: `data/` and `api/registry/registrate/provider/`; prefer these over hand-editing generated resources.

## CONVENTIONS
- Namespace is `com.gregtechceu.gtceu`.
- This project has its own Gradle configuration and is excluded from root `ctnhSubprojects` shared CTNH plugin setup.
- Generated and resource directories are much larger than CTNH modules; narrow searches before editing.
- Prefer reading GTCEu APIs from here instead of guessing addon contracts in CTNH modules.

## COMMANDS
```bash
./gradlew :modules:GregTech-Modern:build
./gradlew :modules:GregTech-Modern:test
```

## ANTI-PATTERNS
- Do not make incidental style or API changes here while working on CTNH modules.
- Do not assume CTNH root Spotless/moddev settings apply to this project.
- Do not update vendored upstream behavior unless the task explicitly targets GTCEu internals or a required local compatibility patch.
