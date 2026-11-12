# FBT (Forgotten Battle Towers) – RLCraft 1.20.1 build

## 1.0.15-rlcraft

- **Which ground makes which tower is a list now** (`config/fbt-common.toml`, `groundTypes`). Each line is blocks or
  `#tags` = a tower type. Like 1.12, a tower counts what the ground is at 9 spots under it (a snow layer on top, or
  snow the world adds later, counts as `minecraft:snow`); the line matched by the most spots picks the type, the
  higher line on a tie. If more spots match no line, it's a normal tower. The defaults are 1.12's rules (sand ->
  sandstone, snow/ice -> ice, water or leaves/logs/lily pads -> mossy cobblestone) plus basalt.
- **Basalt tower**, where most of the ground is basalt, polished basalt or smooth basalt: basalt walls, smooth basalt
  floors, polished blackstone stairs and railing, magma block lights. The magma's light (3) never reaches a
  spawner's spawn spots (tested: 0 of 679 lit), so its spawners work normally.
- **Netherrack towers are the Nether's.** The overworld's normal towers are 1 in 5 smooth stone, else cobblestone
  (1.12 first made 1 in 10 of them netherrack).
- **Nether towers** (`fbt:nether_battletower`, 1.12 had none in the Nether): 10 floors on a cavern floor low enough to
  fit under the bedrock roof (about y 33-46, just above the lava sea), with 1.12's site checks on the nearest solid
  floor at each spot. Netherrack, or basalt on basalt ground. Their spawners ignore light: the glowstone lit 98% of the
  spawn spots past the Nether's monster limit (7), and the Nether has no daylight. Tested: 3 of 3 fresh sites built.
- **End towers** (`fbt:end_battletower`, on the outer islands; 1.12 built normal cobblestone ones there): end stone
  brick walls, purpur floors and stairs, end stone brick wall railing, no lights. Their spawners ignore light.
- **Nether and End chests** (`chests/nether/`, `chests/end/`): the normal tables with the overworld-only items swapped
  for that dimension's (seeds -> nether wart and fungi / chorus, torches -> soul torches / end rods, ...), plus a pool
  of the dimension's items that gets better floor by floor (quartz and gold nuggets up to ghast tears, wither skeleton
  skulls and Pigstep; chorus and pearls up to shulker shells, end crystals and a dragon head). No netherite.
  Made by `make_dimension_loot.py` from the normal tables. The pack's ChestGenHook script only targets the normal ones.
- `/fbt towertype <pos>`: which type the ground there picks, or why the site fails. `/fbt spawntower` takes 9 (basalt)
  and 10 (end).

## 1.0.14-rlcraft

- The tower collapse's explosions have no source entity (the golem's killer was passed as the source before).
  Synced from the 1.0.14 jar the pack uses, whose source wasn't in this repo.

## 1.0.13-rlcraft

- **The obsidian tower's spawners ignore light.** In 1.20.1 a spawner only spawns a monster where the block light
  is 0, and the obsidian tower is lit only by crying obsidian (floor lights and wall stripes, light 10), which reaches
  every spot: its spawners spawned nothing. Its spawners (normal floors and elite floors) now carry
  `custom_spawn_rules` with the full light range, so light never stops them, by night or day, and player torches
  don't either; only breaking a spawner stops it. The rules replace the mob's own placement checks, so a mob can also
  appear over a floor hole. Peaceful still stops them. Other towers keep vanilla's rule.
  Applies to obsidian towers generated with this version (the rules are saved in each spawner).
- Replaces 1.0.12's `SpawnerCryingObsidianLight` (crying obsidian's light ignored for every spawner), which is removed.
- Tested on a server (fake player, natural spawning off, 60 s): obsidian tower lower floors 201 mobs at night and 41
  by day, elite floors 91 / 8; a cobblestone tower 7 / 0.

## 1.0.12-rlcraft

- **The obsidian tower's spawners work.** In 1.20.1 a spawner only spawns a monster where the block light is 0 (the
  mob's normal spawn rule). The obsidian tower is lit only by crying obsidian (its floor lights and the stripes down
  every wall, light 10), which reaches every spot on every floor: in a tested tower only 8 of 3,495 spawn spots were
  dark, and it spawned 0 mobs in 90 s by night or day.
  - New `SpawnerCryingObsidianLight`: when vanilla turns down a spawner's battle tower mob (zombie, skeleton, cave
    spider, spider, wither skeleton), the check runs again with crying obsidian's light taken out. If the block light
    at the spot is no brighter than the nearest crying obsidian could make it (10 minus the distance), it counts as 0.
    Brighter light, like a torch near the spot, still counts, so lighting a spawner up still stops it. Sky light,
    daylight, thunder and peaceful work as in vanilla. Applies to spawners anywhere, but only crying obsidian is ignored.
  - Tested on a server (fake player, natural spawning off, 90 s): obsidian tower 15-57 mobs at night (was 0), elite
    floors 56; cobblestone tower 20-31 at night and 0-2 by day, deep tower 4 (unchanged: torch-lit towers already
    worked, with their torches and sky light blocking part of each floor, as in vanilla).

