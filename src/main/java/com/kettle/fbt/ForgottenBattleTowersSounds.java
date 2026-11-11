package com.kettle.fbt;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** fbt's sound events. Each id matches an entry in assets/fbt/sounds.json, which points to the .ogg files. */
public class ForgottenBattleTowersSounds {
   // the "fbt" sound registry; ForgottenBattleTowers adds it to the mod event bus
   public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "fbt");
   // golem sounds: idle, waking up, charging a fireball, dying, being hurt, and its special attack
   public static final RegistryObject<SoundEvent> GOLEM_IDLE = REGISTRY.register(
      "golem", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "golem"))
   );
   public static final RegistryObject<SoundEvent> GOLEM_AWAKE = REGISTRY.register(
      "golemawaken", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "golemawaken"))
   );
   public static final RegistryObject<SoundEvent> GOLEM_CHARGE = REGISTRY.register(
      "golemcharge", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "golemcharge"))
   );
   public static final RegistryObject<SoundEvent> GOLEM_DEATH = REGISTRY.register(
      "golemdeath", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "golemdeath"))
   );
   public static final RegistryObject<SoundEvent> GOLEM_HURT = REGISTRY.register(
      "golemhurt", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "golemhurt"))
   );
   public static final RegistryObject<SoundEvent> GOLEM_SPECIAL = REGISTRY.register(
      "golemspecial", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "golemspecial"))
   );
   // tower collapse sounds: when the collapse starts, and each time a part of the tower crumbles
   public static final RegistryObject<SoundEvent> TOWER_BREAK_START = REGISTRY.register(
      "towerbreakstart", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "towerbreakstart"))
   );
   public static final RegistryObject<SoundEvent> TOWER_CRUMBLE = REGISTRY.register(
      "towercrumble", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("fbt", "towercrumble"))
   );
}
