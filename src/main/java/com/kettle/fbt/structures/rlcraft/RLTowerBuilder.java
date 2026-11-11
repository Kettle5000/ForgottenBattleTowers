package com.kettle.fbt.structures.rlcraft;

import com.kettle.fbt.ForgottenBattleTowersEntities;
import com.kettle.fbt.BattleTowerGolemEntity;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * 1.12 Battle Towers' tower builder (atomicstryker.battletowers.common.AS_WorldGenTower, the version RLCraft 2.9.3 and
 * Dregora ship), translated to 1.20.1. Every block is placed where 1.12 places it; the comments name the 1.12 parts.
 *
 * Differences that 1.20 forces:
 * - Randomness comes from the RandomSource passed in (seeded per tower), not the world's random, so a tower comes out
 *   the same however world generation is scheduled.
 * - Chests get their fbt loot table (fbt:chests/floorN, last_floor) instead of 1.12's item lists; those tables are the
 *   pack's copies of the 1.12 floor lists.
 * - Snow and ice are placed by world generation after structures, so the tower type check asks the biome whether the
 *   spot will get snow instead of looking for the snow layer.
 */
public final class RLTowerBuilder {
    /** Top of every surface tower (1.12 maximumHeight). */
    public static final int SURFACE_TOP = 120;
    // the 9 ground spots checked around the centre (x, z offsets)
    private static final int[][] CANDIDATES = {{4, -5}, {4, 0}, {4, 5}, {0, -5}, {0, 0}, {0, 5}, {-4, -5}, {-4, 0}, {-4, 5}};
    // the most a spot's ground may differ in height from the centre's
    private static final int MAX_HOLE_DEPTH_IN_BASE = 22;
    /** Why the last chooseType call rejected its site (for the debug log, like 1.12's failState). */
    public static volatile String lastFail = "";

    private RLTowerBuilder() {
    }

    // ---------------------------------------------------------------- tower type (1.12 getChosenTowerOrdinal)

    /** Returns the tower type for this spot, or null when 1.12 would reject the site (uneven, banned block, hole). */
    public static RLTowerType chooseType(WorldGenLevel level, RandomSource random, int ix, int jy, int kz) {
        // count what the ground is at the 9 spots, and reject the site if any spot fails a check
        int countWater = 0, countSand = 0, countSnow = 0, countFoliage = 0, countElse = 0;
        for (int[] pair : CANDIDATES) {
            int x = ix + pair[0], z = kz + pair[1];
            // the top ground block at this spot, and the block above it
            int checkBlockY = getSurfaceBlockHeight(level, x, z);
            BlockPos surface = new BlockPos(x, checkBlockY, z);
            BlockState id = level.getBlockState(surface);
            BlockState above = level.getBlockState(surface.above());
            // sort the spot: snowy, sandy, water, other ground, or plants/trees
            if (above.is(Blocks.SNOW) || id.is(Blocks.ICE) || willGetSnowOrIce(level, surface, id, above)) {
                countSnow++;
            } else if (id.is(Blocks.SAND) || id.is(Blocks.RED_SAND) || id.is(Blocks.SANDSTONE)) {
                countSand++;
            } else if (id.is(Blocks.WATER)) {
                countWater++;
            } else if (!id.is(BlockTags.LEAVES) && !id.is(Blocks.LILY_PAD) && !id.is(BlockTags.LOGS)) {
                countElse++;
            } else {
                countFoliage++;
            }
            // too far above or below the centre: the ground is too uneven
            if (Math.abs(checkBlockY - jy) > MAX_HOLE_DEPTH_IN_BASE) {
                lastFail = "Uneven Surface, diff " + Math.abs(checkBlockY - jy);
                return null;
            }
            // no flowers, mushrooms, cactus, pumpkins or lava in the 3 blocks above the ground
            for (int y = 1; y <= 3; y++) {
                if (isBanned(level.getBlockState(surface.above(y)))) {
                    lastFail = "Surface banned block " + level.getBlockState(surface.above(y)) + " at " + surface.above(y);
                    return null;
                }
            }
            // and 5 solid, non-banned blocks below it (no hole or cave right under the tower)
            for (int y = 1; y <= 5; y++) {
                BlockState below = level.getBlockState(surface.below(y));
                if (below.isAir() || isBanned(below)) {
                    lastFail = "Depth check - banned block or hole " + below + " at " + surface.below(y);
                    return null;
                }
            }
        }
        lastFail = "";
        // the most common kind of ground picks the type: sand -> sandstone, snow -> ice, water or plants -> mossy;
        // otherwise 1 in 10 netherrack, then 1 in 5 smooth stone, else cobblestone
        int[] nums = {countWater, countSnow, countSand, countFoliage, countElse};
        Arrays.sort(nums);
        int result = nums[nums.length - 1];
        if (countSand == result) {
            return RLTowerType.SANDSTONE;
        } else if (countSnow == result) {
            return RLTowerType.ICE;
        } else if (countWater == result || countFoliage == result) {
            return RLTowerType.COBBLESTONE_MOSSY;
        } else if (random.nextInt(10) == 0) {
            return RLTowerType.NETHERRACK;
        }
        return random.nextInt(5) == 0 ? RLTowerType.SMOOTHSTONE : RLTowerType.COBBLESTONE;
    }

