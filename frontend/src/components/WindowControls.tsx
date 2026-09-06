import { useEffect, useState } from 'react'
import { useSettings } from '../settings'

function MinimizeIcon() {
  return (
    <svg width={12} height={12} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.3} strokeLinecap="round" aria-hidden="true">
      <path d="M3 8h10" />
    </svg>
  )
}

function MaximizeIcon() {
  return (
    <svg width={12} height={12} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.3} aria-hidden="true">
      <rect x="3.5" y="3.5" width="9" height="9" rx="0.5" />
    </svg>
  )
}

function RestoreIcon() {
  return (
    <svg width={12} height={12} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.3} aria-hidden="true">
      <rect x="3.5" y="5.5" width="8" height="8" rx="0.5" />
      <path d="M5.5 5.5V3.5h7v7h-2" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg width={12} height={12} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.3} strokeLinecap="round" aria-hidden="true">
      <path d="M4 4l8 8M12 4l-8 8" />
    </svg>
  )
}

export default function WindowControls() {
  const { t } = useSettings()
  const [maximized, setMaximized] = useState(false)

  useEffect(() => {
    const w = window.aether?.window
    if (!w) return
    w.isMaximized().then(setMaximized).catch(() => {})
    return w.onMaximizedChange(setMaximized)
  }, [])

  const w = window.aether?.window
  if (!w) return null

  return (
    <div className="window-controls">
      <button className="wc" onClick={() => w.minimize()} aria-label={t('window.minimize')} title={t('window.minimize')}>
        <MinimizeIcon />
      </button>
      <button
        className="wc"
        onClick={() => w.maximize()}
        aria-label={maximized ? t('window.restore') : t('window.maximize')}
        title={maximized ? t('window.restore') : t('window.maximize')}
      >
        {maximized ? <RestoreIcon /> : <MaximizeIcon />}
      </button>
      <button className="wc close" onClick={() => w.close()} aria-label={t('window.close')} title={t('window.close')}>
        <CloseIcon />
      </button>
    </div>
  )
}
