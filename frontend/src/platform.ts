export function isMacPlatform(): boolean {
  if (typeof navigator === 'undefined') return false
  const platform = navigator.platform || ''
  if (platform) return /mac/i.test(platform)
  return /mac/i.test(navigator.userAgent)
}
