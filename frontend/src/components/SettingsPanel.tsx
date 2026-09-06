import { useEffect } from 'react'
import type { CSSProperties } from 'react'
import { CheckIcon, MonitorIcon, MoonIcon, SunIcon, XIcon } from './Icons'
import { useSettings } from '../settings'
import type { AccentId, ThemeMode } from '../settings'
import type { Language, TranslationKey } from '../i18n'
import { isMacPlatform } from '../platform'

const THEMES: { id: ThemeMode; label: TranslationKey }[] = [
  { id: 'system', label: 'settings.theme.system' },
  { id: 'light', label: 'settings.theme.light' },
  { id: 'dark', label: 'settings.theme.dark' },
]

const LANGUAGES: { id: Language; label: string }[] = [
  { id: 'zh', label: '简体中文' },
  { id: 'en', label: 'English' },
]

const ACCENTS: { id: AccentId; label: TranslationKey; color: string; check: string }[] = [
  { id: 'indigo', label: 'settings.accent.indigo', color: '#6366f1', check: '#ffffff' },
  { id: 'blue', label: 'settings.accent.blue', color: '#2563eb', check: '#ffffff' },
  { id: 'sky', label: 'settings.accent.sky', color: '#0284c7', check: '#ffffff' },
  { id: 'cyan', label: 'settings.accent.cyan', color: '#0891b2', check: '#ffffff' },
  { id: 'teal', label: 'settings.accent.teal', color: '#0d9488', check: '#ffffff' },
  { id: 'emerald', label: 'settings.accent.emerald', color: '#10b981', check: '#ffffff' },
  { id: 'lime', label: 'settings.accent.lime', color: '#65a30d', check: '#1a2e05' },
  { id: 'amber', label: 'settings.accent.amber', color: '#f59e0b', check: '#451a03' },
  { id: 'orange', label: 'settings.accent.orange', color: '#f97316', check: '#431407' },
  { id: 'rose', label: 'settings.accent.rose', color: '#e11d48', check: '#ffffff' },
  { id: 'pink', label: 'settings.accent.pink', color: '#ec4899', check: '#ffffff' },
  { id: 'fuchsia', label: 'settings.accent.fuchsia', color: '#c026d3', check: '#ffffff' },
  { id: 'violet', label: 'settings.accent.violet', color: '#8b5cf6', check: '#ffffff' },
  { id: 'slate', label: 'settings.accent.slate', color: '#475569', check: '#ffffff' },
]

function ThemeIcon({ mode, size = 16 }: { mode: ThemeMode; size?: number }) {
  if (mode === 'system') return <MonitorIcon size={size} />
  if (mode === 'light') return <SunIcon size={size} />
  return <MoonIcon size={size} />
}

export default function SettingsPanel({ onClose }: { onClose: () => void }) {
  const { settings, update, t } = useSettings()
  const isMac = isMacPlatform()
  const shortcutPalette = isMac ? '⌘K' : 'Ctrl+K'
  const shortcutSettings = isMac ? '⌘,' : 'Ctrl+,'
  const shortcutTheme = isMac ? '⇧⌘D' : 'Ctrl+Shift+D'
  const shortcutPanel = isMac ? '⌘/' : 'Ctrl+/'

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
        aria-label={t('settings.title')}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="settings-head">
          <h2>{t('settings.title')}</h2>
          <button className="icon-btn" onClick={onClose} aria-label={t('settings.close')}>
            <XIcon />
          </button>
        </div>

        <section className="settings-section">
          <h3>{t('settings.language')}</h3>
          <div className="segmented">
            {LANGUAGES.map((l) => (
              <button
                key={l.id}
                className={settings.language === l.id ? 'seg active' : 'seg'}
                onClick={() => update('language', l.id)}
              >
                {l.label}
              </button>
            ))}
          </div>
        </section>

        <section className="settings-section">
          <h3>{t('settings.theme')}</h3>
          <div className="segmented">
            {THEMES.map((th) => (
              <button
                key={th.id}
                className={settings.themeMode === th.id ? 'seg active' : 'seg'}
                onClick={() => update('themeMode', th.id)}
              >
                <ThemeIcon mode={th.id} />
                {t(th.label)}
              </button>
            ))}
          </div>
        </section>

        <section className="settings-section">
          <h3>{t('settings.accent')}</h3>
          <div className="swatches">
            {ACCENTS.map((a) => (
              <button
                key={a.id}
                className={settings.accent === a.id ? 'swatch active' : 'swatch'}
                style={{ '--swatch': a.color, '--swatch-text': a.check } as CSSProperties}
                title={t(a.label)}
                aria-label={t(a.label)}
                aria-pressed={settings.accent === a.id}
                onClick={() => update('accent', a.id)}
              >
                {settings.accent === a.id && <CheckIcon size={14} />}
              </button>
            ))}
          </div>
        </section>

        <section className="settings-section">
          <h3>{t('settings.density')}</h3>
          <div className="segmented">
            <button
              className={settings.density === 'comfortable' ? 'seg active' : 'seg'}
              onClick={() => update('density', 'comfortable')}
            >
              {t('settings.density.comfortable')}
            </button>
            <button
              className={settings.density === 'compact' ? 'seg active' : 'seg'}
              onClick={() => update('density', 'compact')}
            >
              {t('settings.density.compact')}
            </button>
          </div>
        </section>

        <section className="settings-section row">
          <div>
            <h3>{t('settings.motion')}</h3>
            <p className="setting-desc">{t('settings.motion.desc')}</p>
          </div>
          <button
            role="switch"
            aria-checked={settings.reduceMotion}
            aria-label={t('settings.motion')}
            className={settings.reduceMotion ? 'switch on' : 'switch'}
            onClick={() => update('reduceMotion', !settings.reduceMotion)}
          >
            <span className="switch-knob" />
          </button>
        </section>

        <section className="settings-section">
          <h3>{t('settings.shortcuts')}</h3>
          <div className="shortcuts">
            <div className="shortcut-row">
              <span>{t('app.commandPalette')}</span>
              <kbd>{shortcutPalette}</kbd>
            </div>
            <div className="shortcut-row">
              <span>{t('settings.shortcuts.open')}</span>
              <kbd>{shortcutSettings}</kbd>
            </div>
            <div className="shortcut-row">
              <span>{t('settings.shortcuts.theme')}</span>
              <kbd>{shortcutTheme}</kbd>
            </div>
            <div className="shortcut-row">
              <span>{t('settings.shortcuts.panel')}</span>
              <kbd>{shortcutPanel}</kbd>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}
