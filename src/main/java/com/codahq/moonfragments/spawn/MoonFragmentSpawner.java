package com.codahq.moonfragments.spawn;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.codahq.moonfragments.Config;
import com.codahq.moonfragments.MoonFragments;
import com.codahq.moonfragments.entity.FallingMoonFragment;
import com.codahq.moonfragments.registry.ModEntities;
import com.codahq.moonfragments.util.MoonTime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Where and when moon fragments fall (issue #1).
 * <p>
 * Each player at night rolls a small chance every tick, tuned so the average is {@code fragmentsPerNight} per player per
 * night. It's stateless, so a lucky night can exceed the average.
 */
public final class MoonFragmentSpawner {
    /** Natural terrain a fragment may land on (dirt, stone, sand…). Leaves, logs, water and built blocks are excluded. */
    public static final TagKey<Block> LANDING_SURFACES =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MoonFragments.MODID, "landing_surfaces"));
    /** No landing inside a village's bounding box or within this many blocks of it. */
    public static final int VILLAGE_BUFFER = 15;
    /** How far above the landing spot the fragment starts. */
    private static final int DROP_HEIGHT = 96;
    /** Fraction of fragments that fall straight down; the rest come in at an angle. */
    private static final float VERTICAL_CHANCE = 0.25F;
    private static final double MIN_TILT_DEGREES = 15.0;
    private static final double MAX_TILT_DEGREES = 50.0;
    /** Blocks per tick. */
    private static final double MIN_SPEED = 1.4;
    private static final double MAX_SPEED = 2.2;
    private static final int ATTEMPTS = 16;

    /** Called every server level tick. */
    public static void tick(ServerLevel level) {
        if (!canFallIn(level) || !MoonTime.isNight(level)) {
            return;
        }
        double chancePerTick = Config.FRAGMENTS_PER_NIGHT.get() / MoonTime.NIGHT_LENGTH;
        for (ServerPlayer player : level.players()) { // spawning adds entities, never players, so no copy needed
            if (!player.isSpectator() && level.random.nextDouble() < chancePerTick) {
                spawnNear(level, player.blockPosition(), level.random);
            }
        }
    }

    /** Only dimensions with a sky and a day/night cycle (the Overworld and similar). */
    public static boolean canFallIn(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime();
    }

    /**
     * Tries a few random spots between the configured min and max distance from {@code center}.
     * @return the spawned entity, or null if no valid spot was found.
     */
    public static @Nullable FallingMoonFragment spawnNear(ServerLevel level, BlockPos center, RandomSource random) {
        int min = Config.MIN_SPAWN_DISTANCE.getAsInt();
        int max = Math.max(min, Config.MAX_SPAWN_DISTANCE.getAsInt());
        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos landing = findLanding(level, x, z);
            if (landing != null) {
                return spawnAbove(level, landing);
            }
        }
        return null;
    }

    /**
     * The natural ground block a fragment would land on at this column, or null if the column is invalid:
     * not loaded/ticking, topped by trees, water or a non-natural block, or in or near a village.
     */
    public static @Nullable BlockPos findLanding(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.isLoaded(column) || !level.isPositionEntityTicking(column)) {
            return null;
        }
        // MOTION_BLOCKING includes leaves and fluids, so trees and water are seen as the top and rejected below.
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
        BlockState state = level.getBlockState(top);
        if (state.is(Blocks.SNOW)) { // thin snow layer over the real ground
            top = top.below();
            state = level.getBlockState(top);
        }
        if (!state.is(LANDING_SURFACES) || isNearVillage(level, top)) {
            return null;
        }
        return top;
    }

    /** True if {@code pos} is inside any village's bounding box grown by {@value #VILLAGE_BUFFER} blocks. */
    public static boolean isNearVillage(ServerLevel level, BlockPos pos) {
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Predicate<Structure> isVillage = structure -> structures.wrapAsHolder(structure).is(StructureTags.VILLAGE);
        // Structure references are stored in every chunk a structure overlaps, so checking the chunks within the
        // buffer distance finds any village whose (grown) box could contain pos.
        int minCx = (pos.getX() - VILLAGE_BUFFER) >> 4;
        int maxCx = (pos.getX() + VILLAGE_BUFFER) >> 4;
        int minCz = (pos.getZ() - VILLAGE_BUFFER) >> 4;
        int maxCz = (pos.getZ() + VILLAGE_BUFFER) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return true; // can't check an unloaded neighbour, so play safe
                }
                for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(cx, cz), isVillage)) {
                    BoundingBox box = start.getBoundingBox().inflatedBy(VILLAGE_BUFFER);
                    if (box.isInside(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Spawns a falling fragment aimed at {@code landing}. Most come in at an angle (up to {@value #MAX_TILT_DEGREES}°
     * from vertical); if an angled path would start in a non-ticking chunk or clip terrain or trees on the way down,
     * it falls straight down instead.
     */
    public static FallingMoonFragment spawnAbove(ServerLevel level, BlockPos landing) {
        RandomSource random = level.random;
        Vec3 target = new Vec3(landing.getX() + 0.5, landing.getY() + 1.0, landing.getZ() + 0.5);
        double height = Math.min(DROP_HEIGHT, level.getMaxY() - 2 - target.y);
        double speed = MIN_SPEED + random.nextDouble() * (MAX_SPEED - MIN_SPEED);

        Vec3 start = null;
        Vec3 direction = null;
        if (random.nextFloat() >= VERTICAL_CHANCE) {
            for (int i = 0; i < 4 && start == null; i++) {
                double tilt = Math.toRadians(MIN_TILT_DEGREES + random.nextDouble() * (MAX_TILT_DEGREES - MIN_TILT_DEGREES));
                double yaw = random.nextDouble() * Math.PI * 2.0;
                Vec3 dir = new Vec3(Math.sin(tilt) * Math.cos(yaw), -Math.cos(tilt), Math.sin(tilt) * Math.sin(yaw));
                Vec3 from = target.subtract(dir.scale(height / Math.cos(tilt)));
                if (isClearPath(level, from, target)) {
                    start = from;
                    direction = dir;
                }
            }
        }
        if (start == null) {
            start = target.add(0.0, height, 0.0);
            direction = new Vec3(0.0, -1.0, 0.0);
        }

        FallingMoonFragment fragment = new FallingMoonFragment(ModEntities.FALLING_MOON_FRAGMENT.get(), level);
        fragment.setPos(start.x, start.y, start.z);
        fragment.startFalling(direction.scale(speed));
        level.addFreshEntity(fragment);
        return fragment;
    }

    /** The start point must be ticking (so the entity moves) and the line down must reach the target unobstructed. */
    private static boolean isClearPath(ServerLevel level, Vec3 from, Vec3 target) {
        BlockPos startPos = BlockPos.containing(from);
        if (!level.isLoaded(startPos) || !level.isPositionEntityTicking(startPos)) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(from, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 1.5 * 1.5;
    }

    private MoonFragmentSpawner() {
    }
}
