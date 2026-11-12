package com.kettle.fbt.structures.rlcraft;

import com.kettle.fbt.structures.FBTStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Builds one {@link RLBattleTowerStructure}. The tower fits in one chunk, so this runs once per tower. */
public class RLBattleTowerPiece extends StructurePiece {
    /** -Dfbt.towerDebug=true logs each tower's site check. */
    private static final boolean DEBUG = Boolean.getBoolean("fbt.towerDebug");
    // what the piece remembers about its tower: the centre, which kind, its seed, fixed floors and forced type
    private final int x, y, z;
    private final boolean underground;
    private final long seed;
    private final int floors;
    private final String towerType;
    private final String fallbackType;
    private final boolean nether;

    // a new piece, made by RLBattleTowerStructure when the site is picked
    public RLBattleTowerPiece(BoundingBox box, int x, int y, int z, boolean underground, long seed, int floors, String towerType,
                              String fallbackType, boolean nether) {
        super(FBTStructures.RLCRAFT_BATTLETOWER_PIECE.get(), 0, box);
        this.x = x;
        this.y = y;
        this.z = z;
        this.underground = underground;
        this.seed = seed;
        this.floors = floors;
        this.towerType = towerType;
        this.fallbackType = fallbackType;
        this.nether = nether;
    }

    // loads a piece back from saved chunk data (a tower whose chunk wasn't built yet when the world was saved)
    public RLBattleTowerPiece(CompoundTag tag) {
        super(FBTStructures.RLCRAFT_BATTLETOWER_PIECE.get(), tag);
        this.x = tag.getInt("TX");
        this.y = tag.getInt("TY");
        this.z = tag.getInt("TZ");
        this.underground = tag.getBoolean("Underground");
        this.seed = tag.getLong("Seed");
        this.floors = tag.getInt("Floors");
        this.towerType = tag.getString("TowerType");
        this.fallbackType = tag.getString("FallbackType");
        this.nether = tag.getBoolean("Nether");
    }

    @Override
    // saves the same values, so the tower builds the same after a reload
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("TX", this.x);
        tag.putInt("TY", this.y);
        tag.putInt("TZ", this.z);
        tag.putBoolean("Underground", this.underground);
        tag.putLong("Seed", this.seed);
        tag.putInt("Floors", this.floors);
        tag.putString("TowerType", this.towerType);
        tag.putString("FallbackType", this.fallbackType);
        tag.putBoolean("Nether", this.nether);
    }

    @Override
    // builds the tower. Called for each chunk the piece's box touches, which is only the tower's own chunk
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        // only build from the chunk that holds the tower's centre
        if (!chunkBox.isInside(new BlockPos(this.x, chunkBox.minY(), this.z))) {
            if (DEBUG) org.slf4j.LoggerFactory.getLogger("fbt").info("tower {} {}: chunk box {} doesn't hold the centre", this.x, this.z, chunkBox);
            return;
        }
        // same seed every time, so the tower always comes out the same
        RandomSource rand = RandomSource.create(this.seed);
        // 1.12 WorldGenHandler takes the real surface here (first air above y 51), then picks and checks the tower type.
        // (The Nether tower uses the cavern floor its site was picked on instead.)
        int jy = this.nether ? RLTowerBuilder.caveFloorNear(level, this.x, this.y, this.z) : RLTowerBuilder.getSpawnHeight(level, this.x, this.z);
        if (jy == Integer.MIN_VALUE || !this.nether && jy <= 49 || this.nether && jy > RLBattleTowerStructure.netherMaxGround(this.floors)) {
            if (DEBUG) org.slf4j.LoggerFactory.getLogger("fbt").info("tower {} {}: ground y {} too low", this.x, this.z, jy);
            return;
        }
        // pick the tower type from the ground (sand = sandstone, snow = ice, ...); null if the site fails 1.12's block checks
        RLTowerType type = RLTowerBuilder.chooseType(level, rand, this.x, jy, this.z, RLTowerType.byName(this.fallbackType), this.nether);
        if (DEBUG) org.slf4j.LoggerFactory.getLogger("fbt").info("tower {} {} y {}: type {}, reason {}", this.x, this.z, jy, type, RLTowerBuilder.lastFail);
        if (type == null) {
            return; // 1.12 rejects this site
        }
        // "tower_type" in the structure json (obsidian) overrides the type picked from the ground
        RLTowerType forced = this.towerType.isEmpty() ? null : RLTowerType.byName(this.towerType);
        if (forced != null) {
            type = forced; // the site passed 1.12's checks; the structure asks for its own tower type
        }
        // build the whole tower now
        RLTowerBuilder.generate(level, rand, this.x, jy, this.z, type, this.underground, this.floors);
    }
}
