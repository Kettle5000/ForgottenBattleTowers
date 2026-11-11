package com.kettle.fbt;

import com.google.common.base.Predicate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** The golem's explosions: break a sphere of blocks and damage and knock back the entities in it. */
public class ForgottenBattleTowersExplosion {
   // short version: big explosion particle, blocks drop items
   public static void explodeCircle(
      ServerLevel level, Entity owner, BlockPos pos, double range, float damage, double blockHardness, Predicate<Entity> predicate
   ) {
      explodeCircle(level, owner, pos, range, damage, ParticleTypes.EXPLOSION_EMITTER, true, blockHardness, false, predicate);
   }

   // short version that can also break obsidian (for golems of obsidian and mega battle towers)
   public static void explodeCircle(
      ServerLevel level, Entity owner, BlockPos pos, double range, float damage, double blockHardness, boolean breakObsidian, Predicate<Entity> predicate
   ) {
      explodeCircle(level, owner, pos, range, damage, ParticleTypes.EXPLOSION_EMITTER, true, blockHardness, breakObsidian, predicate);
   }

   // full version without obsidian breaking (kept so older callers still work)
   public static void explodeCircle(
      ServerLevel level,
      Entity owner,
      BlockPos pos,
      double range,
      float damage,
      ParticleOptions particleTypes,
      boolean dropItems,
      double blockHardness,
      Predicate<Entity> predicate
   ) {
      explodeCircle(level, owner, pos, range, damage, particleTypes, dropItems, blockHardness, false, predicate);
   }

   // full version. range = radius in blocks, damage = hurt dealt to each entity,
   // blockHardness = the hardest block it can break, breakObsidian = also break obsidian and crying obsidian
   // (hardness 50, far above blockHardness; nothing else that hard is added), predicate = which entities it can hit
   public static void explodeCircle(
      ServerLevel level,
      Entity owner,
      BlockPos pos,
      double range,
      float damage,
      ParticleOptions particleTypes,
      boolean dropItems,
      double blockHardness,
      boolean breakObsidian,
      Predicate<Entity> predicate
   ) {
      float hard = 0.0F;
      int radius = (int)range;
      // show the particles (one per block of radius) at the centre
      level.sendParticles(particleTypes, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, radius, 0.0, 0.0, 0.0, 1.0);
      // break blocks only if the mobGriefing game rule (or a mod) allows it
      if (ForgeEventFactory.getMobGriefingEvent(level, owner)) {
         // go through every block in the cube around the centre, and keep the ones inside the sphere
         for (int x = 0; x <= radius * 2; x++) {
            for (int y = 0; y <= radius * 2; y++) {
               for (int z = 0; z <= radius * 2; z++) {
                  int dx = Math.abs(x - radius);
                  int dy = Math.abs(y - radius);
                  int dz = Math.abs(z - radius);
                  int d = dx * dx + dy * dy + dz * dz;
                  int radius2 = radius * radius;
                  if (d <= radius2) {
                     BlockPos newpos = pos.offset(x - radius, y - radius, z - radius);
                     BlockState state = level.getBlockState(newpos);
                     // skip unbreakable blocks (hardness below 0, like bedrock) and blocks harder than blockHardness
                     if (!state.isAir()) {
                        hard = state.getDestroySpeed(level, newpos);
                        boolean obsidian = breakObsidian && (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN));
                        if (obsidian || !(hard <= 0.0F) && !((double)hard > blockHardness)) {
                           // blocks with contents (chests etc.) always drop their items; other blocks drop 1 time in 10
                           if (level.getBlockState(newpos).hasBlockEntity() || level.getRandom().nextInt(10) == 0) {
                              Block.dropResources(state, level, newpos, level.getBlockEntity(newpos), owner, ItemStack.EMPTY);
                           }

                           // remove the block
                           level.removeBlock(newpos, dropItems);
                        }
                     }
                  }
               }
            }
         }
      }

      // entities in a box of the explosion's size, filtered by predicate (the owner itself is skipped)
      AABB searchbox = AABB.ofSize(new Vec3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()), range * 2.0, range * 2.0, range * 2.0);
      List<Entity> entities = level.getEntities(owner, searchbox, predicate);
      // let other mods see the explosion and change which entities it hits
      ForgeEventFactory.onExplosionDetonate(
         level,
         new Explosion(level, owner, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (float)(radius * 2), new ArrayList()),
         entities,
         (double)(radius * 2)
      );
      // explosion damage caused by the owner
      DamageSource explosionDamage = new DamageSource(
         level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.EXPLOSION), owner, null
      );

      // hurt each entity and push living ones away from the centre (strength = 1/10 of the radius)
      for (Entity entity : entities) {
         entity.hurt(explosionDamage, damage);
         Vec3 knockbackDirection = Vec3.atCenterOf(pos).subtract(Vec3.atCenterOf(entity.blockPosition())).normalize();
         double knockbackStrength = range * 0.1;
         if (entity instanceof LivingEntity livingEntity) {
            livingEntity.knockback(knockbackStrength, knockbackDirection.x, knockbackDirection.z);
         }
      }

      // explosion sound
      level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS);
   }
}