    /** What 1.20's freeze_top_layer feature will do to this column later: snow on top, or water freezing to ice. */
    private static boolean willGetSnowOrIce(WorldGenLevel level, BlockPos surface, BlockState id, BlockState above) {
        if (!above.isAir()) {
            return false;
        }
        return level.getBiome(surface.above()).value().coldEnoughToSnow(surface.above());
    }

    /** 1.12 AS_WorldGenTower.getSurfaceBlockHeight: from y 51 up to the first air or foliage block. */
    private static int getSurfaceBlockHeight(WorldGenLevel level, int x, int z) {
        int h = 50;
        BlockState state;
        do {
            h++;
            state = level.getBlockState(new BlockPos(x, h, z));
        } while (!state.isAir() && !isFoliage(state) && h < level.getMaxBuildHeight());
        return h - 1;
    }

    /** 1.12 WorldGenHandler.getSurfaceBlockHeight: from y 51 up to the first air block. */
    public static int getSpawnHeight(WorldGenLevel level, int x, int z) {
        int h = 50;
        while (!level.getBlockState(new BlockPos(x, ++h, z)).isAir() && h < level.getMaxBuildHeight()) {
        }
        return h - 1;
    }

    // plants and tree blocks, which the ground scan treats as the top of the ground
    private static boolean isFoliage(BlockState s) {
        return s.is(Blocks.SNOW) || s.is(Blocks.GRASS) || s.is(Blocks.FERN) || s.is(Blocks.DEAD_BUSH) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
    }

