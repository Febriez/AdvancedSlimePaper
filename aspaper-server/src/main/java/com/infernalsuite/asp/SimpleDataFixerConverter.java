package com.infernalsuite.asp;

import com.infernalsuite.asp.api.SlimeDataConverter;
import com.infernalsuite.asp.level.chunk.SlimeChunkConverter;
import com.infernalsuite.asp.serialization.SlimeWorldReader;
import com.infernalsuite.asp.skeleton.SkeletonSlimeWorld;
import com.infernalsuite.asp.skeleton.SlimeChunkSectionSkeleton;
import com.infernalsuite.asp.skeleton.SlimeChunkSkeleton;
import com.infernalsuite.asp.api.world.SlimeChunk;
import com.infernalsuite.asp.api.world.SlimeChunkSection;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.util.Util;
import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

class SimpleDataFixerConverter implements SlimeWorldReader<SlimeWorld>, SlimeDataConverter {

    @Override
    public SlimeWorld readFromData(SlimeWorld data) {
        int newVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        int currentVersion = data.getDataVersion();
        // Already fixed
        if (currentVersion == newVersion) {
            return data;
        }

        Long2ObjectMap<SlimeChunk> chunks = new Long2ObjectOpenHashMap<>();
        for (SlimeChunk chunk : data.getChunkStorage()) {
            List<CompoundBinaryTag> entities = new ArrayList<>();
            List<CompoundBinaryTag> blockEntities = new ArrayList<>();
            for (CompoundBinaryTag upgradeEntity : chunk.getTileEntities()) {
                blockEntities.add(
                        convertAndBack(upgradeEntity, (tag) -> (CompoundTag) fix(References.BLOCK_ENTITY, tag, currentVersion, newVersion))
                );
            }
            for (CompoundBinaryTag upgradeEntity : chunk.getEntities()) {
                entities.add(
                        convertAndBack(upgradeEntity, (tag) -> (CompoundTag) fix(References.ENTITY, tag, currentVersion, newVersion))
                );
            }
            long chunkPos = Util.chunkPosition(chunk.getX(), chunk.getZ());

            SlimeChunkSection[] sections = new SlimeChunkSection[chunk.getSections().length];
            for (int i = 0; i < sections.length; i++) {
                SlimeChunkSection dataSection = chunk.getSections()[i];
                if (dataSection == null) continue;

                CompoundBinaryTag blockStateTag = convertAndBack(dataSection.getBlockStatesTag(),
                        (tag) -> updatePalette(tag, References.BLOCK_STATE, currentVersion, newVersion));

                CompoundBinaryTag biomeTag = convertAndBack(dataSection.getBiomeTag(),
                        (tag) -> updatePalette(tag, References.BIOME, currentVersion, newVersion));

                sections[i] = new SlimeChunkSectionSkeleton(
                        blockStateTag,
                        biomeTag,
                        dataSection.getBlockLight(),
                        dataSection.getSkyLight()
                );
            }

            CompoundBinaryTag newPoi = chunk.getPoiChunkSections() != null ? convertPoiSections(chunk.getPoiChunkSections(), currentVersion, newVersion) : null;

            chunks.put(chunkPos, new SlimeChunkSkeleton(
                    chunk.getX(),
                    chunk.getZ(),
                    sections,
                    chunk.getHeightMaps(),
                    blockEntities,
                    entities,
                    chunk.getExtraData(),
                    chunk.getUpgradeData(),
                    newPoi,
                    chunk.getBlockTicks(),
                    chunk.getFluidTicks()
            ));

        }

        return new SkeletonSlimeWorld(
                data.getName(),
                data.getLoader(),
                data.isReadOnly(),
                chunks,
                data.getExtraData(),
                data.getPropertyMap(),
                newVersion
        );
    }

