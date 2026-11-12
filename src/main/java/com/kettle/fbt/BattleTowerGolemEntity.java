package com.kettle.fbt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PlayMessages.SpawnEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * The Battle Tower golem: the boss at the top of each tower. It sleeps until a player touches a chest nearby or hits it,
 * fights in melee and with magma fireballs, and when killed in its tower starts the tower's collapse.
 */
public class BattleTowerGolemEntity extends Monster {
   // values kept in sync with clients: charging a fireball, whether its spawn spot was saved yet, that spot,
   // and whether a chest touch already woke it (so the wake-up sound plays once)
   private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(BattleTowerGolemEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> ORIGINAL_COORDINATES_SAVED = SynchedEntityData.defineId(
      BattleTowerGolemEntity.class, EntityDataSerializers.BOOLEAN
   );
   private static final EntityDataAccessor<BlockPos> ORIGINAL_BLOCKPOS = SynchedEntityData.defineId(
      BattleTowerGolemEntity.class, EntityDataSerializers.BLOCK_POS
   );
   private static final EntityDataAccessor<Boolean> CHEST_TRIGGER = SynchedEntityData.defineId(BattleTowerGolemEntity.class, EntityDataSerializers.BOOLEAN);
   // the tower structures it can belong to, and the ids of its sounds
   private static final ResourceLocation TALL_TOWER = new ResourceLocation("fbt:tall_battletower");
   private static final ResourceLocation DEEP_TOWER = new ResourceLocation("fbt:deep_battletower");
   private static final ResourceLocation OBSIDIAN_TOWER = new ResourceLocation("fbt:obsidian_battletower");
   private static final ResourceLocation TOWER_CRUMBLE = new ResourceLocation("fbt:towercrumble");
   private static final ResourceLocation TOWER_BREAK_START = new ResourceLocation("fbt:towerbreakstart");
   private static final ResourceLocation GOLEM_SPECIAL = new ResourceLocation("fbt:golemspecial");
   private static final ResourceLocation GOLEM_CHARGE = new ResourceLocation("fbt:golemcharge");
   private static final ResourceLocation GOLEM = new ResourceLocation("fbt:golem");
   private static final ResourceLocation GOLEM_HURT = new ResourceLocation("fbt:golemhurt");
   private static final ResourceLocation GOLEM_DEATH = new ResourceLocation("fbt:golemdeath");

   // used on the client when the server sends it a new golem
   public BattleTowerGolemEntity(SpawnEntity packet, Level world) {
      this((EntityType<BattleTowerGolemEntity>)ForgottenBattleTowersEntities.GOLEM.get(), world);
   }

   // can step up full blocks; 100 xp when killed; AI on
   public BattleTowerGolemEntity(EntityType<BattleTowerGolemEntity> type, Level world) {
      super(type, world);
      this.setMaxUpStep(1.0F);
      this.xpReward = 100;
      this.setNoAi(false);
   }

   // never despawns, even far from players or in peaceful
   public boolean removeWhenFarAway(double p_21542_) {
      return false;
   }

   // Forge's spawn packet (needed for modded entities)
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }

   protected boolean shouldDespawnInPeaceful() {
      return false;
   }

   // whether it is about to shoot a fireball
   public boolean isCharging() {
      return (Boolean)this.entityData.get(DATA_IS_CHARGING);
   }

   public void setCharging(boolean p_32759_) {
      this.entityData.set(DATA_IS_CHARGING, p_32759_);
   }

   // default fireball power (the fireball goal sets its own)
   public int getExplosionPower() {
      return 1;
   }