## 1.0.11-rlcraft

- **Golems of obsidian towers and mega battle towers can blast through obsidian.** Their fireball explosions now also
  break obsidian and crying obsidian (hardness 50, far above the golem's normal limit of 4.5). Nothing else that hard
  becomes breakable, and other golems are unchanged. The mega battle towers are mostly obsidian (about 8,300 blocks).
  - Which golems: those with the new entity tag `fbt_breaks_obsidian`. The obsidian tower's golem gets it when the
    tower is built; a golem that first joins inside a mega battle tower gets it from `FBTElites` (natural towers only:
    `/place` doesn't register the structure, so a `/place`d mega tower's golem isn't recognised, as before for the elite tags).
  - Golems from towers generated with 1.0.8-1.0.10 have no new tag, but carry both elite tags (`fbt_blight`,
    `fbt_infernal`), which only these golems get, so they count too (`BattleTowerGolemEntity.breaksObsidian`).
  - Like other blocks the fireballs break, obsidian drops itself 1 time in 10. Mob griefing must be on.
- `ForgottenBattleTowersExplosion.explodeCircle` has a new `breakObsidian` option; the old signatures still work.

## 1.0.10-rlcraft

- **The collapse now blows up the obsidian tower's blocks.** Obsidian, crying obsidian and the polished blackstone
  brick stairs are blast proof, so the collapse explosions used to leave them standing. Each collapse explosion now
  repeats vanilla's explosion rays for just those blocks, treating them as cobblestone, so they break where a
  cobblestone tower's walls would. Only blocks within 8 blocks across of the golem's column are affected, and they
  don't drop items (`RLTowerDestroyer.blastTowerBlocks`).
- The collapse still reaches only the top 6 floors, as in 1.12; the obsidian tower is taller, so its lower floors
  stay standing.

## 1.0.9-rlcraft

Tower spawn rates now live in fbt's own structure sets, so the pack no longer needs
`kubejs/data/fbt/worldgen` overrides.

- **Tall and deep towers share one set** (`structure_set/tall_battletower.json`): weights tall 3, deep 2,
  spacing 55, separation 45. Because they share a set, they never spawn on top of each other.
  About 1 tall tower per 5,000 chunks and 1 deep tower per 7,600 chunks, the same as the pack had before.
- **The obsidian tower has its own set** (`structure_set/obsidian_battletower.json`, new): spacing 160,
  separation 80, salt 546466855460. That's about 1 per 25,600 chunks, the rarest tower type.
  - It also won't spawn within 3 chunks of a tall or deep tower (`exclusion_zone`).
  - Why a set of its own: when it shared the tall set, every spot where a tall tower failed its high-ground
    check went to one of the other towers, so the obsidian tower ended up almost as common as a mega tower.
- **The deep set is now empty** (`structure_set/deep_battletower.json`), since the deep tower spawns from the tall set.
- **The mega set is now empty** (`structure_set/mega_battletower.json`), which turns fbt's jigsaw mega tower off.
  RLCraft's mega battle towers come from RLStructure (`rec_migrated:rlcraft_megabattletower` and `_water`).
  - Its salt changed to 546466855453, because it used to be the same as the tall set's.
- Version is 1.0.9-rlcraft in `build.gradle` and `mods.toml`.
- Source: every class and code block now has comments saying what it does. Only comments changed;
  the code itself is the same.

Upgrading a pack: delete the old fbt jar (fbt-1.0.6b.jar / 1.0.8-rlcraft / 1.0.9-rlcraft / 1.0.10-rlcraft / 1.0.11-rlcraft / 1.0.12-rlcraft) and `kubejs/data/fbt/worldgen`.
Keep `kubejs/data/fbt/loot_tables`, which holds the pack's own loot.

## 1.0.8-rlcraft and earlier RLCraft changes (summary)

- RLCraft 1.12 Battle Towers ported to Java (`structures/rlcraft/`), replacing the template-based towers:
  - `RLTowerBuilder`: 1.12's AS_WorldGenTower, built block for block.
  - `RLBattleTowerStructure` / `RLBattleTowerPiece`: the structure type `fbt:rlcraft_battletower`.
    It runs 1.12's site checks, and the tower type comes from the real ground blocks.
  - `RLTowerType`: the 1.12 tower types, plus the 12-floor obsidian tower (`"tower_type": "obsidian"`, `"floors": 12`).
  - `RLTowerDestroyer`: 1.12's tower collapse after the golem dies.
  - `RLTowerCommand`: `/fbt spawntower x y z type underground`.
- `elite/FBTElites`: chosen tower mobs are always a Simple Difficulty blight and an Infernal Mobs elite.
- Loot tables for each floor, plus the obsidian tower's top floor.
