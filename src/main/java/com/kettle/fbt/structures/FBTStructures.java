package com.kettle.fbt.structures;

import com.kettle.fbt.structures.rlcraft.RLBattleTowerPiece;
import com.kettle.fbt.structures.rlcraft.RLBattleTowerStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Structure type and piece type for the 1.12 RLCraft Battle Towers. */
public class FBTStructures {
    // registries for fbt's structure types and piece types; ForgottenBattleTowers adds them to the mod event bus
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, "fbt");
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, "fbt");

    // "type": "fbt:rlcraft_battletower" in data/fbt/worldgen/structure/*.json; the codec reads that file's options
    public static final RegistryObject<StructureType<RLBattleTowerStructure>> RLCRAFT_BATTLETOWER =
            STRUCTURE_TYPES.register("rlcraft_battletower", () -> () -> RLBattleTowerStructure.CODEC);
    // the piece saved in chunk data for each tower; RLBattleTowerPiece::new loads it back from that saved data
    public static final RegistryObject<StructurePieceType> RLCRAFT_BATTLETOWER_PIECE =
            PIECE_TYPES.register("rlcraft_battletower", () -> (StructurePieceType.ContextlessType) RLBattleTowerPiece::new);
}
