// Human-readable minutes:seconds with optional hundredths; storage remains seconds.
export function parseTime(value) {
  if (value == null || value === '') return null
  const match = /^(\d{1,6}):([0-5]\d)(?:\.(\d{1,2}))?$/.exec(String(value).trim())
  if (!match) return NaN
  const seconds = Number(match[1]) * 60 + Number(match[2]) + Number(`0.${match[3] || '0'}`)
  return seconds > 0 ? Math.round(seconds * 100) / 100 : NaN
}
export function formatTime(value) {
  if (value == null || value === '') return ''
  const hundredths = Math.round(Number(value) * 100)
  const minutes = Math.floor(hundredths / 6000)
  const remainder = hundredths % 6000
  const seconds = String(Math.floor(remainder / 100)).padStart(2, '0')
  const fraction = remainder % 100
  return `${minutes}:${seconds}${fraction ? `.${String(fraction).padStart(2, '0').replace(/0$/, '')}` : ''}`
}
