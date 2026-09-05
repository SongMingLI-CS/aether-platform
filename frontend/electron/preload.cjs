const { contextBridge } = require('electron')

// Minimal, safe bridge: expose only what the renderer genuinely needs.
contextBridge.exposeInMainWorld('aether', {
  backendUrl: process.env.AETHER_BACKEND_URL || 'http://localhost:8080',
})