   // starting values of the synced data
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(DATA_IS_CHARGING, false);
      this.entityData.define(ORIGINAL_COORDINATES_SAVED, false);
      this.entityData.define(ORIGINAL_BLOCKPOS, BlockPos.ZERO);
      this.entityData.define(CHEST_TRIGGER, false);
   }

   // saves its spawn spot with the entity, so after a reload it still knows which tower to collapse
   public void addAdditionalSaveData(@NotNull CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putBoolean("saved_pos", (Boolean)this.entityData.get(ORIGINAL_COORDINATES_SAVED));
      tag.putInt("PosX", ((BlockPos)this.entityData.get(ORIGINAL_BLOCKPOS)).getX());
      tag.putInt("PosY", ((BlockPos)this.entityData.get(ORIGINAL_BLOCKPOS)).getY());
      tag.putInt("PosZ", ((BlockPos)this.entityData.get(ORIGINAL_BLOCKPOS)).getZ());
   }

   // loads it back
   public void readAdditionalSaveData(@NotNull CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.entityData.set(ORIGINAL_COORDINATES_SAVED, tag.getBoolean("saved_pos"));
      this.entityData.set(ORIGINAL_BLOCKPOS, new BlockPos(tag.getInt("PosX"), tag.getInt("PosY"), tag.getInt("PosZ")));
   }

   // slightly random pitch for its sounds
   public float getVoicePitch() {
      return (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
   }

   // louder and higher pitched when it has a target
   public void playAmbientSound() {
      if (this.getTarget() != null) {
         this.playSound(this.getAmbientSound(), 0.5F, 1.5F);
      } else {
         this.playSound(this.getAmbientSound(), 0.15F, 1.0F);
      }
   }

   // the first time it enters the world, remember where it is: that is the tower it guards
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!(Boolean)this.entityData.get(ORIGINAL_COORDINATES_SAVED)) {
         this.entityData.set(ORIGINAL_BLOCKPOS, new BlockPos(this.getBlockX(), this.getBlockY(), this.getBlockZ()));
         this.entityData.set(ORIGINAL_COORDINATES_SAVED, true);
      }
   }

   // AI: melee attack with a long reach; target the nearest player it can see, or whoever hurts it
   // (once it has a target it follows it up to 64 blocks, otherwise it only notices players within 4 blocks);
   // shoot fireballs at its target; swim in water
   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.4F, false) {
         protected double getAttackReachSqr(LivingEntity entity) {
            return (double)(this.mob.getBbWidth() * 8.0F * this.mob.getBbWidth() + entity.getBbWidth());
         }
      });
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true, false) {
         protected double getFollowDistance() {
            return this.mob.getTarget() != null ? 64.0 : this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
         }
      });
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
         protected double getFollowDistance() {
            return this.mob.getTarget() != null ? 64.0 : this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
         }
      });
      this.goalSelector.addGoal(4, new BattleTowerGolemEntity.ShootFireballGoal(this));
      this.goalSelector.addGoal(5, new FloatGoal(this));
   }

   // once it loses its target, a chest touch can wake it again
   public void tick() {
      super.tick();
      if (this.getTarget() == null) {
         this.entityData.set(CHEST_TRIGGER, false);
      }
   }

   // not undead or an arthropod, so Smite and Bane of Arthropods don't add damage
   public MobType getMobType() {
      return MobType.UNDEFINED;
   }

   // how far down it sits when riding (it can't ride, see canRide)
   public double getMyRidingOffset() {
      return -0.35;
   }

   // idle sound, or the special sound when it is fighting
   public SoundEvent getAmbientSound() {
      return this.getTarget() == null
         ? (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(GOLEM)
         : (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(GOLEM_SPECIAL);
   }

   // quiet iron golem footsteps
   public void playStepSound(BlockPos pos, BlockState blockIn) {
      this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.15F, 1.0F);
   }

   protected boolean canRide(Entity p_20339_) {
      return false;
   }

   // hurt and death sounds
   public SoundEvent getHurtSound(DamageSource ds) {
      return (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(GOLEM_HURT);
   }

   public SoundEvent getDeathSound() {
      return (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(GOLEM_DEATH);
   }

   // immune to standing in fire, thrown potions and lingering clouds, drowning and fall damage
   public boolean hurt(DamageSource damagesource, float amount) {
      if (damagesource.is(DamageTypes.IN_FIRE)) {
         return false;
      } else if (damagesource.getDirectEntity() instanceof ThrownPotion || damagesource.getDirectEntity() instanceof AreaEffectCloud) {
         return false;
      } else if (damagesource.is(DamageTypes.DROWN)) {
         return false;
      } else {
         return damagesource.is(DamageTypes.FALL) ? false : super.hurt(damagesource, amount);
      }
   }

   // water doesn't push it
   public boolean isPushedByFluid() {
      return false;
   }

   // on death: if it was in its tower, tell every player and start the collapse
   public void die(DamageSource cause) {
      super.die(cause);
      BlockPos towerPos = (BlockPos)this.entityData.get(ORIGINAL_BLOCKPOS);
      // 1.12 Battle Towers: a golem killed in its tower (spawned above y 50) brings the tower down floor by floor.
      if (this.level() instanceof ServerLevel serverLevel && towerPos.getY() > 50 && this.isInsideStructure(serverLevel, towerPos)) {
         Component message = Component.literal("A Battletower's Guardian has fallen! Without its power, the Tower will collapse...");

         for (ServerPlayer player : serverLevel.players()) {
            player.sendSystemMessage(message);
         }

         serverLevel.playSound(null, towerPos, (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(TOWER_BREAK_START), SoundSource.BLOCKS, 4.0F, 1.0F);
         com.kettle.fbt.structures.rlcraft.RLTowerDestroyer.start(serverLevel, towerPos);
      }
   }

   // speed 0.75, 500 health, no armour, 10 attack damage, notices players within 4 blocks,
   // can't be knocked back, 4 block reach
   public static Builder createAttributes() {
      Builder builder = Mob.createMobAttributes();
      builder = builder.add(Attributes.MOVEMENT_SPEED, 0.75);
      builder = builder.add(Attributes.MAX_HEALTH, 500.0);
      builder = builder.add(Attributes.ARMOR, 0.0);
      builder = builder.add(Attributes.ATTACK_DAMAGE, 10.0);
      builder = builder.add(Attributes.FOLLOW_RANGE, 4.0);
      builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 10.0);
      return builder.add((Attribute)ForgeMod.ENTITY_REACH.get(), 4.0);
   }

   // wakes it up and makes it attack this player (from a chest touch or a fishing hook); the sound plays only once
   public void setTargetChest(LivingEntity entity) {
      if (!(Boolean)this.entityData.get(CHEST_TRIGGER)) {
         this.level()
            .playSound(null, entity.blockPosition(), (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(GOLEM_SPECIAL), SoundSource.HOSTILE, 1.0F, 1.8F);
         this.setTarget(entity);
         this.entityData.set(CHEST_TRIGGER, true);
      }
   }

   /**
    * True for the golems of obsidian and mega battle towers: their fireballs also break obsidian.
    * Golems from towers generated before this tag existed (fbt 1.0.8-1.0.10) are recognised by carrying both
    * elite tags, which only those golems get.
    */
   public boolean breaksObsidian() {
      return this.getTags().contains(com.kettle.fbt.elite.FBTElites.BREAKS_OBSIDIAN_TAG)
         || this.getTags().contains(com.kettle.fbt.elite.FBTElites.BLIGHT_TAG) && this.getTags().contains(com.kettle.fbt.elite.FBTElites.INFERNAL_TAG);
   }

   // can't be pushed by other entities or attacks
   public void push(double x, double y, double z) {
   }

   // true if pos is inside a tall, deep or obsidian tower structure
   private boolean isInsideStructure(ServerLevel level, BlockPos pos) {
      StructureManager structureManager = level.structureManager();
      // The 1.12 towers' structure covers their whole chunk column, so the golem's spawn spot is inside it either way.
      for (ResourceLocation id : new ResourceLocation[]{TALL_TOWER, DEEP_TOWER, OBSIDIAN_TOWER}) {
         var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ResourceKey.create(Registries.STRUCTURE, id));
         if (holder.isPresent() && structureManager.getStructureAt(pos, (Structure)holder.get().value()) != StructureStart.INVALID_START) {
            return true;
         }
      }
      return false;
   }

   // fireball attack, like a ghast's: charge for 20 ticks, shoot, then wait 2 seconds
   static class ShootFireballGoal extends Goal {
      private final BattleTowerGolemEntity golem;
      public int chargeTime;

      public ShootFireballGoal(BattleTowerGolemEntity p_32776_) {
         this.golem = p_32776_;
      }

      // active whenever it has a target
      public boolean canUse() {
         return this.golem.getTarget() != null;
      }

      public void start() {
         this.chargeTime = 0;
      }

      public void stop() {
         this.golem.setCharging(false);
      }

      public boolean requiresUpdateEveryTick() {
         return true;
      }

      // runs every tick while active
      public void tick() {
         LivingEntity livingentity = this.golem.getTarget();
         if (livingentity != null) {
            // target within 64 blocks: charge up, playing the charge sound halfway
            if (livingentity.distanceToSqr(this.golem) < 4096.0) {
               Level level = this.golem.level();
               this.chargeTime++;
               if (this.chargeTime == 10 && !this.golem.isSilent()) {
                  this.golem.playSound((SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(BattleTowerGolemEntity.GOLEM_CHARGE));
               }

               // fully charged: aim at the target (slightly above its feet)
               if (this.chargeTime == 20) {
                  Vec3 vec3 = this.golem.getTarget().position().subtract(this.golem.getEyePosition()).add(0.0, 0.5, 0.0);
                  // a weak fireball (power 1) if it can see the target, a strong one (power 4) if the target is hiding
                  float localpower = (float)this.golem.getExplosionPower();
                  if (this.golem.getSensing().hasLineOfSight(livingentity)) {
                     localpower = 1.0F;
                  } else {
                     localpower = 4.0F;
                  }

                  // fire it at twice normal speed; -40 = 2 seconds before charging again
                  BattleTowerGolemFireBall largefireball = new BattleTowerGolemFireBall(level, this.golem, localpower);
                  largefireball.setDeltaMovement(vec3.normalize().scale(2.0));
                  level.addFreshEntity(largefireball);
                  this.chargeTime = -40;
               }
            } else if (this.chargeTime > 0) {
               // target too far away: let the charge drain
               this.chargeTime--;
            }

            // shows the charging look for the last 10 ticks
            this.golem.setCharging(this.chargeTime > 10);
         }
      }
   }
}
