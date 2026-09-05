// Generates the Aether app icon (build/icon.png, 1024x1024) with zero external
// dependencies. The icon is an "atom" motif on a rounded dark-indigo tile,
// drawn with signed-distance smoothing. Run with: node scripts/generate-icon.mjs
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'

const S = 1024
const cx = S / 2
const cy = S / 2

// Palette
const c0 = [15, 10, 46] // deep indigo (top-left)
const c1 = [43, 33, 92] // lighter indigo (bottom-right)
const orbitCol = [165, 180, 252] // indigo-300
const nucleusCol = [129, 140, 248] // indigo-400
const electronCol = [244, 114, 182] // pink-400

// Atom geometry
const orbits = [
  [340, 132, 0],
  [340, 132, 60],
  [340, 132, 120],
]
const orbitThickness = 0.05
const nucleusR = 74
const electronR = 34
const electronAngles = [45, 180, 315]

const margin = 24
const half = (S - margin * 2) / 2
const radius = 200

function clamp01(x) {
  return x < 0 ? 0 : x > 1 ? 1 : x
}
function smoothstep(e0, e1, x) {
  const t = clamp01((x - e0) / (e1 - e0))
  return t * t * (3 - 2 * t)
}
function lerp(a, b, t) {
  return a + (b - a) * t
}
function rotate(x, y, deg) {
  const r = (deg * Math.PI) / 180
  const c = Math.cos(r)
  const s = Math.sin(r)
  return [x * c - y * s, x * s + y * c]
}
function roundedRectDist(px, py) {
  const qx = Math.abs(px - cx) - (half - radius)
  const qy = Math.abs(py - cy) - (half - radius)
  const outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
  const inside = Math.min(Math.max(qx, qy), 0)
  return outside + inside - radius
}
function ellipseCoverage(px, py, a, b, rotDeg, edge) {
  const [u, v] = rotate(px - cx, py - cy, -rotDeg)
  const f = 1 - (u / a) ** 2 - (v / b) ** 2
  return smoothstep(-edge, edge, f)
}
function circleCoverage(px, py, x, y, r, edge) {
  const d = Math.hypot(px - x, py - y)
  return smoothstep(edge, -edge, d - r)
}
function electronPos(a, b, rotDeg, phiDeg) {
  const p = (phiDeg * Math.PI) / 180
  const [rx, ry] = rotate(a * Math.cos(p), b * Math.sin(p), rotDeg)
  return [cx + rx, cy + ry]
}

const stride = S * 4
const raw = Buffer.alloc(S * (stride + 1))
const pixels = new Uint8Array(S * stride)

for (let y = 0; y < S; y++) {
  for (let x = 0; x < S; x++) {
    const bgAlpha = smoothstep(1.5, -1.5, roundedRectDist(x, y))
    if (bgAlpha <= 0) continue

    const t = (x + y) / (2 * S)
    let r = lerp(c0[0], c1[0], t)
    let g = lerp(c0[1], c1[1], t)
    let b = lerp(c0[2], c1[2], t)

    for (const [oa, ob, rot] of orbits) {
      const outer = ellipseCoverage(x, y, oa, ob, rot, 0.006)
      const inner = ellipseCoverage(x, y, oa * (1 - orbitThickness), ob * (1 - orbitThickness), rot, 0.006)
      const cov = outer * (1 - inner)
      r = lerp(r, orbitCol[0], cov)
      g = lerp(g, orbitCol[1], cov)
      b = lerp(b, orbitCol[2], cov)
    }

    const nc = circleCoverage(x, y, cx, cy, nucleusR, 1.5)
    r = lerp(r, nucleusCol[0], nc)
    g = lerp(g, nucleusCol[1], nc)
    b = lerp(b, nucleusCol[2], nc)

    for (let i = 0; i < orbits.length; i++) {
      const [ex, ey] = electronPos(orbits[i][0], orbits[i][1], orbits[i][2], electronAngles[i])
      const ec = circleCoverage(x, y, ex, ey, electronR, 1.5)
      r = lerp(r, electronCol[0], ec)
      g = lerp(g, electronCol[1], ec)
      b = lerp(b, electronCol[2], ec)
    }

    const idx = y * stride + x * 4
    pixels[idx] = Math.round(r)
    pixels[idx + 1] = Math.round(g)
    pixels[idx + 2] = Math.round(b)
    pixels[idx + 3] = Math.round(bgAlpha * 255)
  }
}

for (let y = 0; y < S; y++) {
  raw[y * (stride + 1)] = 0
  raw.set(pixels.subarray(y * stride, y * stride + stride), y * (stride + 1) + 1)
}

let crcTable = null
function crc32(buf) {
  if (!crcTable) {
    crcTable = new Int32Array(256)
    for (let n = 0; n < 256; n++) {
      let c = n
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
      crcTable[n] = c
    }
  }
  let c = 0xffffffff
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length, 0)
  const typeBuf = Buffer.from(type, 'ascii')
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0)
  return Buffer.concat([len, typeBuf, data, crc])
}

const ihdr = Buffer.alloc(13)
ihdr.writeUInt32BE(S, 0)
ihdr.writeUInt32BE(S, 4)
ihdr[8] = 8
ihdr[9] = 6
ihdr[10] = 0
ihdr[11] = 0
ihdr[12] = 0

const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', ihdr),
  chunk('IDAT', deflateSync(raw)),
  chunk('IEND', Buffer.alloc(0)),
])

mkdirSync('build', { recursive: true })
writeFileSync('build/icon.png', png)
console.log('icon written: build/icon.png (' + png.length + ' bytes)')
