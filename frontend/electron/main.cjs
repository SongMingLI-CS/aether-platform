const {
  app,
  BrowserWindow,
  Menu,
  Tray,
  Notification,
  ipcMain,
  nativeImage,
} = require('electron')
const path = require('node:path')
const fs = require('node:fs')

const BACKEND_URL = process.env.AETHER_BACKEND_URL || 'http://localhost:8080'
const DEV_SERVER_URL = process.env.VITE_DEV_SERVER_URL || ''
const IS_MAC = process.platform === 'darwin'

let mainWindow = null
let tray = null
let isQuitting = false

// ---- Persisted settings: a JSON file in userData, independent of browser cache ----
function settingsFile() {
  return path.join(app.getPath('userData'), 'settings.json')
}

function readSettings() {
  try {
    return JSON.parse(fs.readFileSync(settingsFile(), 'utf-8'))
  } catch {
    return null
  }
}

function writeSettings(data) {
  try {
    fs.mkdirSync(path.dirname(settingsFile()), { recursive: true })
    fs.writeFileSync(settingsFile(), JSON.stringify(data ?? {}, null, 2))
    return true
  } catch (err) {
    console.error('Failed to persist settings:', err)
    return false
  }
}

// ---- Native menu & application-level keyboard shortcuts ----
function sendToRenderer(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload)
  }
}

function buildMenu() {
  const template = [
    ...(IS_MAC
      ? [{
          label: app.name,
          submenu: [
            { role: 'about' },
            { type: 'separator' },
            { role: 'services' },
            { type: 'separator' },
            { role: 'hide' },
            { role: 'hideOthers' },
            { role: 'unhide' },
            { type: 'separator' },
            { role: 'quit' },
          ],
        }]
      : []),
    {
      label: '文件',
      submenu: [
        { label: '命令面板', accelerator: 'CmdOrCtrl+K', click: () => sendToRenderer('menu:action', 'open-palette') },
        { label: '设置', accelerator: 'CmdOrCtrl+,', click: () => sendToRenderer('menu:action', 'open-settings') },
        { type: 'separator' },
        IS_MAC ? { role: 'close', label: '关闭窗口' } : { role: 'quit', label: '退出' },
      ],
    },
    {
      label: '编辑',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'selectAll' },
      ],
    },
    {
      label: '视图',
      submenu: [
        { label: '切换主题', accelerator: 'CmdOrCtrl+Shift+D', click: () => sendToRenderer('menu:action', 'toggle-theme') },
        { label: '快捷键', accelerator: 'CmdOrCtrl+/', click: () => sendToRenderer('menu:action', 'open-shortcuts') },
        { type: 'separator' },
        { role: 'reload' },
        { role: 'toggleDevTools' },
        { type: 'separator' },
        { role: 'togglefullscreen' },
      ],
    },
    {
      label: '窗口',
      submenu: [
        { role: 'minimize' },
        { role: 'zoom' },
        ...(IS_MAC ? [{ role: 'front' }] : [{ role: 'close' }]),
      ],
    },
  ]
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

// ---- Frameless / custom-titlebar window ----
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 760,
    minWidth: 760,
    minHeight: 560,
    title: 'Aether Platform',
    // Windows/Linux: fully frameless (HTML titlebar). macOS: hidden title bar, keep native traffic lights.
    frame: IS_MAC ? undefined : false,
    titleBarStyle: IS_MAC ? 'hiddenInset' : undefined,
    // Native materials: macOS vibrancy / Windows 11 Mica (glassmorphism behind translucent UI).
    vibrancy: IS_MAC ? 'under-window' : undefined,
    visualEffectState: IS_MAC ? 'active' : undefined,
    backgroundMaterial: process.platform === 'win32' ? 'mica' : undefined,
    show: false,
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

  // Show after first paint → avoids a white/dark flash (FOUC).
  mainWindow.once('ready-to-show', () => mainWindow.show())

  mainWindow.on('maximize', () => sendToRenderer('window:maximized', true))
  mainWindow.on('unmaximize', () => sendToRenderer('window:maximized', false))

  // Windows/Linux: closing hides to the tray so background notifications keep working.
  mainWindow.on('close', (e) => {
    if (!isQuitting && !IS_MAC) {
      e.preventDefault()
      mainWindow.hide()
    }
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

function showWindow() {
  if (!mainWindow) {
    createWindow()
    return
  }
  if (mainWindow.isMinimized()) mainWindow.restore()
  mainWindow.show()
  mainWindow.focus()
}

// ---- System tray ----
function createTray() {
  let image = nativeImage.createFromPath(path.join(app.getAppPath(), 'build', 'icon.png'))
  if (image.isEmpty()) {
    image = nativeImage.createFromPath(path.join(__dirname, '..', 'build', 'icon.png'))
  }
  if (!image.isEmpty()) image = image.resize({ width: 18, height: 18 })

  tray = new Tray(image)
  tray.setToolTip('Aether Platform')
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '显示 Aether Platform', click: showWindow },
    { type: 'separator' },
    { label: '退出', click: () => { isQuitting = true; app.quit() } },
  ]))
  tray.on('click', showWindow)
}

// ---- Native notifications + SSE stream (Epic 3) ----
function showNotification(connection) {
  if (!Notification.isSupported()) return
  const pct = ((connection.similarity || 0) * 100).toFixed(1)
  const body = `${connection.sourceText || ''} → ${connection.targetText || ''}（${pct}%）`
  new Notification({ title: '发现新的知识连接', body }).show()
}

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
    setTimeout(subscribeToConnections, 5000)
  }
}

// ---- IPC (window controls + settings persistence) ----
ipcMain.handle('window:minimize', () => mainWindow?.minimize())
ipcMain.handle('window:maximize', () => {
  if (!mainWindow) return false
  if (mainWindow.isMaximized()) mainWindow.unmaximize()
  else mainWindow.maximize()
  return mainWindow.isMaximized()
})
ipcMain.handle('window:is-maximized', () => !!mainWindow?.isMaximized())
ipcMain.handle('window:close', () => mainWindow?.close())
ipcMain.handle('settings:get', () => readSettings())
ipcMain.handle('settings:set', (_e, data) => writeSettings(data))

// ---- Lifecycle ----
app.whenReady().then(() => {
  buildMenu()
  createWindow()
  createTray()
  subscribeToConnections()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
    else showWindow()
  })
})

app.on('before-quit', () => {
  isQuitting = true
})

app.on('window-all-closed', () => {
  // Keep running in the tray / dock; quit explicitly via menu or tray.
})