    /** 1.12 isBuildableBlockID: stone, grass, sand, sandstone, gravel, dirt (with their 1.12 metadata variants). */
    private static boolean isBuildable(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.GRANITE) || s.is(Blocks.DIORITE) || s.is(Blocks.ANDESITE)
                || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.CHISELED_SANDSTONE) || s.is(Blocks.CUT_SANDSTONE)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL);
    }

    /** 1.12 isBannedBlockID: flowers, mushroom blocks, cactus, pumpkin, lava. */
    private static boolean isBanned(BlockState s) {
        return s.is(Blocks.DANDELION) || s.is(BlockTags.SMALL_FLOWERS) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                || s.is(Blocks.CACTUS) || s.is(Blocks.PUMPKIN) || s.is(Blocks.LAVA);
    }

    // ---------------------------------------------------------------- tower (1.12 generate)

    // the normal 1.12 tower (no fixed floor count)
    public static void generate(WorldGenLevel world, RandomSource random, int ix, int jy, int kz, RLTowerType towerChosen, boolean underground) {
        generate(world, random, ix, jy, kz, towerChosen, underground, 0);
    }

    /**
     * @param spawnerFloors 0 for the 1.12 tower (surface towers go up to y 120). Otherwise the tower gets exactly this
     *                      many floors with spawners and chests, plus the roof with the golem, from the same start height.
     */
    public static void generate(WorldGenLevel world, RandomSource random, int ix, int jy, int kz, RLTowerType towerChosen, boolean underground,
                                int spawnerFloors) {
        // where the tower starts and ends: surface towers start 6 below the ground and go up to y 120;
        // underground (deep) towers start 70 below the ground (not below y 15) and end 7 above it;
        // a fixed floor count gives that many floors plus the roof floor
        Block towerWallBlockID = towerChosen.wall;
        Block towerLightBlockID = towerChosen.light;
        int startingHeight = underground ? Math.max(jy - 70, 15) : jy - 6;
        int maximumHeight = spawnerFloors > 0 ? startingHeight + 7 * (spawnerFloors + 1) : underground ? jy + 7 : SURFACE_TOP;
        // floor number (1 = bottom); topFloor is set on the last floor; roofHeight ends as the last floor's base
        int floor = 1;
        boolean topFloor = false;
        int roofHeight = startingHeight;

        // one floor per loop: each is 7 blocks tall
        for (int builderHeight = startingHeight; builderHeight < maximumHeight; builderHeight += 7) {
            roofHeight = builderHeight;
            if (builderHeight + 7 >= maximumHeight) {
                topFloor = true;
            }

            // each of the floor's 7 layers. The bottom floor skips its lowest 4 layers (that part is filled to the ground instead)
            for (int floorIterator = 0; floorIterator < 7; floorIterator++) {
                if (floor == 1 && floorIterator < 4) {
                    floorIterator = 4;
                }

                // the 14x14 area around the centre. The z rows below give the tower's rounded outline (see TOP_OUTLINE):
                // wall blocks on the edge, the floor on layer 5, air inside. Rows z -6 and -5 are the stairwell on the north side.
                for (int xIterator = -7; xIterator < 7; xIterator++) {
                    for (int zIterator = -7; zIterator < 7; zIterator++) {
                        int iCurrent = xIterator + ix;
                        int jCurrent = floorIterator + builderHeight;
                        int zCurrent = zIterator + kz;
                        // north edge of the stairwell
                        if (zIterator == -7) {
                            if (xIterator > -5 && xIterator < 4) {
                                buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                            }
                        } else if (zIterator != -6 && zIterator != -5) {
                            // everything except the stairwell rows
                            if (zIterator != -4 && zIterator != -3 && zIterator != 2 && zIterator != 3) {
                                // middle rows (z -2 to 1): the widest part, with the east and west walls at x -7 and 6
                                if (zIterator > -3 && zIterator < 2) {
                                    if (xIterator != -7 && xIterator != 6) {
                                        if (xIterator > -7 && xIterator < 6) {
                                            if (floorIterator == 5) {
                                                buildFloorPiece(world, iCurrent, jCurrent, zCurrent, towerChosen);
                                            } else {
                                                setAir(world, iCurrent, jCurrent, zCurrent);
                                            }
                                        }
                                    } else if (floorIterator >= 0 && floorIterator <= 3 && (xIterator == -7 || xIterator == 6) && !underground
                                            && (zIterator == -1 || zIterator == 0)) {
                                        setAir(world, iCurrent, jCurrent, zCurrent); // doorways
                                    } else {
                                        buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                                    }
                                } else if (zIterator == 4) {
                                    // z 4 to 6: the south side, narrowing in steps
                                    if (xIterator == -5 || xIterator == 4) {
                                        buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                                    } else if (xIterator > -5 && xIterator < 4) {
                                        if (floorIterator == 5) {
                                            buildFloorPiece(world, iCurrent, jCurrent, zCurrent, towerChosen);
                                        } else {
                                            setAir(world, iCurrent, jCurrent, zCurrent);
                                        }
                                    }
                                } else if (zIterator == 5) {
                                    if (xIterator == -4 || xIterator == -3 || xIterator == 2 || xIterator == 3) {
                                        buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                                    } else if (xIterator > -3 && xIterator < 2) {
                                        if (floorIterator == 5) {
                                            buildFloorPiece(world, iCurrent, jCurrent, zCurrent, towerChosen);
                                        } else {
                                            buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                                        }
                                    }
                                } else if (zIterator == 6 && xIterator > -3 && xIterator < 2) {
                                    buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                                }
                            } else if (xIterator == -6 || xIterator == 5) {
                                // z -4, -3, 2, 3: walls at x -6 and 5, floor or air between
                                buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                            } else if (xIterator > -6 && xIterator < 5) {
                                if (floorIterator == 5) {
                                    buildFloorPiece(world, iCurrent, jCurrent, zCurrent, towerChosen);
                                } else if (!world.getBlockState(new BlockPos(iCurrent, jCurrent, zCurrent)).is(Blocks.CHEST)) {
                                    setAir(world, iCurrent, jCurrent, zCurrent); // keeps the chests of the floor below
                                }
                            }
                        } else if (xIterator == -5 || xIterator == 4) {
                            // stairwell rows (z -6 and -5): the stairwell's east and west walls
                            buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                        } else if (zIterator == -6) {
                            // z -6: the stairs. Each layer's step moves one block east, so the 7 layers make one flight per floor
                            // (the deep tower has no stairs on its bottom floor)
                            if (xIterator == (floorIterator + 1) % 7 - 3) {
                                if (!underground || floor != 1) {
                                    set(world, iCurrent, jCurrent, zCurrent, stairs(towerChosen));
                                }
                                if (floorIterator == 5) {
                                    set(world, iCurrent - 7, jCurrent, zCurrent, towerChosen.floorDefaultState());
                                }
                                if (floorIterator == 6 && topFloor) {
                                    buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                                }
                            } else if (xIterator < 4 && xIterator > -5) {
                                setAir(world, iCurrent, jCurrent, zCurrent);
                            }
                        } else if (zIterator == -5 && xIterator > -5 && xIterator < 5) {
                            // z -5: the wall between the stairwell and the room, with openings at its two ends
                            if (floorIterator != 0 && floorIterator != 6 || xIterator != -4 && xIterator != 3) {
                                if (floorIterator != 5 || xIterator != 3 && xIterator != -4) {
                                    buildWallPiece(world, iCurrent, jCurrent, zCurrent, towerWallBlockID, floor, floorIterator);
                                } else {
                                    buildFloorPiece(world, iCurrent, jCurrent, zCurrent, towerChosen);
                                }
                            } else {
                                setAir(world, iCurrent, jCurrent, zCurrent);
                            }
                        }
                    }
                }
            }

            // 1.12 puts two extra wall blocks in the stairwell on the second floor
            if (floor == 2) {
                set(world, ix + 3, builderHeight, kz - 5, towerWallBlockID.defaultBlockState());
                set(world, ix + 3, builderHeight - 1, kz - 5, towerWallBlockID.defaultBlockState());
            }

            // the golem stands on the top floor of a surface tower, or the bottom floor of a deep tower
            if (!underground && topFloor || underground && floor == 1) {
                if (towerChosen != RLTowerType.NULL) {
                    // The obsidian tower's golem stands above its blight and infernal floors and is one as well.
                    spawnGolem(world, random, ix + 0.5, builderHeight + 6, kz + 0.5, towerChosen == RLTowerType.OBSIDIAN);
                }
            } else if (towerChosen == RLTowerType.OBSIDIAN && spawnerFloors > 0 && floor >= spawnerFloors - 1) {
                // The obsidian tower's last two floors: armored skeletons, then armored wither skeletons, all blights and infernals.
                EntityType<?> elite = floor == spawnerFloors ? EntityType.WITHER_SKELETON : EntityType.SKELETON;
                placeEliteSpawner(world, random, new BlockPos(ix + 2, builderHeight + 6, kz + 2), elite);
                placeEliteSpawner(world, random, new BlockPos(ix - 3, builderHeight + 6, kz + 2), elite);
            } else if (towerChosen != RLTowerType.NULL) {
                // every other floor gets two mob spawners
                // the obsidian tower's spawners ignore light (see anyLightSpawnRules)
                boolean anyLight = towerChosen == RLTowerType.OBSIDIAN;
                placeSpawner(world, random, new BlockPos(ix + 2, builderHeight + 6, kz + 2), anyLight);
                placeSpawner(world, random, new BlockPos(ix - 3, builderHeight + 6, kz + 2), anyLight);
            } else {
                // a NULL tower (never built normally) gets no spawners
                setAir(world, ix + 2, builderHeight + 6, kz + 2);
                setAir(world, ix - 3, builderHeight + 6, kz + 2);
            }

            // two floor blocks under where the chests go
            set(world, ix, builderHeight + 6, kz + 3, towerChosen.floorDefaultState());
            set(world, ix - 1, builderHeight + 6, kz + 3, towerChosen.floorDefaultState());
            // 1.12: a surface tower with too little room for 9 floors (started within 56 blocks of y 120) skips floor number 2,
            // so its loot and holes are those of one floor higher
            if (spawnerFloors == 0 && builderHeight + 56 >= SURFACE_TOP && floor == 1) {
                floor = 2;
            }

            // Two chests (a double chest), facing north.
            ResourceLocation loot = null;
            if (towerChosen != RLTowerType.NULL) {
                // the loot stage: on a surface tower the floor number (the top floor is 10);
                // on a deep tower it counts the other way, since its golem and best loot are at the bottom
                int stage = !underground ? (topFloor ? 10 : floor) : (floor == 1 ? 10 : Math.abs(11 - floor));
                loot = lootTableForStage(stage);
                // The obsidian tower's top chest has its own table (the pack fills it like the mega battle
                // tower's boss floor chests, and Locks puts a lock on it by this table id).
                if (towerChosen == RLTowerType.OBSIDIAN && topFloor && !underground) {
                    loot = new ResourceLocation("fbt", "chests/obsidian_last_floor");
                }
            }
            // place both halves of the double chest and give each its loot table (filled when a player first opens it)
            for (int chestlength = 0; chestlength < 2; chestlength++) {
                BlockPos chestPos = new BlockPos(ix - chestlength, builderHeight + 7, kz + 3);
                // 1.12 joins the two chests itself; in 1.20 the halves are set: the west one (x - 1) is the left half.
                set(world, chestPos.getX(), chestPos.getY(), chestPos.getZ(), Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, chestlength == 1 ? ChestType.LEFT : ChestType.RIGHT));
                if (loot != null) {
                    RandomizableContainerBlockEntity.setLootTable(world, random, chestPos, loot);
                }
            }

            // four lights on each floor: torches facing south, or the type's light block (glowstone, cobweb, ...)
            if (towerLightBlockID == Blocks.WALL_TORCH) {
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH);
                set(world, ix + 3, builderHeight + 2, kz - 6, torch);
                set(world, ix - 4, builderHeight + 2, kz - 6, torch);
                set(world, ix + 1, builderHeight + 2, kz - 4, torch);
                set(world, ix - 2, builderHeight + 2, kz - 4, torch);
            } else {
                BlockState light = towerLightBlockID.defaultBlockState();
                set(world, ix + 3, builderHeight + 2, kz - 6, light);
                set(world, ix - 4, builderHeight + 2, kz - 6, light);
                set(world, ix + 1, builderHeight + 2, kz - 4, light);
                set(world, ix - 2, builderHeight + 2, kz - 4, light);
            }

            // Holes knocked into the floor: more on higher floors and for later tower types.
            if (towerChosen != RLTowerType.NULL) {
                // (The obsidian tower's floors go past 1.12's nine, so its holes stop at 1.12's most: 35 tries.)
                // number of tries at knocking a hole in the floor
                int holes = floor * 4 + towerChosen.ordinal() - 8;
                if (towerChosen == RLTowerType.OBSIDIAN) {
                    holes = Math.min(holes, 35);
                }
                // each try picks a random spot on the floor (not the top floor, and the condition keeps it out of the stairwell)
                // and only removes it if it is floor block and isn't under a spawner
                for (int l3 = 0; l3 < holes && !topFloor; l3++) {
                    int k4 = 5 - random.nextInt(12);
                    int k5 = builderHeight + 5;
                    int j6 = 5 - random.nextInt(10);
                    if (j6 >= -2 || k4 >= 4 || k4 <= -5 || k4 == 1 || k4 == -2) {
                        k4 += ix;
                        j6 += kz;
                        BlockPos hole = new BlockPos(k4, k5, j6);
                        if (towerChosen.isFloorBlock(world.getBlockState(hole)) && !world.getBlockState(hole.above()).is(Blocks.SPAWNER)) {
                            setAir(world, k4, k5, j6);
                        }
                    }
                }
            }

            // next floor up
            floor++;
        }

        // after the last floor: the railing around the roof, and on the obsidian tower its glowing stripes
        if (towerChosen != RLTowerType.NULL) {
            buildRailing(world, ix, roofHeight + 7, kz, towerChosen.rail, towerChosen.railPost);
        }
        if (towerChosen == RLTowerType.OBSIDIAN) {
            markTallestColumns(world, ix, roofHeight + 7, kz); // from the railing ring block down
        }
    }

    // ---------------------------------------------------------------- roof railing (not 1.12: fbt's old towers)

    /**
     * The top floor's outline at its last layer (builderHeight + 6), x -7..6 west to east, one row per z -7..6 north to
     * south: '#' is the tower's wall, '.' inside the tower, ' ' outside. That wall ring is the roof's 1 block parapet.
     */
    private static final String[] TOP_OUTLINE = {
            "   ########   ",
            "  #.#......#  ",
            "  #.######.#  ",
            " #..........# ",
            " #..........# ",
            "#............#",
            "#............#",
            "#............#",
            "#............#",
            " #..........# ",
            " #..........# ",
            "  #........#  ",
            "   ########   ",
            "     ####     "};

    /**
     * fbt's old towers had a stone brick wall railing around the roof, with posts at the corners 2 high and at the ends
     * of the long sides 3 high. This puts the same on the 1.12 roof: a wall on every outer parapet block, a second at
     * each outer corner of the outline, and a third at both ends of the four long straight sides.
     */
    private static void buildRailing(WorldGenLevel world, int ix, int y, int kz, Block rail, Block post) {
        java.util.List<BlockPos> placed = new java.util.ArrayList<>();
        for (int z = -7; z < 7; z++) {
            for (int x = -7; x < 7; x++) {
                if (!outlineIs(x, z, '#') || !touchesOutside(x, z)) {
                    continue; // only the outer ring, not the stairwell's walls
                }
                int height = 1;
                boolean outsideX = outlineIs(x - 1, z, ' ') || outlineIs(x + 1, z, ' ');
                boolean outsideZ = outlineIs(x, z - 1, ' ') || outlineIs(x, z + 1, ' ');
                if (outsideX && outsideZ) {
                    height = isEndOfLongSide(x, z) ? 3 : 2;
                }
                for (int h = 0; h < height; h++) {
                    BlockPos p = new BlockPos(ix + x, y + h, kz + z);
                    world.setBlock(p, (h == 0 ? rail : post).defaultBlockState(), 2);
                    placed.add(p);
                }
            }
        }
        // Walls placed by world generation don't connect on their own; give each the shape its neighbours ask for.
        for (BlockPos p : placed) {
            BlockState state = world.getBlockState(p);
            BlockState shaped = Block.updateFromNeighbourShapes(state, world, p);
            if (shaped != state) {
                world.setBlock(p, shaped, 2);
            }
        }
    }

    // what the outline has at x, z (anywhere outside the 14x14 area counts as outside)
    private static boolean outlineIs(int x, int z, char c) {
        if (x < -7 || x > 6 || z < -7 || z > 6) {
            return c == ' ';
        }
        return TOP_OUTLINE[z + 7].charAt(x + 7) == c;
    }

    // true if any of the 8 blocks around x, z is outside the tower
    private static boolean touchesOutside(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (outlineIs(x + dx, z + dz, ' ')) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The obsidian tower's warning: under each of its tallest (3 high, crying obsidian) railing posts, the wall column is
     * crying obsidian all the way down, through the ground fill, so the tower shows glowing stripes from far away. It
     * replaces only obsidian, from the railing ring down to the first block that isn't (the natural ground).
     */
    private static void markTallestColumns(WorldGenLevel world, int ix, int topY, int kz) {
        for (int z = -7; z < 7; z++) {
            for (int x = -7; x < 7; x++) {
                if (!outlineIs(x, z, '#') || !isEndOfLongSide(x, z)) {
                    continue;
                }
                for (int y = topY; y > world.getMinBuildHeight(); y--) {
                    BlockPos p = new BlockPos(ix + x, y, kz + z);
                    if (!world.getBlockState(p).is(Blocks.OBSIDIAN)) {
                        break;
                    }
                    world.setBlock(p, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                }
            }
        }
    }

    /** The two end blocks of the north (z -7), south (z 6), west (x -7) and east (x 6) sides. */
    private static boolean isEndOfLongSide(int x, int z) {
        return z == -7 && (x == -4 || x == 3) || z == 6 && (x == -2 || x == 1)
                || x == -7 && (z == -2 || z == 1) || x == 6 && (z == -2 || z == 1);
    }

    /** fbt loot tables for the 1.12 floor stages: 1-9 are the floors, 10 (and anything above 9) is the top floor. */
    private static ResourceLocation lootTableForStage(int stage) {
        if (stage < 1) {
            stage = 1;
        }
        return stage >= 10 ? new ResourceLocation("fbt", "chests/last_floor") : new ResourceLocation("fbt", "chests/floor" + stage);
    }

    /** 1.12 getMobType. Its switch falls through from spider into cave spider, so it's 3 skeleton : 4 zombie : 3 cave spider. */
    private static EntityType<?> getMobType(RandomSource random) {
        switch (random.nextInt(10)) {
            case 0, 1, 2:
                return EntityType.SKELETON;
            case 3, 4, 5, 6:
                return EntityType.ZOMBIE;
            case 7, 8, 9:
                return EntityType.CAVE_SPIDER;
            default:
                return EntityType.ZOMBIE;
        }
    }

    /**
     * Spawn rules for the obsidian tower's spawners: any block light and any sky light (custom_spawn_rules with the
     * full 0-15 range).
     * <p>
     * A 1.20.1 spawner otherwise uses the mob's natural spawn rule, and a monster then needs block light 0 where it
     * appears. The obsidian tower is lit only by crying obsidian (its floor lights and the stripes down every wall,
     * light 10), which reaches every spot, so its spawners never spawned (tested: 0 mobs in 90 s, night or day).
     * With these rules light never stops them, by day too, and torches placed by players don't either: the only
     * way to stop one is to break it. They also replace the mob's own placement checks, so a mob can appear over a
     * hole in the floor and drop to the floor below. Peaceful still stops them. Other towers keep vanilla's rule.
     */
    private static net.minecraft.nbt.CompoundTag anyLightSpawnRules() {
        net.minecraft.nbt.CompoundTag rules = new net.minecraft.nbt.CompoundTag();
        rules.put("block_light_limit", lightRange(0, 15));
        rules.put("sky_light_limit", lightRange(0, 15));
        return rules;
    }

    // {min_inclusive: min, max_inclusive: max}, the format of a light limit in custom_spawn_rules
    private static net.minecraft.nbt.CompoundTag lightRange(int min, int max) {
        net.minecraft.nbt.CompoundTag range = new net.minecraft.nbt.CompoundTag();
        range.putInt("min_inclusive", min);
        range.putInt("max_inclusive", max);
        return range;
    }

    // a normal floor spawner with a random 1.12 mob; anyLight = spawn at any light level (obsidian tower)
    private static void placeSpawner(WorldGenLevel world, RandomSource random, BlockPos pos, boolean anyLight) {
        set(world, pos.getX(), pos.getY(), pos.getZ(), Blocks.SPAWNER.defaultBlockState());
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SpawnerBlockEntity spawner)) {
            return;
        }
        EntityType<?> mob = getMobType(random);
        if (!anyLight) {
            spawner.setEntityId(mob, random); // vanilla spawner, vanilla light rule
            return;
        }
        // SpawnData = {entity: {id: <mob>}, custom_spawn_rules: {...}}; no SpawnPotentials, so it keeps this mob
        net.minecraft.nbt.CompoundTag entity = new net.minecraft.nbt.CompoundTag();
        entity.putString("id", net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob).toString());
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.put("entity", entity);
        data.put("custom_spawn_rules", anyLightSpawnRules());
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("SpawnData", data);
        // loaded like a saved spawner (the world isn't ready for block updates during generation)
        spawner.getSpawner().load(null, pos, tag);
    }

    /**
     * A spawner for the obsidian tower's last floors. Each spawn is one of six loadouts (picked per spawn by the
     * spawner): every armor piece iron or diamond, and an iron or diamond sword or axe. The mobs carry FBTElites' tags,
     * so each one becomes a blight and an infernal when it appears. Because the spawn data holds more than the mob id,
     * the spawner doesn't give the mob its usual random gear (finalizeSpawn), so this gear is what it has.
     */
    private static void placeEliteSpawner(WorldGenLevel world, RandomSource random, BlockPos pos, EntityType<?> type) {
        set(world, pos.getX(), pos.getY(), pos.getZ(), Blocks.SPAWNER.defaultBlockState());
        if (!(world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
            return;
        }
        // 6 spawn options, each an equally likely armed mob; the first one is also what the spawner shows first
        String id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
        net.minecraft.nbt.ListTag potentials = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.CompoundTag first = null;
        for (int i = 0; i < 6; i++) {
            net.minecraft.nbt.CompoundTag entity = eliteEntity(id, random);
            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
            data.put("entity", entity);
            // the elite floors are only in the obsidian tower: every loadout ignores light too
            // (the spawner switches to another loadout after each spawn, so each one needs the rules)
            data.put("custom_spawn_rules", anyLightSpawnRules());
            if (first == null) {
                first = data.copy();
            }
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("weight", 1);
            entry.put("data", data);
            potentials.add(entry);
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("SpawnPotentials", potentials);
        tag.put("SpawnData", first);
        // load them into the spawner like a saved spawner
        spawner.getSpawner().load(null, pos, tag);
    }

    private static final String[] ARMOR_PIECES = {"boots", "leggings", "chestplate", "helmet"}; // ArmorItems order
    private static final String[] WEAPONS = {"iron_sword", "diamond_sword", "iron_axe", "diamond_axe"};

    // one spawn option: the mob, each armour piece iron or diamond at random, a random weapon in the main hand,
    // and the tags FBTElites looks for (blight, infernal, keep this gear)
    private static net.minecraft.nbt.CompoundTag eliteEntity(String id, RandomSource random) {
        net.minecraft.nbt.CompoundTag entity = new net.minecraft.nbt.CompoundTag();
        entity.putString("id", id);
        net.minecraft.nbt.ListTag armor = new net.minecraft.nbt.ListTag();
        for (String piece : ARMOR_PIECES) {
            armor.add(item("minecraft:" + (random.nextBoolean() ? "iron_" : "diamond_") + piece));
        }
        entity.put("ArmorItems", armor);
        net.minecraft.nbt.ListTag hands = new net.minecraft.nbt.ListTag();
        hands.add(item("minecraft:" + WEAPONS[random.nextInt(WEAPONS.length)]));
        hands.add(new net.minecraft.nbt.CompoundTag());
        entity.put("HandItems", hands);
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(com.kettle.fbt.elite.FBTElites.BLIGHT_TAG));
        tags.add(net.minecraft.nbt.StringTag.valueOf(com.kettle.fbt.elite.FBTElites.INFERNAL_TAG));
        tags.add(net.minecraft.nbt.StringTag.valueOf(com.kettle.fbt.elite.FBTElites.KEEP_GEAR_TAG));
        entity.put("Tags", tags);
        return entity;
    }

    // one item stack of 1 in saved form
    private static net.minecraft.nbt.CompoundTag item(String id) {
        net.minecraft.nbt.CompoundTag item = new net.minecraft.nbt.CompoundTag();
        item.putString("id", id);
        item.putByte("Count", (byte) 1);
        return item;
    }

    // puts the tower's golem in place (on the obsidian tower it is also a blight and an infernal)
    private static void spawnGolem(WorldGenLevel world, RandomSource random, double x, double y, double z, boolean elite) {
        BattleTowerGolemEntity golem = ForgottenBattleTowersEntities.GOLEM.get().create(world.getLevel());
        if (golem == null) {
            return;
        }
        if (elite) {
            golem.addTag(com.kettle.fbt.elite.FBTElites.BLIGHT_TAG);
            golem.addTag(com.kettle.fbt.elite.FBTElites.INFERNAL_TAG);
            // the obsidian tower's golem can blast through obsidian
            golem.addTag(com.kettle.fbt.elite.FBTElites.BREAKS_OBSIDIAN_TAG);
        }
        // random facing; never despawns; vanilla spawn setup (as a structure mob); then add it to the world
        golem.moveTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
        golem.setPersistenceRequired();
        golem.finalizeSpawn(world, world.getCurrentDifficultyAt(golem.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        world.addFreshEntityWithPassengers(golem);
    }

    private static BlockState stairs(RLTowerType type) {
        // 1.12 stairs metadata 0: facing east, bottom half.
        return type.stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    // a floor block of the tower's type
    private static void buildFloorPiece(WorldGenLevel world, int i, int j, int k, RLTowerType type) {
        set(world, i, j, k, type.floorState());
    }

    // a wall block; on the bottom floor's lowest layer, also fill down under it until solid ground
    private static void buildWallPiece(WorldGenLevel world, int i, int j, int k, Block wall, int floor, int floorIterator) {
        set(world, i, j, k, wall.defaultBlockState());
        if (floor == 1 && floorIterator == 4) {
            fillTowerBaseToGround(world, i, j, k, wall);
        }
    }

    // fills down from the tower's base until it reaches stone, dirt, grass, sand, sandstone or gravel
    private static void fillTowerBaseToGround(WorldGenLevel world, int i, int j, int k, Block blocktype) {
        for (int y = j - 1; y > 0 && !isBuildable(world.getBlockState(new BlockPos(i, y, k))); y--) {
            set(world, i, y, k, blocktype.defaultBlockState());
        }
    }

    // helpers: place air / place a block (flag 2 = tell clients, without block updates)
    private static void setAir(WorldGenLevel world, int x, int y, int z) {
        set(world, x, y, z, Blocks.AIR.defaultBlockState());
    }

    private static void set(WorldGenLevel world, int x, int y, int z, BlockState state) {
        world.setBlock(new BlockPos(x, y, z), state, 2);
    }
}
