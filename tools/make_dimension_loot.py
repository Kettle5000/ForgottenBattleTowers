#!/usr/bin/env python3
"""Make fbt's Nether and End Battle Tower chest tables from the normal (overworld) ones.

Usage:
    make_dimension_loot.py <fbt loot_tables/chests dir>

Reads floor1..floor9.json and last_floor.json in that folder and writes nether/<same names> and end/<same names>:
- items that only make sense in the overworld (seeds, wheat, oak planks, torches, ...) are swapped for that
  dimension's version (SWAPS below);
- one extra pool of the dimension's items is added, better on each floor up to the last (STAGE_POOLS below).
The Nether tables never contain netherite (scrap, ingots, ancient debris, the upgrade template): CHECK_BANNED fails
the run if any shows up. Re-run after changing the overworld tables. RLTowerBuilder.lootTableForStage picks
chests/nether/... and chests/end/... for towers built in those dimensions.
"""
import argparse
import copy
import json
import os

# overworld item -> that dimension's version
SWAPS = {
    'nether': {
        'minecraft:wheat': 'minecraft:nether_wart', 'minecraft:melon_seeds': 'minecraft:crimson_fungus',
        'minecraft:pumpkin_seeds': 'minecraft:warped_fungus', 'minecraft:beetroot_seeds': 'minecraft:nether_wart',
        'minecraft:glow_berries': 'minecraft:cooked_porkchop', 'minecraft:oak_planks': 'minecraft:crimson_planks',
        'minecraft:oak_sign': 'minecraft:crimson_sign', 'minecraft:torch': 'minecraft:soul_torch',
        'minecraft:cobblestone': 'minecraft:blackstone', 'minecraft:stone': 'minecraft:basalt',
        'minecraft:stone_button': 'minecraft:polished_blackstone_button', 'minecraft:bricks': 'minecraft:nether_bricks',
        'minecraft:fishing_rod': 'minecraft:warped_fungus_on_a_stick', 'minecraft:clay_ball': 'minecraft:magma_cream',
    },
    'end': {
        'minecraft:wheat': 'minecraft:chorus_fruit', 'minecraft:melon_seeds': 'minecraft:chorus_flower',
        'minecraft:pumpkin_seeds': 'minecraft:popped_chorus_fruit', 'minecraft:beetroot_seeds': 'minecraft:chorus_fruit',
        'minecraft:glow_berries': 'minecraft:chorus_fruit', 'minecraft:oak_planks': 'minecraft:purpur_block',
        'minecraft:torch': 'minecraft:end_rod', 'minecraft:cobblestone': 'minecraft:end_stone',
        'minecraft:stone': 'minecraft:end_stone_bricks', 'minecraft:bricks': 'minecraft:purpur_block',
    },
}


