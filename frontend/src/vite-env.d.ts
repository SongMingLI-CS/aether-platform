/// <reference types="vite/client" />

interface Window {
  aether?: {
    backendUrl?: string
    platform?: string
    material?: string
    window?: {
      minimize: () => Promise<void>
      maximize: () => Promise<boolean>
      close: () => Promise<void>
      isMaximized: () => Promise<boolean>
      onMaximizedChange: (cb: (maximized: boolean) => void) => () => void
    }
    settings?: {
      get: () => Promise<Record<string, unknown> | null>
      set: (data: unknown) => Promise<boolean>
    }
    onMenu?: (cb: (action: string) => void) => () => void
  }
}
