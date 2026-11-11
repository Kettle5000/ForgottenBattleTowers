package com.kettle.fbt.structures.processors;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo;

/**
 * fbt:deep_decay - the same wear as {@link HeightDecayProcessor}, but it stops at 60 blocks up,
 * because fbt's original template deep tower is shorter.
 */
public class DeepDecayProcessor extends StructureProcessor {
   // no settings, so the codec just makes a new processor
   public static final Codec<DeepDecayProcessor> CODEC = Codec.unit(DeepDecayProcessor::new);
   // only this block is ever removed
   private static final Block TARGET_BLOCK = Blocks.SMOOTH_STONE;

   // called once for every block of the template as it is placed
   public StructureBlockInfo processBlock(
      LevelReader level, BlockPos pos, BlockPos pivot, StructureBlockInfo original, StructureBlockInfo current, StructurePlaceSettings settings
   ) {
      BlockState state = current.state();
      BlockPos currentpos = current.pos();
      if (state.is(TARGET_BLOCK)) {
         // height of this block above the template's base
         int y = currentpos.getY() - pos.getY();
         // random seeded from the block position, so the same tower always decays the same way
         RandomSource random = settings.getRandom(currentpos);
         // between 25 and 60 blocks up: chance is 0.3% per block above 20
         if (y > 25 && y < 60 && random.nextFloat() <= 0.003F * (float)(y - 20)) {
            state = Blocks.AIR.defaultBlockState();
            return new StructureBlockInfo(currentpos, state, current.nbt());
         }
      }

      // every other block is placed unchanged
      return current;
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
      return (StructureProcessorType<?>)FBTStructureProcessors.DEEP_DECAY.get();
   }
}
