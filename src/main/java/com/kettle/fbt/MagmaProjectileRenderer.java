package com.kettle.fbt;

import com.kettle.fbt.client.ClientModLayerRegister;
import com.kettle.fbt.client.FireCubeModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Client only: draws the golem's magma projectile as a spinning cube that faces the way it flies. */
public class MagmaProjectileRenderer extends EntityRenderer<BattleTowerGolemFireBall> {
   // the cube model
   private final FireCubeModel<BattleTowerGolemFireBall> model;

   // bakes the cube model from the layer registered in ClientModLayerRegister
   public MagmaProjectileRenderer(Context ctx) {
      super(ctx);
      this.model = new FireCubeModel(ctx.bakeLayer(ClientModLayerRegister.FIRE_CUBE_LAYER));
   }

   // called every frame for each projectile
   public void render(BattleTowerGolemFireBall entity, float yaw, float partialTicks, PoseStack stack, MultiBufferSource buffer, int light) {
      stack.pushPose();
      // turn to face the direction the projectile is moving (from its horizontal motion)
      Vec3 motion = entity.getDeltaMovement();
      float x = (float)motion.x;
      float z = (float)motion.z;
      float newyaw = (float)(Math.PI - Math.atan2((double)z, (double)x));
      // spin: one full turn every 20 ticks (18 degrees a tick)
      float roll = (float)(entity.tickCount % 20) * 18.0F * (float) (Math.PI / 180.0);
      // lift the cube so its centre is half a block up, then apply the turn and the spin
      stack.translate(0.0, 0.5, 0.0);
      stack.mulPose(Axis.YP.rotation(newyaw));
      stack.mulPose(Axis.ZP.rotation(roll));
      stack.translate(0.0, 0.0, 0.0);
      // draw the cube with the magma texture, at the light level where it is
      this.model.setupAnim(entity, 0.0F, 0.0F, partialTicks, 0.0F, 0.0F);
      VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(this.getTextureLocation(entity)));
      this.model.renderToBuffer(stack, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
      stack.popPose();
      // vanilla part: draws the name tag if it has one
      super.render(entity, yaw, partialTicks, stack, buffer, light);
   }

   // the projectile's texture
   public ResourceLocation getTextureLocation(BattleTowerGolemFireBall entity) {
      return new ResourceLocation("fbt", "textures/entities/magma.png");
   }
}
