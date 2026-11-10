import React, { useState } from 'react'
import api from './api'

export default function SecuritySettings({ onChanged, onBack }) {
  const [currentPassword,setCurrent] = useState('')
  const [newPassword,setNew] = useState('')
  const [confirm,setConfirm] = useState('')
  const [error,setError] = useState('')
  const [busy,setBusy] = useState(false)
  async function submit(e) {
    e.preventDefault(); setError('')
    if (newPassword !== confirm) return setError('Las contraseñas nuevas no coinciden.')
    if (new TextEncoder().encode(newPassword).length > 72) return setError('La contraseña nueva no puede superar 72 bytes.')
    setBusy(true)
    try { await api.post('/auth/change-password',{currentPassword,newPassword}); await onChanged() }
    catch (err) { setError(err.response?.status === 400 ? 'Verifica tu contraseña actual y elige una nueva distinta.' : err.response?.status === 409 ? 'La contraseña cambió en otra sesión. Vuelve a intentarlo.' : 'No se pudo cambiar la contraseña. Inténtalo de nuevo.') }
    finally { setBusy(false) }
  }
  return <div className="content"><div className="progress-header"><div><span className="eyebrow">TU CUENTA</span><h1>Seguridad</h1><p>Actualiza tu contraseña para mantener tu cuenta protegida.</p></div><button className="button ghost" onClick={onBack}>Volver a mi semana</button></div><section className="progress-panel"><h2>Cambiar contraseña</h2><p>Necesitarás ingresar nuevamente. Se cerrarán las sesiones activas en este servidor.</p><form onSubmit={submit} style={{maxWidth:440,marginTop:24}}><label>Contraseña actual<input type="password" autoComplete="current-password" required maxLength={100} value={currentPassword} onChange={e=>setCurrent(e.target.value)}/></label><label>Contraseña nueva<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={newPassword} onChange={e=>setNew(e.target.value)}/></label><label>Confirmar contraseña nueva<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={confirm} onChange={e=>setConfirm(e.target.value)}/></label>{error&&<div className="alert" role="alert">{error}</div>}<button className="button dark" disabled={busy}>{busy?'Guardando...':'Actualizar contraseña'}</button></form></section></div>
}
