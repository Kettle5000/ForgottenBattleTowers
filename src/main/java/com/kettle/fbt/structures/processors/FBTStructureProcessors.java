package com.kettle.fbt.structures.processors;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers fbt's structure processors. A processor changes blocks of a template (.nbt) structure as it is placed.
 * Template pools refer to these by id, e.g. "processor_type": "fbt:height_decay".
 * Only fbt's original template towers use them (template_pool/tall_tower_pool.json and deep_battletower/startpart.json).
 * No structure uses those pools any more: the tall, deep and obsidian towers are now built in Java (structures/rlcraft).
 */
public class FBTStructureProcessors {
   // the "fbt" processor registry; ForgottenBattleTowers adds it to the mod event bus
   public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, "fbt");
   // fbt:height_decay - knocks holes in smooth stone 25-80 blocks up the tower, more holes the higher up
   public static final RegistryObject<StructureProcessorType<HeightDecayProcessor>> HEIGHT_DECAY = PROCESSORS.register(
      "height_decay", () -> () -> HeightDecayProcessor.CODEC
   );
   // fbt:randomize_spawner - gives each spawner a random zombie, skeleton or cave spider
   public static final RegistryObject<StructureProcessorType<RandomizeSpawnerProcessor>> RANDOMIZE_SPAWNER = PROCESSORS.register(
      "randomize_spawner", () -> () -> RandomizeSpawnerProcessor.CODEC
   );
   // fbt:deep_decay - like height_decay but only 25-60 blocks up, for the old deep tower
   public static final RegistryObject<StructureProcessorType<DeepDecayProcessor>> DEEP_DECAY = PROCESSORS.register(
      "deep_decay", () -> () -> DeepDecayProcessor.CODEC
   );
}
