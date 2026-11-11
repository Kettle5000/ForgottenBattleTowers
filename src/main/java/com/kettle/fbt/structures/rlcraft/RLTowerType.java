package com.kettle.fbt.structures.rlcraft;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The tower types of 1.12 Battle Towers (AtomicStryker, as used in RLCraft 2.9.3 and Dregora), in the same order:
 * the ordinal matters, 1.12 uses it for the number of holes knocked into each floor.
 *
 * Block ids are the 1.12 ones translated to 1.20: double_stone_slab meta 0/1/3 are the smooth stone, sandstone and
 * cobblestone double slabs, stone_stairs are cobblestone stairs.
 */
public enum RLTowerType {
    // each type lists: wall, light, floor, stairs, roof railing (and railing posts, if different)
    // NULL is a placeholder at position 0 so the real types start at 1, like 1.12's numbers
    NULL(Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR),
    COBBLESTONE(Blocks.COBBLESTONE, Blocks.WALL_TORCH, Blocks.SMOOTH_STONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_WALL),
    COBBLESTONE_MOSSY(Blocks.MOSSY_COBBLESTONE, Blocks.WALL_TORCH, Blocks.SMOOTH_STONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_WALL),
    SANDSTONE(Blocks.SANDSTONE, Blocks.WALL_TORCH, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_WALL),
    ICE(Blocks.ICE, Blocks.AIR, Blocks.CLAY, Blocks.OAK_STAIRS, Blocks.STONE_BRICK_WALL),
    SMOOTHSTONE(Blocks.STONE, Blocks.WALL_TORCH, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.STONE_BRICK_WALL),
    NETHERRACK(Blocks.NETHERRACK, Blocks.GLOWSTONE, Blocks.SOUL_SAND, Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_BRICK_WALL),
    JUNGLE(Blocks.MOSSY_COBBLESTONE, Blocks.COBWEB, Blocks.DIRT, Blocks.JUNGLE_STAIRS, Blocks.MOSSY_COBBLESTONE_WALL),
    /**
     * Not a 1.12 type: the 12 floor obsidian tower (RLBattleTowerStructure "tower_type": "obsidian"). Crying obsidian is
     * its light; there are no obsidian stairs or walls, so the stairs are polished blackstone bricks (the closest dark
     * stone) and the railing is obsidian blocks. Its railing posts are crying obsidian, so the tower stands out from far
     * away as not an ordinary battle tower.
     */
    OBSIDIAN(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);

    // the tower's outer wall block
    public final Block wall;
    // the light on each floor's wall (torches; glowstone in the netherrack tower, cobwebs in the jungle tower)
    public final Block light;
    /** The floor block with its 1.12 metadata (a double slab for the slab floors). */
    private final Block floor;
    // the staircase between floors
    public final Block stairs;
    /** The railing on the roof (fbt's old towers had a stone brick wall railing); a wall matching the tower. */
    public final Block rail;
    /** The raised posts of the railing (the blocks above the ring): the rail block, or crying obsidian on the obsidian tower. */
    public final Block railPost;

    // most types: the railing posts are the same block as the railing
    RLTowerType(Block wall, Block light, Block floor, Block stairs, Block rail) {
        this(wall, light, floor, stairs, rail, rail);
    }

    RLTowerType(Block wall, Block light, Block floor, Block stairs, Block rail, Block railPost) {
        this.wall = wall;
        this.light = light;
        this.floor = floor;
        this.stairs = stairs;
        this.rail = rail;
        this.railPost = railPost;
    }

    /** Floor block with its metadata, used by buildFloorPiece in 1.12. */
    public BlockState floorState() {
        return this.floor instanceof SlabBlock ? this.floor.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE) : this.floor.defaultBlockState();
    }

    /**
     * The floor block's default state (1.12 metadata 0). Some floor blocks are placed this way in 1.12; for the slab
     * floors, metadata 0 of double_stone_slab is always the smooth stone double slab, whatever the tower type.
     */
    public BlockState floorDefaultState() {
        return this.floor instanceof SlabBlock ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE) : this.floor.defaultBlockState();
    }

    /** 1.12 compares Blocks, and all double_stone_slab metadata are the same Block, so any of these double slabs matches. */
    public boolean isFloorBlock(BlockState state) {
        if (this.floor instanceof SlabBlock) {
            return (state.is(Blocks.SMOOTH_STONE_SLAB) || state.is(Blocks.SANDSTONE_SLAB) || state.is(Blocks.COBBLESTONE_SLAB))
                    && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
        }
        return state.is(this.floor);
    }

    // the type named in a structure json's "tower_type" (any case), or null if there isn't one
    public static RLTowerType byName(String name) {
        for (RLTowerType t : values()) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    // the type for /fbt spawntower's number; an unknown number gives cobblestone
    public static RLTowerType byOrdinal(int i) {
        RLTowerType[] values = values();
        return i >= 0 && i < values.length ? values[i] : COBBLESTONE;
    }
}
