// A JavaScript twin of the mod's Masonry (src/main/java/com/elderlexicon/mod/university/Masonry.java), for drafting a
// building in a minecraft-builder-skill recipe and porting it to Java line for line. Offsets are from the building's
// middle, y = 0 is the ground (the grass), as in the mod. The campus's rotation and the chunk clipping are left out:
// a draft is one building, unturned.
//
//   import { masonry } from '<path>/masonry.mjs'
//   export default function build(api) {
//     const m = masonry(api, { x: [-8, 8], y: [-2, 20], z: [-6, 6] })
//     m.fill(-8, 0, -6, 8, 0, 6, 'minecraft:grass_block')
//     m.set(0, 1, 0, m.stone(0, 1, 0))
//     return { structure: m.structure, metadata: { name: 'draft' } }
//   }

/** The mod's Mth.getSeed(x, y, z), so a draft's chance() falls like the game's at the same offsets. */
function seed(x, y, z) {
  // (long)(x * 3129871): the x product overflows as an int in the game, the z one as a long.
  let l = BigInt(Math.imul(x, 3129871)) ^(BigInt.asIntN(64, BigInt(z) * 116129781n)) ^ BigInt(y)
  l = BigInt.asIntN(64, BigInt.asIntN(64, l * l * 42317861n) + BigInt.asIntN(64, l * 11n))
  return Number(l >> 16n)
}

export function masonry(api, bounds) {
  const [x0, x1] = bounds.x
  const [y0, y1] = bounds.y
  const [z0, z1] = bounds.z
  const structure = api.createStructure([x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1])
  const placed = new Map()
  const key = (x, y, z) => `${x},${y},${z}`
  const inside = (x, y, z) => x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1

  const m = {
    structure,

    /** Places a block; false (and nothing placed) outside the bounds, as the mod's set() is outside the chunk. */
    set(x, y, z, block) {
      if (!inside(x, y, z)) return false
      api.setBlock(structure, [x - x0, y - y0, z - z0], block)
      placed.set(key(x, y, z), block)
      return true
    },

    fill(ax, ay, az, bx, by, bz, block) {
      for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
        for (let y = Math.min(ay, by); y <= Math.max(ay, by); y++)
          for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) m.set(x, y, z, block)
    },

    /** What was placed there ('minecraft:air' if nothing); outside the bounds, stone, as in the mod. */
    get(x, y, z) {
      if (!inside(x, y, z)) return 'minecraft:stone'
      return placed.get(key(x, y, z)) ?? 'minecraft:air'
    },

    solid(x, y, z) {
      const b = m.get(x, y, z)
      return !/^minecraft:(air|cave_air|water|lava|short_grass|tall_grass|fern|snow)\b/.test(b)
    },

    /** 0 to 99, by the place. */
    chance(x, y, z) {
      return ((seed(x, y, z) % 100) + 100) % 100
    },

    /** The walls' stone: now and then mossy or cracked (Masonry.stone). */
    stone(x, y, z) {
      const roll = m.chance(x, y, z)
      return roll < 8 ? 'minecraft:mossy_stone_bricks' : roll < 13 ? 'minecraft:cracked_stone_bricks'
        : 'minecraft:stone_bricks'
    },

    /** Stairs facing {@code facing} (north/south/east/west); top for upside down; shape for corners. */
    stairs(block, facing, { top = false, shape = 'straight' } = {}) {
      return `${block}[facing=${facing},half=${top ? 'top' : 'bottom'},shape=${shape}]`
    },

    slab(block, type = 'bottom') {
      return `${block}[type=${type}]`
    },

    log(block, axis = 'y') {
      return `${block}[axis=${axis}]`
    },

    /** A door opening to {@code outside}, two blocks tall, standing on y = 1 (Masonry.door). */
    door(x, z, outside, door, right = false) {
      const s = `facing=${outside},hinge=${right ? 'right' : 'left'}`
      m.set(x, 1, z, `${door}[${s},half=lower]`)
      m.set(x, 2, z, `${door}[${s},half=upper]`)
    },

    /** A lantern hung on a chain {@code length} long from the ceiling at {@code ceiling} (Masonry.chandelier). */
    chandelier(x, ceiling, z, length) {
      for (let y = ceiling - 1; y > ceiling - length; y--) m.set(x, y, z, 'minecraft:chain')
      m.set(x, ceiling - length, z, 'minecraft:lantern[hanging=true]')
    },

    /** A disc (filled) or a ring of {@code radius} at {@code y} (Masonry.ring). */
    ring(x, y, z, radius, block, filled) {
      for (let dx = -radius; dx <= radius; dx++)
        for (let dz = -radius; dz <= radius; dz++) {
          const d = Math.sqrt(dx * dx + dz * dz)
          if (d <= radius + 0.5 && (filled || d > radius - 0.5)) m.set(x + dx, y, z + dz, block)
        }
    },

    /** A round cone from {@code radius} at {@code y} to a point, a layer every {@code step}; its open edge of {@code edge} (Masonry.cone). */
    cone(x, y, z, radius, step, block, edge) {
      let layer = 0
      for (let r = radius; r >= 0; r--) {
        for (let s = 0; s < step; s++) {
          m.ring(x, y + layer, z, r, block, true)
          if (s === step - 1) {
            if (r === 0) m.set(x, y + layer, z, edge)
            else m.ring(x, y + layer, z, r, edge, false)
          }
          layer++
        }
      }
    },
  }
  return m
}
