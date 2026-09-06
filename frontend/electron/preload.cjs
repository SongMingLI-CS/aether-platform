const { contextBridge, ipcRenderer } = require('electron')

// Minimal, safe bridge: expose only what the renderer genuinely needs.
contextBridge.exposeInMainWorld('aether', {
  backendUrl: process.env.AETHER_BACKEND_URL || 'http://localhost:8080',
  platform: process.platform,
  material: process.platform === 'darwin' ? 'vibrancy' : process.platform === 'win32' ? 'mica' : 'none',

  window: {
    minimize: () => ipcRenderer.invoke('window:minimize'),
    maximize: () => ipcRenderer.invoke('window:maximize'),
    close: () => ipcRenderer.invoke('window:close'),
    isMaximized: () => ipcRenderer.invoke('window:is-maximized'),
    onMaximizedChange: (cb) => {
      const listener = (_e, value) => cb(value)
      ipcRenderer.on('window:maximized', listener)
      return () => ipcRenderer.removeListener('window:maximized', listener)
    },
  },

  settings: {
    get: () => ipcRenderer.invoke('settings:get'),
    set: (data) => ipcRenderer.invoke('settings:set', data),
  },

  onMenu: (cb) => {
    const listener = (_e, action) => cb(action)
    ipcRenderer.on('menu:action', listener)
    return () => ipcRenderer.removeListener('menu:action', listener)
  },
})
