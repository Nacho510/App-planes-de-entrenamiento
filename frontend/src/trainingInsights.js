// Summary of athlete-reported values; missing observations remain missing.
export function trainingInsights(rows, today) {
  const logged = rows.filter(r => r.type !== 'REST' && r.execution_id != null && String(r.session_date).slice(0, 10) <= today)
  const reported = logged.filter(r => r.rpe != null)
  const paired = reported.filter(r => r.duration_s != null)
  const load = paired.reduce((sum, r) => sum + Number(r.rpe) * Number(r.duration_s) / 60, 0)
  const byDate = [...reported].sort((a,b) => String(b.session_date).localeCompare(String(a.session_date)))
  return { count: reported.length, total: logged.length, average: reported.length ? reported.reduce((sum,r)=>sum+Number(r.rpe),0)/reported.length : null, pairedCount: paired.length, load: paired.length ? load : null, recent: byDate.slice(0,5) }
}
