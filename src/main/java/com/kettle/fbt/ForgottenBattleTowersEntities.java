package com.kettle.fbt;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@EventBusSubscriber(
   bus = Bus.MOD
)
/** Registers fbt's entities (the Battle Tower golem and its magma projectile) and the golem's attributes. */
public class ForgottenBattleTowersEntities {
   // the "fbt" entity registry; ForgottenBattleTowers adds it to the mod event bus
   public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "fbt");
   // fbt:battle_tower_golem - the boss at the top of each tower: a monster that can't burn,
   // 1.2 blocks wide and 3.6 tall, tracked by players up to 64 blocks away, position synced every 3 ticks
   public static final RegistryObject<EntityType<BattleTowerGolemEntity>> GOLEM = register(
      "battle_tower_golem",
      Builder.<BattleTowerGolemEntity>of((type, world) -> new BattleTowerGolemEntity(type, world), MobCategory.MONSTER)
         .setShouldReceiveVelocityUpdates(true)
         .setTrackingRange(64)
         .setUpdateInterval(3)
         .setCustomClientFactory((packet, world) -> new BattleTowerGolemEntity(packet, world))
         .fireImmune()
         .sized(1.2F, 3.6F)
   );
   // fbt:magma_projectile - the golem's fireball: a 1 block cube, tracked up to 128 blocks, synced every tick
   public static final RegistryObject<EntityType<BattleTowerGolemFireBall>> MAGMA_PROJECTILE = REGISTRY.register(
      "magma_projectile",
      () -> Builder.<BattleTowerGolemFireBall>of((type, world) -> new BattleTowerGolemFireBall(type, world), MobCategory.MISC).sized(1.0F, 1.0F).clientTrackingRange(128).updateInterval(1).build("magma_projectile")
   );

   // helper: registers an entity type under fbt:<name>, built only when the registry is filled
   private static <T extends Entity> RegistryObject<EntityType<T>> register(String registryname, Builder<T> entityTypeBuilder) {
      return REGISTRY.register(registryname, () -> entityTypeBuilder.build(registryname));
   }

   // gives the golem its health, damage, speed etc. (from BattleTowerGolemEntity.createAttributes)
   @SubscribeEvent
   public static void registerAttributes(EntityAttributeCreationEvent event) {
      event.put((EntityType)GOLEM.get(), BattleTowerGolemEntity.createAttributes().build());
   }
}
