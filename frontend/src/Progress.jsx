import React, { useEffect, useMemo, useState } from 'react'
import api from './api'
import { trainingInsights } from './trainingInsights'

const labels = { EASY:'Rodaje', FARTLEK:'Cambios de ritmo', INTERVALS:'Series', STRENGTH:'Fuerza', REST:'Descanso' }
const dateText = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
const shift = (date, days) => { const d = new Date(date + 'T12:00:00'); d.setDate(d.getDate()+days); return dateText(d) }
const num = n => new Intl.NumberFormat('es-CR',{maximumFractionDigits:1}).format(n)
const pace = n => { const t=Math.round(n); return `${Math.floor(t/60)}:${String(t%60).padStart(2,'0')}` }

export default function Progress({ user, athletes, athleteId, setAthleteId, onBack, onOpen }) {
  const today = dateText(new Date())
  const [from,setFrom] = useState(shift(today,-27)); const [to,setTo] = useState(today)
  const [type,setType] = useState('ALL'); const [rows,setRows] = useState([]); const [loading,setLoading] = useState(false); const [error,setError] = useState('')
  useEffect(() => {
    if (user.role==='COACH' && !athleteId) { setRows([]); return }
    if (!from || !to || from>to || (Date.parse(to)-Date.parse(from))/86400000>365) { setRows([]); setError('Selecciona un periodo válido de hasta 366 días.'); return }
    let active=true; setLoading(true); setError(''); setRows([])
    api.get('/progress',{params:{from,to,...(user.role==='COACH'?{athleteId}:{})}}).then(r=>{if(active)setRows(r.data)}).catch(()=>{if(active)setError('No fue posible cargar el progreso. Inténtalo de nuevo.')}).finally(()=>{if(active)setLoading(false)})
    return ()=>{active=false}
  },[from,to,athleteId,user.role])
  const stats=useMemo(()=>{
    const selected=rows.filter(r=>type==='ALL'||r.type===type)
    const due=selected.filter(r=>r.type!=='REST' && String(r.session_date).slice(0,10)<=today)
    const logged=due.filter(r=>r.execution_id!=null)
    const runs=selected.filter(r=>['EASY','FARTLEK','INTERVALS'].includes(r.type) && r.execution_id!=null && String(r.session_date).slice(0,10)<=today)
    const withDistance=runs.filter(r=>r.distance_m!=null)
    const distance=withDistance.reduce((sum,r)=>sum+Number(r.distance_m),0)
    const paired=runs.filter(r=>r.distance_m!=null && r.duration_s!=null)
    const pairedDistance=paired.reduce((sum,r)=>sum+Number(r.distance_m),0)
    const pairedDuration=paired.reduce((sum,r)=>sum+Number(r.duration_s),0)
    const time=selected.filter(r=>r.execution_id!=null && r.type!=='REST' && String(r.session_date).slice(0,10)<=today && r.duration_s!=null).reduce((sum,r)=>sum+Number(r.duration_s),0)
    const weeks={}; for(const r of withDistance){const date=String(r.session_date).slice(0,10);const d=new Date(date+'T12:00:00');const key=shift(date,-((d.getDay()+6)%7)); weeks[key]=(weeks[key]||0)+Number(r.distance_m)/1000}
    return {due,logged,runs,withDistance,distance,paired,pairedDistance,pairedDuration,time,weeks:Object.entries(weeks).sort(([a],[b])=>a.localeCompare(b)),selected}
  },[rows,type,today])
  const max=Math.max(1,...stats.weeks.map(([,v])=>v))
  const effort = trainingInsights(stats.selected, today)
  return <div className="content progress-page"><div className="progress-header"><div><span className="eyebrow">DATOS QUE CUENTAN UNA HISTORIA</span><h1>{user.role==='COACH'?'Progreso de atletas':'Mi progreso'}</h1><p>Seguimiento de lo planificado y lo registrado.</p></div><button className="button ghost" onClick={onBack}>← Volver a mi semana</button></div>
    <div className="progress-filters">{user.role==='COACH'&&<label>Atleta<select value={athleteId} onChange={e=>setAthleteId(e.target.value)}>{!athletes.length&&<option value="">Sin atletas asignados</option>}{athletes.map(a=><option key={a.id} value={a.id}>{a.name}</option>)}</select></label>}<label>Desde<input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label><label>Hasta<input type="date" value={to} onChange={e=>setTo(e.target.value)}/></label><label>Tipo de sesión<select value={type} onChange={e=>setType(e.target.value)}><option value="ALL">Todos</option>{Object.entries(labels).map(([k,v])=><option key={k} value={k}>{v}</option>)}</select></label></div>
    {error&&<div className="alert" role="alert">{error}</div>}{loading?<div className="empty">Cargando progreso...</div>:<>
    <div className="progress-cards"><div><span>SESIONES CON FEEDBACK</span><strong>{stats.due.length?`${Math.round(stats.logged.length/stats.due.length*100)}%`:'—'}</strong><small>{stats.logged.length} de {stats.due.length} sesiones previstas hasta hoy</small></div><div><span>DISTANCIA CORRIDA</span><strong>{stats.withDistance.length?num(stats.distance/1000):'—'} <i>km</i></strong><small>{stats.withDistance.length} de {stats.runs.length} carreras con distancia</small></div><div><span>TIEMPO REGISTRADO</span><strong>{stats.selected.some(r=>r.execution_id!=null && r.type!=='REST' && String(r.session_date).slice(0,10)<=today && r.duration_s!=null)?`${Math.floor(stats.time/3600)}h ${String(Math.floor(stats.time%3600/60)).padStart(2,'0')}min`:'—'}</strong><small>Sesiones con tiempo registrado</small></div><div><span>RITMO MEDIO GLOBAL</span><strong>{stats.pairedDistance?pace(stats.pairedDuration*1000/stats.pairedDistance):'—'} <i>/km</i></strong><small>{stats.paired.length} carreras con tiempo y distancia</small></div></div>
    <div className="progress-panel"><div><span className="eyebrow">VOLUMEN DE CARRERA</span><h2>Kilómetros por semana</h2><p>Distancia registrada en rodajes, cambios de ritmo y series.</p></div>{stats.weeks.length?<div className="progress-bars">{stats.weeks.map(([date,km])=><div className="bar-row" key={date}><span>{date}</span><div className="bar-track"><div style={{width:`${km/max*100}%`}} /></div><strong>{num(km)} km</strong></div>)}</div>:<div className="empty">Todavía no hay distancias de carrera registradas en este periodo.</div>}</div>
    <div className="progress-panel"><span className="eyebrow">ESFUERZO PERCIBIDO</span><h2>Cómo se sintió el entrenamiento</h2><p>Indicadores basados en el esfuerzo subjetivo registrado por el atleta, del 1 al 10.</p><div className="progress-cards effort-cards"><div><span>ESFUERZO MEDIO</span><strong>{effort.average==null?'—':num(effort.average)} <i>/10</i></strong><small>{effort.count} de {effort.total} sesiones con feedback incluyen esfuerzo</small></div><div><span>CARGA INTERNA ESTIMADA</span><strong>{effort.load==null?'—':num(effort.load)} <i>u.a.</i></strong><small>{effort.pairedCount} sesiones con tiempo y esfuerzo</small></div></div><p>La carga suma minutos × esfuerzo de cada sesión con ambos datos. Es orientativa, depende del registro del atleta y no es una medida de recuperación ni un diagnóstico.</p>{effort.recent.length>0&&<div className="history-list">{effort.recent.map(r=><button type="button" className="history-row" key={r.id} onClick={()=>onOpen(r.id)}><span>{String(r.session_date).slice(0,10)} · {labels[r.type]}</span><strong>{r.title}</strong><small>Esfuerzo {r.rpe}/10 →</small></button>)}</div>}</div>
    <div className="progress-panel"><span className="eyebrow">HISTORIAL</span><h2>Sesiones del periodo</h2><p>Selecciona una sesión para ver su plan y resultados.</p>{stats.selected.length?<div className="history-list">{[...stats.selected].reverse().map(r=><button type="button" className="history-row" key={r.id} onClick={()=>onOpen(r.id)}><span>{String(r.session_date).slice(0,10)} · {labels[r.type]}</span><strong>{r.title}</strong><small>{r.execution_id!=null?'Con feedback':'Sin feedback'} →</small></button>)}</div>:<div className="empty">No hay sesiones para los filtros seleccionados.</div>}</div>
    <div className="progress-panel"><span className="eyebrow">CÓMO LEER TUS DATOS</span><h2>Sobre estas métricas</h2><p>El porcentaje cuenta sesiones con algún feedback, no mide si se alcanzaron los ritmos. Excluye descansos y fechas futuras. El ritmo global usa solo carreras con tiempo y distancia, ponderado por distancia. Los datos faltantes no se tratan como cero. Las fechas corresponden al día planificado.</p></div>
    </>}</div>
}
