import { useSettings } from '../settings'

export function SkeletonList({ rows = 5 }: { rows?: number }) {
  const { t } = useSettings()
  return (
    <div className="skeleton-list" role="status" aria-live="polite">
      <span className="sr-only">{t('common.loading')}</span>
      {Array.from({ length: rows }).map((_, i) => (
        <div className="skeleton-item" key={i}>
          <div className="skeleton line w-70" />
          <div className="skeleton line w-40" />
        </div>
      ))}
    </div>
  )
}
