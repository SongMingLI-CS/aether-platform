import { useEffect } from 'react'
import { XIcon } from './Icons'
import { useSettings } from '../settings'
import { isMacPlatform } from '../platform'

export default function ShortcutsPanel({ onClose }: { onClose: () => void }) {
  const { t } = useSettings()
  const isMac = isMacPlatform()

  const shortcuts = [
    { label: t('app.commandPalette'), keys: isMac ? '⌘ K' : 'Ctrl K' },
    { label: t('settings.shortcuts.open'), keys: isMac ? '⌘ ,' : 'Ctrl ,' },
    { label: t('settings.shortcuts.theme'), keys: isMac ? '⇧ ⌘ D' : 'Ctrl Shift D' },
    { label: t('settings.shortcuts.panel'), keys: isMac ? '⌘ /' : 'Ctrl /' },
  ]

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="overlay" onClick={onClose}>
      <div
        className="settings"
        role="dialog"
        aria-modal="true"
        aria-label={t('settings.shortcuts')}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="settings-head">
          <h2>{t('settings.shortcuts')}</h2>
          <button className="icon-btn" onClick={onClose} aria-label={t('settings.close')}>
            <XIcon />
          </button>
        </div>
        <div className="shortcuts">
          {shortcuts.map((s) => (
            <div className="shortcut-row" key={s.label}>
              <span>{s.label}</span>
              <span className="kbd-group">
                {s.keys.split(' ').map((k) => (
                  <kbd key={k}>{k}</kbd>
                ))}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
