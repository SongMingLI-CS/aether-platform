const { app, BrowserWindow, Notification } = require('electron')
const path = require('node:path')

const BACKEND_URL = process.env.AETHER_BACKEND_URL || 'http://localhost:8080'
const DEV_SERVER_URL = process.env.VITE_DEV_SERVER_URL || ''

let mainWindow = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 760,
    title: 'Aether Platform',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  })

  if (DEV_SERVER_URL) {
    mainWindow.loadURL(DEV_SERVER_URL)
  } else {
    mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'))
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

// The minimal desktop popup (Epic 3): "新笔记 → 旧笔记 → 相似度".
function showNotification(connection) {
  if (!Notification.isSupported()) return
  const pct = ((connection.similarity || 0) * 100).toFixed(1)
  const body = `${connection.sourceText || ''} → ${connection.targetText || ''}（${pct}%）`
  new Notification({ title: '发现新的知识连接', body }).show()
}

// Minimal SSE frame parser: split events on blank lines, then event:/data:.
function processSseChunk(buffer) {
  const parts = buffer.split(/\r?\n\r?\n/)
  const remainder = parts.pop() || ''
  for (const part of parts) {
    let eventName = 'message'
    let data = ''
    for (const line of part.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trim()
    }
    if (eventName === 'connection-discovered' && data) {
      try {
        showNotification(JSON.parse(data))
      } catch (err) {
        console.error('Bad SSE payload:', err)
      }
    }
  }
  return remainder
}

async function subscribeToConnections() {
  const url = `${BACKEND_URL}/api/v1/connections/stream`
  try {
    const res = await fetch(url, { headers: { Accept: 'text/event-stream' } })
    if (!res.ok || !res.body) throw new Error(`SSE status ${res.status}`)
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = processSseChunk(buffer)
    }
  } catch (err) {
    console.error('SSE stream error:', err.message)
  } finally {
    // Reconnect after a short back-off (stream ended or backend unreachable).
    setTimeout(subscribeToConnections, 5000)
  }
}

app.whenReady().then(() => {
  createWindow()
  subscribeToConnections()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
