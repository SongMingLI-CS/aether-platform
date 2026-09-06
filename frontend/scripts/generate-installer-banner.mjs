// Generates build/installer-side.bmp (164x314) — the NSIS welcome/finish
// page side banner. Zero dependencies; run with:
//   node scripts/generate-installer-banner.mjs
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const WIDTH = 164
const HEIGHT = 314

// Brand gradient (indigo -> deep indigo), matching the app's accent.
const TOP = [99, 102, 241] // #6366f1
const BOTTOM = [30, 27, 75] // #1e1b4b

const rowSize = Math.ceil((WIDTH * 3) / 4) * 4
const pixelDataSize = rowSize * HEIGHT
const fileSize = 54 + pixelDataSize

const buf = Buffer.alloc(fileSize)

// BITMAPFILEHEADER
buf.write('BM', 0, 'ascii')
buf.writeUInt32LE(fileSize, 2)
buf.writeUInt32LE(0, 6) // reserved
buf.writeUInt32LE(54, 10) // pixel data offset

// BITMAPINFOHEADER
buf.writeUInt32LE(40, 14)
buf.writeInt32LE(WIDTH, 18)
buf.writeInt32LE(HEIGHT, 22)
buf.writeUInt16LE(1, 26) // planes
buf.writeUInt16LE(24, 28) // bpp
buf.writeUInt32LE(0, 30) // BI_RGB
buf.writeUInt32LE(pixelDataSize, 34)

for (let y = 0; y < HEIGHT; y++) {
  const t = y / (HEIGHT - 1)
  const r = Math.round(TOP[0] + (BOTTOM[0] - TOP[0]) * t)
  const g = Math.round(TOP[1] + (BOTTOM[1] - TOP[1]) * t)
  const b = Math.round(TOP[2] + (BOTTOM[2] - TOP[2]) * t)
  const row = HEIGHT - 1 - y // BMP is bottom-up
  const offset = 54 + row * rowSize
  for (let x = 0; x < WIDTH; x++) {
    const px = offset + x * 3
    buf[px] = b // BGR order
    buf[px + 1] = g
    buf[px + 2] = r
  }
}

const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'build', 'installer-side.bmp')
mkdirSync(dirname(out), { recursive: true })
writeFileSync(out, buf)
console.log('Wrote', out, `(${WIDTH}x${HEIGHT}, ${fileSize} bytes)`)
