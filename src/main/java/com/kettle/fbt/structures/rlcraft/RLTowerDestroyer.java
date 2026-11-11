package com.kettle.fbt.structures.rlcraft;

import com.kettle.fbt.ForgottenBattleTowers;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.12 Battle Towers' AS_TowerDestroyer: 15 seconds after the golem dies, a power 10 explosion goes off where it
 * spawned, then one every 5 seconds 7 blocks lower, 6 in all, stopping below y 70. Each explosion also clears the
 * 16x16x8 area above it. At the end the tower's spawners are removed (towerFallDestroysMobSpawners, on in RLCraft).
 * <p>
 * Not 1.12: the obsidian tower's blocks (obsidian, crying obsidian, polished blackstone brick stairs) are blast proof,
 * so vanilla's explosion leaves them standing. {@link #blastTowerBlocks} runs the same blast again for just those
 * blocks, as if they were cobblestone, so the obsidian tower blows apart the way a cobblestone tower does.
 */
public final class RLTowerDestroyer {
    // the crumbling sound played between explosions
    private static final ResourceLocation TOWER_CRUMBLE = new ResourceLocation("fbt:towercrumble");
    private static final int INITIAL_DELAY = 300; // 15 s
    private static final int FLOOR_DELAY = 100;   // 5 s
    // number of explosions
    private static final int FLOORS = 6;
    // explosion power, the same for vanilla's blast and blastTowerBlocks
    private static final float POWER = 10.0F;
    // blast resistance the obsidian tower's blocks get in blastTowerBlocks: cobblestone's (6)
    private static final float TOWER_BLOCK_RESISTANCE = 6.0F;

    private RLTowerDestroyer() {
    }

    // called when a tower golem dies: queues the explosions and sounds, then the spawner cleanup
    public static void start(ServerLevel level, BlockPos golemPos) {
        // set once the collapse is over, so later queued explosions do nothing
        AtomicBoolean finished = new AtomicBoolean(false);
        // explosion i goes off 7 * i blocks below the golem, 15 s + 5 s * i after it died
        for (int i = 0; i < FLOORS; i++) {
            int y = golemPos.getY() - 7 * i;
            ForgottenBattleTowers.queueServerWork(INITIAL_DELAY + FLOOR_DELAY * i, () -> {
                if (finished.get()) {
                    return;
                }
                // below y 70 the collapse stops and the spawners are removed
                if (y < 70) {
                    finish(level, golemPos, finished);
                    return;
                }
                // a power 10 TNT-like explosion, then clear what's left in the 16x16x8 area above it.
                // The explosion has no source entity, so mobs killed by the collapse (or by falling after it)
                // don't count as kills by the player who killed the golem: no "blown up by <player>" messages,
                // no player kill loot/xp and no kill advancements from the tower falling.
                level.explode(null, golemPos.getX(), y, golemPos.getZ(), POWER, Level.ExplosionInteraction.TNT);
                // break the blast proof obsidian tower blocks the explosion would have broken if they were cobblestone
                blastTowerBlocks(level, golemPos, y);
                cleanUpStragglerBlocks(level, golemPos, y);
            });
            // the crumbling sounds 1.12 plays between explosions
            int soundDelay = INITIAL_DELAY + FLOOR_DELAY * i - FLOOR_DELAY / 2;
            ForgottenBattleTowers.queueServerWork(soundDelay, () -> {
                if (!finished.get()) {
                    SoundEvent crumble = ForgeRegistries.SOUND_EVENTS.getValue(TOWER_CRUMBLE);
                    if (crumble != null) {
                        // somewhere random within 7 blocks across and on one of the tower's floors
                        BlockPos at = golemPos.offset(level.random.nextInt(15) - 7, -7 * Math.max(0, level.random.nextInt(FLOORS)), level.random.nextInt(15) - 7);
                        level.playSound(null, at, crumble, SoundSource.BLOCKS, 4.0F, 1.0F);
                    }
                }
            });
        }
        // after the last explosion, remove the spawners (does nothing if y < 70 already ended it)
        ForgottenBattleTowers.queueServerWork(INITIAL_DELAY + FLOOR_DELAY * FLOORS, () -> finish(level, golemPos, finished));
    }

