package com.kettle.fbt.structures.processors;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo;

/** fbt:randomize_spawner - every spawner in fbt's original template towers gets a random mob from SPAWNER_MOBS. */
public class RandomizeSpawnerProcessor extends StructureProcessor {
   // no settings, so the codec just makes a new processor
   public static final Codec<RandomizeSpawnerProcessor> CODEC = Codec.unit(RandomizeSpawnerProcessor::new);
   // the mobs a spawner can be set to, each equally likely
   private static final EntityType<?>[] SPAWNER_MOBS = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CAVE_SPIDER};

   // called once for every block of the template as it is placed
   public StructureBlockInfo processBlock(
      LevelReader level, BlockPos pos, BlockPos pivot, StructureBlockInfo original, StructureBlockInfo current, StructurePlaceSettings settings
   ) {
      BlockState state = current.state();
      BlockPos currentpos = current.pos();
      if (state.getBlock() instanceof SpawnerBlock spawn) {
         state = Blocks.SPAWNER.defaultBlockState();
         // pick the mob with a random seeded from the position, so the same tower always gets the same mobs
         RandomSource random = settings.getRandom(currentpos);
         EntityType<?> type = SPAWNER_MOBS[random.nextInt(SPAWNER_MOBS.length)];
         // copy the spawner's saved data (or start empty) so other spawner settings are kept
         CompoundTag spawnerNBT = current.nbt() == null ? new CompoundTag() : current.nbt().copy();
         // SpawnData = {entity: {id: "<mob>"}} is the mob the spawner shows and spawns
         CompoundTag entityTag = new CompoundTag();
         entityTag.putString("id", EntityType.getKey(type).toString());
         CompoundTag spawnData = new CompoundTag();
         spawnData.put("entity", entityTag);
         spawnerNBT.put("SpawnData", spawnData);
         // clear SpawnPotentials so the spawner doesn't switch to another mob from the template
         spawnerNBT.put("SpawnPotentials", new ListTag());
         return new StructureBlockInfo(currentpos, Blocks.SPAWNER.defaultBlockState(), spawnerNBT);
      } else {
         // not a spawner: place it unchanged
         return current;
      }
   }

   // entities in the template are left as they are
   public StructureEntityInfo processEntity(
      LevelReader world,
      BlockPos seedPos,
      StructureEntityInfo rawEntityInfo,
      StructureEntityInfo entityInfo,
      StructurePlaceSettings placementSettings,
      StructureTemplate template
   ) {
      return entityInfo;
   }

   // ties this class to its registered type (needed to save it in processor lists)
   protected StructureProcessorType<?> getType() {
      return (StructureProcessorType<?>)FBTStructureProcessors.RANDOMIZE_SPAWNER.get();
   }
}