def e(item, weight, lo=1, hi=1):
    """A weighted item entry with a count of lo..hi."""
    entry = {'type': 'minecraft:item', 'name': item, 'weight': weight}
    if hi > 1:
        entry['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    return entry


def pool(rolls_lo, rolls_hi, entries):
    return {'rolls': {'type': 'minecraft:uniform', 'min': rolls_lo, 'max': rolls_hi}, 'entries': entries}


# the dimension's extra pool, by floor (stage 10 = the last floor); better items and more of them higher up
STAGE_POOLS = {
    'nether': [
        ((1, 2), pool(1, 2, [e('minecraft:quartz', 10, 1, 4), e('minecraft:gold_nugget', 10, 2, 6), e('minecraft:glowstone_dust', 8, 1, 3),
                             e('minecraft:nether_wart', 8, 1, 3), e('minecraft:magma_cream', 4)])),
        ((3, 4), pool(1, 2, [e('minecraft:quartz', 8, 2, 6), e('minecraft:gold_ingot', 8, 1, 2), e('minecraft:magma_cream', 8, 1, 2),
                             e('minecraft:blaze_powder', 6, 1, 2), e('minecraft:fire_charge', 6, 1, 3), e('minecraft:crying_obsidian', 4)])),
        ((5, 6), pool(2, 2, [e('minecraft:blaze_rod', 8, 1, 2), e('minecraft:ghast_tear', 5), e('minecraft:magma_cream', 6, 2, 4),
                             e('minecraft:glowstone', 6, 1, 2), e('minecraft:crying_obsidian', 5, 1, 2), e('minecraft:gold_block', 2)])),
        ((7, 8), pool(2, 3, [e('minecraft:blaze_rod', 8, 2, 4), e('minecraft:ghast_tear', 8, 1, 2), e('minecraft:gilded_blackstone', 5),
                             e('minecraft:respawn_anchor', 2), e('minecraft:piglin_banner_pattern', 2), e('minecraft:gold_block', 3)])),
        ((9, 9), pool(2, 3, [e('minecraft:ghast_tear', 8, 2, 3), e('minecraft:blaze_rod', 8, 3, 5), e('minecraft:respawn_anchor', 4),
                             e('minecraft:music_disc_pigstep', 2), e('minecraft:wither_skeleton_skull', 1)])),
        ((10, 10), pool(3, 3, [e('minecraft:ghast_tear', 8, 2, 4), e('minecraft:blaze_rod', 8, 4, 8), e('minecraft:respawn_anchor', 4),
                               e('minecraft:music_disc_pigstep', 3), e('minecraft:wither_skeleton_skull', 3)])),
    ],
    'end': [
        ((1, 2), pool(1, 2, [e('minecraft:ender_pearl', 6), e('minecraft:chorus_fruit', 10, 2, 5), e('minecraft:popped_chorus_fruit', 6, 1, 3),
                             e('minecraft:end_rod', 5, 1, 2)])),
        ((3, 4), pool(1, 2, [e('minecraft:ender_pearl', 8, 1, 2), e('minecraft:popped_chorus_fruit', 6, 2, 4), e('minecraft:purpur_block', 6, 4, 8),
                             e('minecraft:phantom_membrane', 5, 1, 2)])),
        ((5, 6), pool(2, 2, [e('minecraft:ender_pearl', 8, 2, 3), e('minecraft:ender_eye', 5), e('minecraft:phantom_membrane', 5, 1, 2),
                             e('minecraft:shulker_shell', 2)])),
        ((7, 8), pool(2, 3, [e('minecraft:ender_eye', 8, 1, 2), e('minecraft:shulker_shell', 5), e('minecraft:dragon_breath', 5, 1, 2),
                             e('minecraft:ender_pearl', 6, 2, 4)])),
        ((9, 9), pool(2, 3, [e('minecraft:shulker_shell', 6, 1, 2), e('minecraft:dragon_breath', 6, 2, 3), e('minecraft:ender_eye', 6, 2, 3),
                             e('minecraft:end_crystal', 3)])),
        ((10, 10), pool(3, 3, [e('minecraft:shulker_shell', 8, 2, 4), e('minecraft:dragon_breath', 6, 2, 4), e('minecraft:end_crystal', 4, 1, 2),
                               e('minecraft:dragon_head', 2)])),
    ],
}

CHECK_BANNED = {'nether': ('netherite', 'ancient_debris')}


def swap(node, table):
    """Replaces item names in place, anywhere in a loot table."""
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:item' and node.get('name') in table:
            node['name'] = table[node['name']]
        for v in node.values():
            swap(v, table)
    elif isinstance(node, list):
        for v in node:
            swap(v, table)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('chests', help="fbt's data/fbt/loot_tables/chests folder")
    a = ap.parse_args()
    files = [('floor%d.json' % n, n) for n in range(1, 10)] + [('last_floor.json', 10)]
    for dim in ('nether', 'end'):
        out_dir = os.path.join(a.chests, dim)
        os.makedirs(out_dir, exist_ok=True)
        for name, stage in files:
            table = copy.deepcopy(json.load(open(os.path.join(a.chests, name))))
            swap(table, SWAPS[dim])
            extra = next(p for (lo, hi), p in STAGE_POOLS[dim] if lo <= stage <= hi)
            table['pools'].append(copy.deepcopy(extra))
            text = json.dumps(table, indent=2)
            for banned in CHECK_BANNED.get(dim, ()):
                if banned in text:
                    raise SystemExit('%s/%s contains %s' % (dim, name, banned))
            with open(os.path.join(out_dir, name), 'w') as f:
                f.write(text + '\n')
        print('wrote', out_dir)


if __name__ == '__main__':
    main()
