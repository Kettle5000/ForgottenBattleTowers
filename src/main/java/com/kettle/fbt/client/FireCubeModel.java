package com.kettle.fbt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;

/** The golem's magma projectile model: one plain cube, a full block in size. */
public class FireCubeModel<T extends Entity> extends EntityModel<T> {
   // the baked model, made from createBodyLayer
   private final ModelPart root;

   public FireCubeModel(ModelPart root) {
      this.root = root;
   }

   // the shape: a 16x16x16 pixel cube centred on the entity, using a 32x32 texture
   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild("cube", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.ZERO);
      return LayerDefinition.create(mesh, 32, 32);
   }

   // no animation; the renderer spins it instead
   public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
   }

   // draws the cube (the colour tint arguments are ignored)
   public void renderToBuffer(PoseStack stack, VertexConsumer buffer, int packedLight, int packedOverlay, float r, float g, float b, float a) {
      this.root.render(stack, buffer, packedLight, packedOverlay);
   }
}
