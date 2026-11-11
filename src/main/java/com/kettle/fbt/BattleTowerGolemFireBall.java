package com.kettle.fbt;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.ForgeEventFactory;

/** The golem's magma fireball: flies straight, hurts what it hits and blows up a small sphere around it. */
public class BattleTowerGolemFireBall extends Projectile {
   // life is only saved and loaded (nothing counts it); power sets the explosion size and damage
   private int life;
   private float power = 1.0F;
   // hit box 1.5 blocks across
   private static final EntityDimensions SIZE = EntityDimensions.scalable(1.5F, 1.5F);

   // used by the entity type (and on clients); always drawn, even when off screen
   public BattleTowerGolemFireBall(EntityType<? extends BattleTowerGolemFireBall> type, Level level) {
      super(type, level);
      this.noCulling = true;
   }

   // a new fireball shot by the golem from its eye height
   public BattleTowerGolemFireBall(Level level, LivingEntity shooter, float power) {
      this((EntityType<? extends BattleTowerGolemFireBall>)ForgottenBattleTowersEntities.MAGMA_PROJECTILE.get(), level);
      this.setOwner(shooter);
      this.setPos(shooter.getX(), shooter.getEyeY(), shooter.getZ());
      this.power = power;
   }

   public EntityDimensions getDimensions(Pose pose) {
      return SIZE;
   }

   // every tick: removed if its golem is gone or its chunk isn't loaded
   public void tick() {
      Entity entity = this.getOwner();
      if ((entity == null || !entity.isRemoved()) && this.level().hasChunkAt(this.blockPosition())) {
         super.tick();
         // looks like it's burning
         this.setSecondsOnFire(1);
         // check whether this tick's movement hits an entity or block (other mods can cancel the hit)
         HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, x$0 -> this.canHitEntity(x$0));
         if (hitresult.getType() != Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hitresult)) {
            this.onHit(hitresult);
         }

         // move it on, with bubbles in water and a smoke trail
         this.checkInsideBlocks();
         Vec3 vec3 = this.getDeltaMovement();
         double d0 = this.getX() + vec3.x;
         double d1 = this.getY() + vec3.y;
         double d2 = this.getZ() + vec3.z;
         if (this.isInWater()) {
            for (int i = 0; i < 4; i++) {
               this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3.x * 0.25, d1 - vec3.y * 0.25, d2 - vec3.z * 0.25, vec3.x, vec3.y, vec3.z);
            }
         }

         this.level().addParticle(this.getTrailParticle(), d0, d1 + 0.5, d2, 0.0, 0.0, 0.0);
         this.setPos(d0, d1, d2);
      } else {
         this.discard();
      }
   }

   // on the server: hurt or explode, then remove the fireball
   protected void onHit(HitResult result) {
      super.onHit(result);
      Level level = this.level();
      if (!level.isClientSide()) {
         // hit an entity: 20 fireball damage, then (if mob griefing is on) an explosion of radius = power
         // that hits other living entities around it, not the one hit or the golem
         // golems of obsidian and mega battle towers also blow up obsidian
         boolean breakObsidian = this.getOwner() instanceof BattleTowerGolemEntity golem && golem.breaksObsidian();
         if (result instanceof EntityHitResult hit) {
            hit.getEntity()
               .hurt(
                  new DamageSource(
                     level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FIREBALL), this.getOwner(), this.getOwner()
                  ),
                  20.0F
               );
            if (ForgeEventFactory.getMobGriefingEvent(this.level(), this.getOwner()) && level instanceof ServerLevel serverLevel) {
               ForgottenBattleTowersExplosion.explodeCircle(
                  serverLevel, this.getOwner(), this.blockPosition(), (double)this.power, this.power * 5.0F, 4.5, breakObsidian, entity -> {
                     if (entity instanceof LivingEntity livingEntity && entity != hit.getEntity() && entity != this.getOwner()) {
                        return true;
                     }

                     return false;
                  }
               );
            }
         } else if (result instanceof BlockHitResult
            && ForgeEventFactory.getMobGriefingEvent(this.level(), this.getOwner())
            && level instanceof ServerLevel serverLevel) {
            // hit a block (and mob griefing is on): a slightly bigger explosion, radius power + 1.
            // Both explosions break blocks up to hardness 4.5 and deal power x 5 damage
            ForgottenBattleTowersExplosion.explodeCircle(
               serverLevel, this.getOwner(), this.blockPosition(), (double)(this.power + 1.0F), this.power * 5.0F, 4.5, breakObsidian, entity -> {
                  if (entity instanceof LivingEntity livingEntity && entity != this.getOwner()) {
                     return true;
                  }

                  return false;
               }
            );
         }

         this.discard();
      }
   }

   // nothing extra to sync
   protected void defineSynchedData() {
   }

   // save/load
   protected void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.life = tag.getInt("Life");
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Life", this.life);
   }

   // explosions, attacks and pushes don't affect it, so it can't be knocked back at the golem
   public boolean isInvulnerableTo(DamageSource source) {
      return source.is(DamageTypeTags.IS_EXPLOSION) || super.isInvulnerableTo(source);
   }

   public boolean ignoreExplosion() {
      return true;
   }

   public boolean hurt(DamageSource source, float amount) {
      return false;
   }

   public void push(double x, double y, double z) {
   }

   // the trail particle
   protected ParticleOptions getTrailParticle() {
      return ParticleTypes.SMOKE;
   }
}
