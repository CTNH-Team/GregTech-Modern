package com.gregtechceu.gtceu.api.data.worldgen.generator.veins;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTLayerPattern;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreBlockPlacer;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreVeinUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class LayeredVeinGenerator extends VeinGenerator {

    public record LayerAttitude(@Nullable Vector3f fixedNormal, boolean uniformDirection, boolean randomThickness) {}

    private static final Codec<Vector3f> NORMAL_CODEC = Codec.FLOAT.listOf().flatXmap(
            list -> list.size() == 3 && (list.get(0) != 0.0F || list.get(1) != 0.0F || list.get(2) != 0.0F) ?
                    DataResult.success(new Vector3f(list.get(0), list.get(1), list.get(2))) :
                    DataResult.error(() -> "layer_axis vector must contain exactly 3 floats and be non-zero"),
            normal -> DataResult.success(List.of(normal.x(), normal.y(), normal.z())));

    private static final Codec<LayerAttitude> ATTITUDE_CODEC = Codec.either(Codec.STRING, NORMAL_CODEC)
            .comapFlatMap(raw -> raw.map(LayeredVeinGenerator::parseAttitudeToken,
                    normal -> DataResult.success(new LayerAttitude(normal, false, false))),
                    attitude -> {
                if (attitude.fixedNormal() != null) {
                    Vector3f n = attitude.fixedNormal();
                    if (n.x() == 0.0F && n.y() == 0.0F) return Either.left("Z");
                    if (n.x() == 0.0F && n.z() == 0.0F) return Either.left("Y");
                    if (n.y() == 0.0F && n.z() == 0.0F) return Either.left("X");
                    return Either.right(n);
                }
                if (attitude.uniformDirection() && attitude.randomThickness()) return Either.left("VD");
                if (attitude.uniformDirection()) return Either.left("V");
                if (attitude.randomThickness()) return Either.left("D");
                throw new IllegalStateException("default layer attitude should stay unserialized");
            });

    private static DataResult<LayerAttitude> parseAttitudeToken(String token) {
        return switch (token.trim().toUpperCase(Locale.ROOT)) {
            case "V" -> DataResult.success(new LayerAttitude(null, true, false));
            case "D" -> DataResult.success(new LayerAttitude(null, false, true));
            case "VD", "DV" -> DataResult.success(new LayerAttitude(null, true, true));
            case "X" -> DataResult.success(new LayerAttitude(new Vector3f(1, 0, 0), false, false));
            case "Y" -> DataResult.success(new LayerAttitude(new Vector3f(0, 1, 0), false, false));
            case "Z" -> DataResult.success(new LayerAttitude(new Vector3f(0, 0, 1), false, false));
            default -> DataResult.error(() -> "unknown layer_axis option: " + token);
        };
    }

    public static final Codec<LayeredVeinGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GTLayerPattern.CODEC.listOf().fieldOf("layer_patterns")
                    .forGetter(LayeredVeinGenerator::getLayerPatterns),
            ATTITUDE_CODEC.optionalFieldOf("layer_axis").forGetter(generator -> Optional.ofNullable(generator.attitude)))
            .apply(instance, LayeredVeinGenerator::new));

    private final List<NonNullSupplier<GTLayerPattern>> bakingLayerPatterns = new ArrayList<>();

    public List<GTLayerPattern> layerPatterns;

    @Nullable
    private LayerAttitude attitude;

    public LayeredVeinGenerator(GTOreDefinition entry) {
        super(entry);
    }

    public List<GTLayerPattern> getLayerPatterns() {
        if (layerPatterns == null || this.layerPatterns.isEmpty()) {
            layerPatterns = bakingLayerPatterns.stream().map(Supplier::get).collect(Collectors.toList());
        }
        return layerPatterns;
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        return getLayerPatterns().stream()
                .flatMap(pattern -> pattern.layers.stream())
                .flatMap(GTLayerPattern.Layer::asVeinEntries)
                .distinct()
                .toList();
    }

    @Override
    public Map<BlockPos, OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry,
                                                  BlockPos origin) {
        Map<BlockPos, OreBlockPlacer> generatedBlocks = new Object2ObjectOpenHashMap<>();
        var patternPool = this.getLayerPatterns();

        if (patternPool.isEmpty())
            return Map.of();

        // minor optimization, usually only one layer pool exists
        GTLayerPattern layerPattern = patternPool.size() == 1 ?
                patternPool.get(0) : patternPool.get(random.nextInt(patternPool.size()));

        int size = entry.clusterSize().sample(random);
        float density = entry.density();

        int radius = Mth.ceil(size / 2f);

        int xMin = origin.getX() - radius;
        int yMin = origin.getY() - radius;
        int zMin = origin.getZ() - radius;
        int width = (radius * 2) + 1;
        int length = (radius * 2) + 1;
        int height = (radius * 2) + 1;

        if (origin.getY() >= level.getMaxBuildHeight())
            return Map.of();

        List<GTLayerPattern.Layer> resolvedLayers = new ArrayList<>();
        FloatList layerDiameterOffsets = new FloatArrayList();

        Vector3f layerNormal = resolveNormal(
                this.attitude == null ? new LayerAttitude(null, false, false) : this.attitude, random);
        float normalOffset = radius * (Math.abs(layerNormal.x()) + Math.abs(layerNormal.y()) +
                Math.abs(layerNormal.z()));
        for (int xOffset = 0; xOffset < width; xOffset++) {
            float sizeFractionX = xOffset * 2f / width - 1;
            float xSizeSqr = sizeFractionX * sizeFractionX;
            if (xSizeSqr > 1)
                continue;

            for (int yOffset = 0; yOffset < height; yOffset++) {
                float sizeFractionY = yOffset * 2f / height - 1;
                float ySizeSqr = sizeFractionY * sizeFractionY;
                if (xSizeSqr + ySizeSqr > 1)
                    continue;
                if (level.isOutsideBuildHeight(yMin + yOffset))
                    continue;

                for (int zOffset = 0; zOffset < length; zOffset++) {
                    float sizeFractionZ = zOffset * 2f / length - 1;
                    float zSizeSqr = sizeFractionZ * sizeFractionZ;
                    // OPTIMIZATION: all values in layerDiameterOffsets are in the [0,1] range, so
                    // check if the size is >1 before doing any of that math
                    if (xSizeSqr + ySizeSqr + zSizeSqr > 1)
                        continue;

                    int layerIndex = Mth.floor((xOffset - radius) * layerNormal.x() + (yOffset - radius) *
                            layerNormal.y() + (zOffset - radius) * layerNormal.z() + normalOffset);

                    while (layerIndex >= resolvedLayers.size()) {
                        GTLayerPattern.Layer next = layerPattern.rollNext(
                                resolvedLayers.isEmpty() ? null : resolvedLayers.get(resolvedLayers.size() - 1),
                                random);

                        float offset = random.nextFloat() * 0.5f + 0.5f;
                        // insert the previous layer if this one is null (e.g. invalid)
                        if (next == null) {
                            if (resolvedLayers.isEmpty()) {
                                continue;
                            }
                            resolvedLayers.add(resolvedLayers.get(resolvedLayers.size() - 1));
                            layerDiameterOffsets.add(offset);
                            continue;
                        }
                        for (int i = 0; i < next.minSize + random.nextInt(1 + next.maxSize - next.minSize); i++) {
                            resolvedLayers.add(next);
                            layerDiameterOffsets.add(offset);
                        }
                    }

                    if (xSizeSqr + ySizeSqr + zSizeSqr > layerDiameterOffsets.getFloat(layerIndex))
                        continue;

                    GTLayerPattern.Layer layer = resolvedLayers.get(layerIndex);
                    Either<List<OreConfiguration.TargetBlockState>, Material> state = layer.rollBlock(random);

                    int currentX = xMin + xOffset;
                    int currentY = yMin + yOffset;
                    int currentZ = zMin + zOffset;

                    final var randomSeed = random.nextLong(); // Fully deterministic regardless of chunk order

                    BlockPos currentPos = new BlockPos(currentX, currentY, currentZ);
                    generatedBlocks.put(currentPos, (access, section) -> placeBlock(access, section, randomSeed, entry,
                            density, state, currentPos));
                }
            }
        }

        return generatedBlocks;
    }

    private static void placeBlock(BulkSectionAccess access, LevelChunkSection section, long randomSeed,
                                   GTOreDefinition entry, float density,
                                   Either<List<OreConfiguration.TargetBlockState>, Material> state, BlockPos pos) {
        RandomSource random = new XoroshiroRandomSource(randomSeed);
        int x = SectionPos.sectionRelative(pos.getX());
        int y = SectionPos.sectionRelative(pos.getY());
        int z = SectionPos.sectionRelative(pos.getZ());

        BlockState blockState = section.getBlockState(x, y, z);
        BlockPos.MutableBlockPos posCursor = pos.mutable();

        if (random.nextFloat() <= density) {
            state.ifLeft(blockStates -> {
                for (OreConfiguration.TargetBlockState targetState : blockStates) {
                    if (!OreVeinUtil.canPlaceOre(blockState, access::getBlockState, random, entry, targetState,
                            posCursor))
                        continue;
                    if (targetState.state.isAir())
                        continue;
                    section.setBlockState(x, y, z, targetState.state, false);
                    break;
                }
            }).ifRight(material -> {
                if (!OreVeinUtil.canPlaceOre(blockState, access::getBlockState, random, entry, posCursor))
                    return;
                BlockState currentState = access.getBlockState(posCursor);
                var prefix = ChemicalHelper.getOrePrefix(currentState);
                if (prefix.isEmpty()) return;
                Block toPlace = ChemicalHelper.getBlock(prefix.get(), material);
                if (toPlace == null || toPlace.defaultBlockState().isAir())
                    return;
                section.setBlockState(x, y, z, toPlace.defaultBlockState(), false);
            });
        }
    }

    public LayeredVeinGenerator(List<GTLayerPattern> layerPatterns) {
        this(layerPatterns, (LayerAttitude) null);
    }

    public LayeredVeinGenerator(List<GTLayerPattern> layerPatterns, Optional<LayerAttitude> attitude) {
        this(layerPatterns, attitude.orElse(null));
    }

    public LayeredVeinGenerator(List<GTLayerPattern> layerPatterns, @Nullable LayerAttitude attitude) {
        super();
        this.layerPatterns = layerPatterns;
        this.attitude = attitude;
    }

    public LayeredVeinGenerator buildLayerPattern(Consumer<GTLayerPattern.Builder> config) {
        var builder = GTLayerPattern.builder(parent().layer().getTarget());
        config.accept(builder);

        return withLayerPattern(builder::build);
    }

    public LayeredVeinGenerator withLayerPattern(NonNullSupplier<GTLayerPattern> pattern) {
        this.bakingLayerPatterns.add(pattern);
        return this;
    }

    public LayeredVeinGenerator withLayerAxis(Direction.Axis axis) {
        return withLayerNormal(switch (axis) {
            case X -> new Vector3f(1, 0, 0);
            case Y -> new Vector3f(0, 1, 0);
            case Z -> new Vector3f(0, 0, 1);
        });
    }

    public LayeredVeinGenerator withLayerNormal(Vector3f normal) {
        if (normal.lengthSquared() == 0.0F)
            throw new IllegalArgumentException("layer normal must be non-zero");
        this.attitude = new LayerAttitude(new Vector3f(normal), false, false);
        return this;
    }

    private static Vector3f resolveNormal(LayerAttitude attitude, RandomSource random) {
        if (attitude.fixedNormal() != null)
            return attitude.fixedNormal();

        float nx;
        float ny;
        float nz;
        if (attitude.uniformDirection()) {
            double theta = random.nextDouble() * Math.PI * 2;
            double z = random.nextDouble() * 2 - 1;
            double r = Math.sqrt(1 - z * z);
            nx = (float) (r * Math.cos(theta));
            ny = (float) z;
            nz = (float) (r * Math.sin(theta));
        } else {
            ny = random.nextFloat() * 2.0F - 1.0F;
            ny = Math.signum(ny) * ny * ny;
            float r = Mth.sqrt(1.0F - ny * ny);
            float theta = random.nextFloat() * (float) (Math.PI * 2);
            nx = r * Mth.cos(theta);
            nz = r * Mth.sin(theta);
        }
        if (attitude.randomThickness()) {
            float magnitude = random.nextFloat() * 1.5F + 0.5F;
            nx *= magnitude;
            ny *= magnitude;
            nz *= magnitude;
        }
        return new Vector3f(nx, ny, nz);
    }

    public VeinGenerator build() {
        if (this.layerPatterns != null && !this.layerPatterns.isEmpty()) return this;
        this.layerPatterns = this.bakingLayerPatterns.stream()
                .map(NonNullSupplier::get)
                .toList();
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new LayeredVeinGenerator(new ArrayList<>(this.layerPatterns), this.attitude);
    }

    @Override
    public Codec<? extends VeinGenerator> codec() {
        return CODEC;
    }
}
