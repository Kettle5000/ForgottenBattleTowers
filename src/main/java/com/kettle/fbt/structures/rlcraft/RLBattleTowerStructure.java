package com.kettle.fbt.structures.rlcraft;

import com.kettle.fbt.structures.FBTStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A 1.12 RLCraft Battle Tower ({@link RLTowerBuilder}). The tower is centred in its chunk so that the whole tower, its
 * ground checks and its base fill stay inside that one chunk; it is then built in a single go, like 1.12 did, and the
 * tower type comes from the real ground blocks.
 *
 * Here the site gets the checks that can be made from the terrain shape alone (the 1.12 height and evenness checks).
 * The block checks run again when the tower is built.
 */
public class RLBattleTowerStructure extends Structure {
    // reads the structure's json file: the usual vanilla settings (biomes, step, ...) plus fbt's own options below
    public static final Codec<RLBattleTowerStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            settingsCodec(i),
            // "underground": true makes the deep tower (built down into the ground) instead of a surface tower
            Codec.BOOL.fieldOf("underground").orElse(false).forGetter(s -> s.underground),
            // Not 1.12: a fixed number of floors with spawners (0 = 1.12's height), and a tower type that replaces the
            // one picked from the ground (the site is still checked like 1.12 does). Used by fbt:obsidian_battletower.
            Codec.intRange(0, 30).fieldOf("floors").orElse(0).forGetter(s -> s.floors),
            Codec.STRING.fieldOf("tower_type").orElse("").forGetter(s -> s.towerType),
            // Not 1.12: the type when the ground matches no groundTypes line (fbt-common.toml); "" = a normal tower
            // (smooth stone or cobblestone). The Nether tower falls back to netherrack, the End tower to end.
            Codec.STRING.fieldOf("fallback_type").orElse("").forGetter(s -> s.fallbackType),
            // Not 1.12: built on a Nether cavern floor instead of the surface found by scanning up from y 51
            Codec.BOOL.fieldOf("nether").orElse(false).forGetter(s -> s.nether)
    ).apply(i, RLBattleTowerStructure::new));
    // the 9 spots (x, z offsets from the centre) whose ground 1.12 checks before building
    private static final int[][] CANDIDATES = {{4, -5}, {4, 0}, {4, 5}, {0, -5}, {0, 0}, {0, 5}, {-4, -5}, {-4, 0}, {-4, 5}};

    public final boolean underground;
    public final int floors;
    public final String towerType;
    public final String fallbackType;
    public final boolean nether;
    /** Air a Nether cavern needs above the tower's ground. */
    private static final int NETHER_MIN_AIR = 12;
    /** Lowest Nether ground (the lava sea is at y 31). */
    private static final int NETHER_MIN_Y = 33;

    // made by the codec with the values read from the json
    public RLBattleTowerStructure(StructureSettings settings, boolean underground, int floors, String towerType, String fallbackType, boolean nether) {
        super(settings);
        this.underground = underground;
        this.floors = floors;
        this.towerType = towerType;
        this.fallbackType = fallbackType;
        this.nether = nether;
    }

    @Override
    // decides whether a tower can go in this chunk (called for world generation and for /locate); empty = no tower here
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        // the tower's centre is the middle of the chunk
        int x = ctx.chunkPos().getMinBlockX() + 8;
        int z = ctx.chunkPos().getMinBlockZ() + 8;
        // The same scans 1.12 makes on the built world, run on the generated terrain column (stone, water and the
        // noise caves; ravines, surface blocks and features aren't in it yet), so few sites fail later at build time.
        NoiseColumn centre = column(ctx, x, z);
        if (this.nether) {
            return netherSite(ctx, centre, x, z);
        }
        // ground height at the centre
        int y = firstAirFrom51(centre, ctx);
        if (y <= 49) {
            return Optional.empty(); // 1.12 WorldGenHandler: y > 49
        }
        if (this.floors > 0 && y - 6 + 7 * (this.floors + 1) + 3 >= ctx.heightAccessor().getMaxBuildHeight()) {
            return Optional.empty(); // the fixed floor tower and its railing wouldn't fit under the build limit
        }
        if (this.floors == 0 && !this.underground && y - 6 >= RLTowerBuilder.SURFACE_TOP) {
            return Optional.empty(); // no floor would fit under the 1.12 tower top
        }
        // each of the 9 spots must be within 22 blocks of the centre's height, with 5 solid blocks (no air or lava) under it
        for (int[] c : CANDIDATES) {
            NoiseColumn column = column(ctx, x + c[0], z + c[1]);
            int h = firstAirFrom51(column, ctx);
            if (Math.abs(h - y) > 22) {
                return Optional.empty(); // "Uneven Surface"
            }
            for (int d = 1; d <= 5; d++) {
                BlockState below = column.getBlock(h - d);
                if (below.isAir() || below.is(Blocks.LAVA)) {
                    return Optional.empty(); // "Depth check - Banned Block or hole"
                }
            }
        }
        return Optional.of(stub(ctx, x, y, z));
    }

    // one piece covering this whole chunk, full height (the tower never leaves it), which builds the tower in postProcess
    private GenerationStub stub(GenerationContext ctx, int x, int y, int z) {
        long seed = ctx.random().nextLong(); // the tower's own seed, saved with it so it always builds the same
        BoundingBox box = new BoundingBox(ctx.chunkPos().getMinBlockX(), ctx.heightAccessor().getMinBuildHeight(), ctx.chunkPos().getMinBlockZ(),
                ctx.chunkPos().getMaxBlockX(), ctx.heightAccessor().getMaxBuildHeight() - 1, ctx.chunkPos().getMaxBlockZ());
        return new GenerationStub(new BlockPos(x, y, z), builder -> builder.addPiece(
                new RLBattleTowerPiece(box, x, y, z, this.underground, seed, this.floors, this.towerType, this.fallbackType, this.nether)));
    }

    /**
     * A Nether site: the lowest cavern floor at the centre (above the lava sea) with at least 12 blocks of air over it,
     * low enough that the tower's floors and railing stay under the bedrock roof; then 1.12's checks at the 9 spots,
     * each on the cavern floor nearest that height. The terrain here is the generator's noise; the real blocks (with the
     * Nether's surface and features) are checked again at build time, so some sites still fail then.
     */
    private Optional<GenerationStub> netherSite(GenerationContext ctx, NoiseColumn centre, int x, int z) {
        int floors = Math.max(1, this.floors);
        int maxY = netherMaxGround(floors); // room under the bedrock roof
        int y = Integer.MIN_VALUE;
        for (int h = NETHER_MIN_Y; h <= maxY && y == Integer.MIN_VALUE; h++) {
            if (solidDown(centre, h) && airAbove(centre, h, NETHER_MIN_AIR)) y = h;
        }
        if (y == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        for (int[] c : CANDIDATES) {
            NoiseColumn column = column(ctx, x + c[0], z + c[1]);
            // the cavern floor nearest the centre's height, within 22 blocks (like RLTowerBuilder.caveFloorNear)
            int h = Integer.MIN_VALUE;
            for (int d = 0; d <= 22 && h == Integer.MIN_VALUE; d++) {
                for (int t : d == 0 ? new int[]{y} : new int[]{y - d, y + d}) {
                    if (h == Integer.MIN_VALUE && column.getBlock(t + 1).isAir() && solidDown(column, t)) h = t;
                }
            }
            if (h == Integer.MIN_VALUE) {
                return Optional.empty(); // no floor near: a cliff or the lava sea
            }
            for (int d = 1; d <= 5; d++) {
                BlockState below = column.getBlock(h - d);
                if (below.isAir() || below.is(Blocks.LAVA)) {
                    return Optional.empty(); // "Depth check - Banned Block or hole"
                }
            }
        }
        return Optional.of(stub(ctx, x, y, z));
    }

    /**
     * The highest Nether ground a tower of this many floors fits on: its floors (7 blocks each, plus the roof floor,
     * starting 6 below the ground) and railing (3) must end under y 120, below the bedrock roof (y 123-127). The Nether's
     * build height is 256, so that can't be used. For 10 floors: y 46.
     */
    public static int netherMaxGround(int floors) {
        return 120 - (7 * (Math.max(1, floors) + 1) + 3 - 6);
    }

    private static boolean isSolid(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    // h and the 5 blocks under it are solid: real ground, not a thin ledge (like 1.12's depth check)
    private static boolean solidDown(NoiseColumn column, int h) {
        for (int d = 0; d <= 5; d++) {
            if (!isSolid(column.getBlock(h - d))) return false;
        }
        return true;
    }

    private static boolean airAbove(NoiseColumn column, int h, int blocks) {
        for (int d = 1; d <= blocks; d++) {
            if (!column.getBlock(h + d).isAir()) return false;
        }
        return true;
    }

    // the terrain's block column at x, z before surface blocks and features are added
    private static NoiseColumn column(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
    }

    /** 1.12's surface scan: from y 51 up to the first air block, minus one. */
    private static int firstAirFrom51(NoiseColumn column, GenerationContext ctx) {
        int max = ctx.heightAccessor().getMaxBuildHeight() - 1;
        int h = 50;
        while (++h < max && !column.getBlock(h).isAir()) {
        }
        return h - 1;
    }

    @Override
    // links this structure to its registered type, fbt:rlcraft_battletower
    public StructureType<?> type() {
        return FBTStructures.RLCRAFT_BATTLETOWER.get();
    }
}
