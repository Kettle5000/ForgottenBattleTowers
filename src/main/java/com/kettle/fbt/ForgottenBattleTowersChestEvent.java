package com.kettle.fbt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
/**
 * Wakes the tower golem when a player touches a chest near it (right or left click within 10 blocks),
 * or hooks the golem with a fishing rod. The touch is cancelled when a golem woke, so the chest doesn't open.
 */
public class ForgottenBattleTowersChestEvent {
   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   // right click on a block (LOW priority: runs after most other mods and is skipped if one cancelled it)
   public static void onChestOpened(RightClickBlock event) {
      if (!event.isCanceled()) {
         Level level = event.getLevel();
         BlockPos pos = event.getPos();
         Player player = event.getEntity();
         BlockState state = level.getBlockState(pos);
         // a chest: wake any golem nearby; cancel the click if one woke
         if (state.getBlock() instanceof ChestBlock chestBlock) {
            event.setCanceled(notifyEntitiesNearby(level, pos, player));
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   // left click on a chest does the same
   public static void onChestAttacked(LeftClickBlock event) {
      if (!event.isCanceled()) {
         Level level = event.getLevel();
         BlockPos pos = event.getPos();
         Player player = event.getEntity();
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof ChestBlock) {
            event.setCanceled(notifyEntitiesNearby(level, pos, player));
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   // a fishing hook hitting the golem wakes it and makes it target the player who cast it
   public static void onFishingHookImpact(ProjectileImpactEvent event) {
      if (!event.isCanceled()) {
         if (event.getEntity() instanceof BattleTowerGolemEntity golem && event.getProjectile() instanceof FishingHook hook) {
            // a hook with no player (e.g. cast before a relog) does nothing
            if (hook.getPlayerOwner() == null) {
               return;
            }

            // cancel the hit so the hook doesn't pull the golem
            golem.setTargetChest(hook.getPlayerOwner());
            event.setCanceled(true);
         }
      }
   }

   // wakes every golem within 10 blocks of the chest and points it at the player; true if any was found
   private static boolean notifyEntitiesNearby(Level level, BlockPos chestPos, Player player) {
      AABB box = new AABB(chestPos).inflate(10.0);
      boolean flag = false;

      for (BattleTowerGolemEntity entity : level.getEntitiesOfClass(BattleTowerGolemEntity.class, box)) {
         entity.setTargetChest(player);
         flag = true;
      }

      return flag;
   }
}