    /** True for the obsidian tower's blast proof blocks (wall, light and floor, stairs). */
    private static boolean isBlastProofTowerBlock(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);
    }

    /**
     * Vanilla's explosion rays (Explosion.explode in 1.20.1), from the same centre and power as the real explosion.
     * 1352 rays go out from the centre in every direction; each starts with a strength of power x 0.7 to 1.3 and loses
     * some every 0.3 blocks it travels, more for each block it passes through (the block's blast resistance).
     * A ray breaks the blocks it reaches while it still has strength left.
     * <p>
     * Here only the obsidian tower's blocks are broken, and they count as cobblestone, so they go where a cobblestone
     * tower's walls would. Other blocks stop the rays with their real resistance. Only blocks within 8 blocks across of
     * the golem's column are touched (the tower's own footprint), so obsidian near the tower is left alone.
     * The blocks are removed without drops, like the cleanup below, so the collapse doesn't hand out obsidian.
     */
    private static void blastTowerBlocks(ServerLevel level, BlockPos golem, int y) {
        double cx = golem.getX(), cy = y, cz = golem.getZ();
        java.util.Set<BlockPos> hit = new java.util.HashSet<>();
        // one ray for each point on the surface of a 16x16x16 grid (vanilla's ray directions)
        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < 16; l++) {
                    if (j != 0 && j != 15 && k != 0 && k != 15 && l != 0 && l != 15) {
                        continue;
                    }
                    // the ray's direction, as a unit vector
                    double dx = j / 15.0F * 2.0F - 1.0F;
                    double dy = k / 15.0F * 2.0F - 1.0F;
                    double dz = l / 15.0F * 2.0F - 1.0F;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= len;
                    dy /= len;
                    dz /= len;
                    // starting strength, a bit random like vanilla's
                    float strength = POWER * (0.7F + level.random.nextFloat() * 0.6F);
                    double px = cx, py = cy, pz = cz;
                    // walk the ray in 0.3 block steps until it runs out of strength
                    while (strength > 0.0F) {
                        BlockPos p = BlockPos.containing(px, py, pz);
                        if (!level.isInWorldBounds(p)) {
                            break;
                        }
                        BlockState state = level.getBlockState(p);
                        boolean towerBlock = isBlastProofTowerBlock(state);
                        if (!state.isAir() || !state.getFluidState().isEmpty()) {
                            // the block (or fluid) slows the ray: tower blocks as cobblestone, others as themselves
                            float resistance = towerBlock ? TOWER_BLOCK_RESISTANCE
                                    : Math.max(state.getBlock().getExplosionResistance(), state.getFluidState().getExplosionResistance());
                            strength -= (resistance + 0.3F) * 0.3F;
                        }
                        // still strong enough after passing it: a tower block in the tower's footprint breaks
                        if (strength > 0.0F && towerBlock && Math.abs(p.getX() - golem.getX()) <= 8 && Math.abs(p.getZ() - golem.getZ()) <= 8) {
                            hit.add(p);
                        }
                        px += dx * 0.3;
                        py += dy * 0.3;
                        pz += dz * 0.3;
                        // every step costs strength, even through air
                        strength -= 0.22500001F;
                    }
                }
            }
        }
        for (BlockPos p : hit) {
            level.removeBlock(p, false);
        }
    }

    // removes every block in the 16x16 area around the golem's column, from 1 to 8 blocks above the explosion
    private static void cleanUpStragglerBlocks(ServerLevel level, BlockPos golem, int ytemp) {
        for (int x = -8; x < 8; x++) {
            for (int z = -8; z < 8; z++) {
                for (int y = 1; y < 9; y++) {
                    BlockPos p = new BlockPos(golem.getX() + x, ytemp + y, golem.getZ() + z);
                    if (!level.getBlockState(p).isAir()) {
                        level.removeBlock(p, false);
                    }
                }
            }
        }
    }

    // ends the collapse (only once): removes every spawner within 8 blocks across, from the golem down 80 blocks (not below y 8)
    private static void finish(ServerLevel level, BlockPos golem, AtomicBoolean finished) {
        if (finished.getAndSet(true)) {
            return;
        }
        int minY = Math.max(golem.getY() - 80, 8);
        for (int x = golem.getX() - 8; x < golem.getX() + 8; x++) {
            for (int z = golem.getZ() - 8; z < golem.getZ() + 8; z++) {
                for (int y = golem.getY(); y >= minY; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).is(Blocks.SPAWNER)) {
                        level.removeBlock(p, false);
                    }
                }
            }
        }
    }
}
