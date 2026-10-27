// Walks a drafted building as a player would (2 tall, steps up 1, drops up to 3, stairs/slabs/beds as steps), from
// its entrances, and reports the targets (rooms, doors, stair landings) it cannot reach. Use with masonry.mjs:
//   import { report, cellsOf } from '<skill>/walk.mjs'
//   report('Hall', m, [[x, 1, z]], { x0, x1, z0, z1, y1 }, [{ name: 'kitchen', cells: cellsOf(x0, z0, x1, z1, 1) }])

// ---------------------------------------------------------------- walking

const id = (b) => b.replace(/\[.*$/, '')
const OPEN = /^minecraft:(air|cave_air|.*_door|.*_carpet|candle|.*_wall_banner|short_grass|allium|azure_bluet|oxeye_daisy|cornflower)$/
const STEPPY = /_(stairs|slab|bed)$/
function passable(m, x, y, z) {
  const b = m.get(x, y, z)
  if (/lantern\[hanging=true\]/.test(b) || /^minecraft:chain/.test(b)) return true // hung from above, not in the way at the feet
  return OPEN.test(id(b))
}
function solid(m, x, y, z) { const b = id(m.get(x, y, z)); return !OPEN.test(b) && !/lantern|chain/.test(b) }
/** A player stands with feet in (x, y, z): room for two, and something under the feet (or a step in the cell). */
const step = (b) => STEPPY.test(id(b)) && !/type=top|half=top|type=double/.test(b)
function stand(m, x, y, z) {
  const here = m.get(x, y, z)
  if (step(here)) return passable(m, x, y + 1, z) && passable(m, x, y + 2, z)
  return passable(m, x, y, z) && passable(m, x, y + 1, z) && solid(m, x, y - 1, z) && !/campfire|magma/.test(id(m.get(x, y - 1, z)))
}
export function walk(m, starts, box) {
  const seen = new Set(), q = []
  const key = (x, y, z) => `${x},${y},${z}`
  for (const s of starts) if (stand(m, ...s)) { seen.add(key(...s)); q.push(s) }
  while (q.length) {
    const [x, y, z] = q.shift()
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = x + dx, nz = z + dz
      if (nx < box.x0 || nx > box.x1 || nz < box.z0 || nz > box.z1) continue
      for (const dy of [0, 1, -1, -2, -3]) {
        const ny = y + dy
        if (ny < 0 || ny > box.y1) continue
        if (dy === 1 && !passable(m, x, y + 2, z) && !step(m.get(x, y, z))) continue // a jump needs room overhead
        if (dy < 0) { let clear = true; for (let k = 0; k > dy; k--) clear &&= passable(m, nx, y + k, nz) && passable(m, nx, y + k + 1, nz); if (!clear) continue }
        if (stand(m, nx, ny, nz)) {
          const k = key(nx, ny, nz)
          if (!seen.has(k)) { seen.add(k); q.push([nx, ny, nz]) }
          break
        }
      }
    }
  }
  return seen
}

export function report(name, m, starts, box, targets) {
  const seen = walk(m, starts, box)
  const reached = (t) => t.cells.some(([x, y, z]) => seen.has(`${x},${y},${z}`))
  const missing = targets.filter((t) => !reached(t))
  console.log(`${name}: ${targets.length - missing.length}/${targets.length} reached`)
  for (const t of missing) console.log('   NOT REACHED:', t.name)
}

export const cellsOf = (x0, z0, x1, z1, y) => {
  const out = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) out.push([x, y, z])
  return out
}

