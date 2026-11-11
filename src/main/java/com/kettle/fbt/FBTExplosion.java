package com.kettle.fbt;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/**
 * A plain vanilla explosion that starts with a fixed list of blocks to destroy.
 * It exists only to reach that Explosion constructor (the list version), which other code can't call directly.
 */
public class FBTExplosion extends Explosion {
   // level, the entity that caused it, the centre x/y/z, the radius, and the blocks it will break
   public FBTExplosion(Level p_46024_, Entity p_46025_, double p_46026_, double p_46027_, double p_46028_, float p_46029_, List<BlockPos> p_46030_) {
      super(p_46024_, p_46025_, p_46026_, p_46027_, p_46028_, p_46029_, p_46030_);
   }
}