    private CompoundBinaryTag convertPoiSections(CompoundBinaryTag poiChunkSections, int from, int to) {
        CompoundTag poiChunk = SlimeChunkConverter.createPoiChunkFromSlimeSections(poiChunkSections, from);
        CompoundTag fixed = (CompoundTag) fix(References.POI_CHUNK, poiChunk, from, to);
        return SlimeChunkConverter.getSlimeSectionsFromPoiCompound(fixed);
    }

    @Override
    public SlimeWorld applyDataFixers(SlimeWorld world) {
        return readFromData(world);
    }

    /**
     * Runs the vanilla DataFixer for {@code type} on {@code tag}, upgrading it from {@code from} to {@code to}.
     * Unlike the previously bundled spottedleaf dataconverter (which mutated in place), Mojang's DataFixer
     * returns a new value, so callers must use the returned tag.
     */
    private static Tag fix(DSL.TypeReference type, Tag tag, int from, int to) {
        return DataFixers.getDataFixer()
                .update(type, new Dynamic<>(NbtOps.INSTANCE, tag), from, to)
                .getValue();
    }

    /**
     * Upgrades every entry of the {@code "palette"} list of a section tag (block states are compounds,
     * biomes are strings) and writes the results back into the (mutable) list.
     */
    private static CompoundTag updatePalette(CompoundTag sectionTag, DSL.TypeReference type, int from, int to) {
        ListTag palette = sectionTag.getListOrEmpty("palette");
        for (int i = 0, len = palette.size(); i < len; i++) {
            palette.set(i, fix(type, palette.get(i), from, to));
        }
        return sectionTag;
    }

    private static CompoundBinaryTag convertAndBack(CompoundBinaryTag value, UnaryOperator<CompoundTag> fixer) {
        if (value == null) return null;

        net.minecraft.nbt.CompoundTag converted = (net.minecraft.nbt.CompoundTag) Converter.convertTag(value);
        net.minecraft.nbt.CompoundTag fixed = fixer.apply(converted);

        return Converter.convertTag(fixed);
    }

    @Override
    public CompoundBinaryTag convertChunkTo1_13(CompoundBinaryTag tag) {
        return convertChunk(tag, 1631);
    }

    @Override
    public CompoundBinaryTag convertChunk(CompoundBinaryTag globalTag, int to) {
        CompoundTag nmsTag = (CompoundTag) Converter.convertTag(globalTag);

        int version = nmsTag.getInt("DataVersion").orElseThrow();

        CompoundTag fixed = (CompoundTag) fix(References.CHUNK, nmsTag, version, to);

        return Converter.convertTag(fixed);
    }

    @Override
    public List<CompoundBinaryTag> convertEntities(List<CompoundBinaryTag> input, int from, int to) {
        List<CompoundBinaryTag> entities = new ArrayList<>(input.size());

        for (CompoundBinaryTag upgradeEntity : input) {
            entities.add(
                    convertAndBack(upgradeEntity, (tag) -> (CompoundTag) fix(References.ENTITY, tag, from, to))
            );
        }
        return entities;
    }

    @Override
    public List<CompoundBinaryTag> convertTileEntities(List<CompoundBinaryTag> input, int from, int to) {
        List<CompoundBinaryTag> blockEntities = new ArrayList<>(input.size());

        for (CompoundBinaryTag upgradeEntity : input) {
            blockEntities.add(
                    convertAndBack(upgradeEntity, (tag) -> (CompoundTag) fix(References.BLOCK_ENTITY, tag, from, to))
            );
        }
        return blockEntities;
    }

    @Override
    public ListBinaryTag convertBlockPalette(ListBinaryTag input, int from, int to) {
        ListTag nbtList = (ListTag) Converter.convertTag(input);

        for (int i = 0, len = nbtList.size(); i < len; ++i) {
            nbtList.set(i, fix(References.BLOCK_STATE, nbtList.get(i), from, to));
        }

        return Converter.convertTag(nbtList);
    }

    @Override
    public int getServerVersion() {
        return 0;
    }
}
