import React, { useState } from 'react'
import api from './api'

export default function AccountForm({ athlete = false, onClose, onCreated }) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError('')
    if (password !== confirm) return setError('Las contraseñas no coinciden.')
    if (new TextEncoder().encode(password).length > 72) return setError('La contraseña no puede superar 72 bytes.')
    setBusy(true)
    try {
      await api.post(athlete ? '/athletes' : '/auth/register-coach', { name, email, password })
      onCreated()
    } catch (err) {
      setError(err.response?.status === 409 ? 'No se pudo crear la cuenta. Revisa el correo ingresado.' : err.response?.status === 400 ? 'Revisa los datos y la contraseña.' : 'No se pudo crear la cuenta. Verifica la conexión.')
    } finally { setBusy(false) }
  }
  return <div className="modal-backdrop"><section className="modal" role="dialog" aria-modal="true" aria-label={athlete ? 'Crear atleta' : 'Crear cuenta'}>
    <div className="modal-head"><div><span className="eyebrow">RITMO / CUENTAS</span><h2>{athlete ? 'Agregar atleta' : 'Crear cuenta de entrenador'}</h2></div><button type="button" className="close" onClick={onClose} aria-label="Cerrar">×</button></div>
    <p>{athlete ? 'La cuenta quedará vinculada a tu perfil de entrenador.' : 'Crea tu espacio para planificar entrenamientos.'}</p>
    <form onSubmit={submit}>
      <label>Nombre completo<input required maxLength={100} value={name} onChange={e => setName(e.target.value)} autoComplete="name" /></label>
      <label>Correo electrónico<input required type="email" maxLength={254} value={email} onChange={e => setEmail(e.target.value)} autoComplete="email" /></label>
      <label>Contraseña<input required type="password" minLength={12} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} autoComplete="new-password" /></label>
      <label>Confirmar contraseña<input required type="password" minLength={12} maxLength={72} value={confirm} onChange={e => setConfirm(e.target.value)} autoComplete="new-password" /></label>
      <p className="footnote">Usa al menos 12 caracteres. {athlete && 'Comparte la contraseña con el atleta de forma privada.'}</p>
      {error && <div className="alert" role="alert">{error}</div>}
      <div className="modal-actions"><button type="button" className="button ghost" onClick={onClose}>Cancelar</button><button className="button dark" disabled={busy}>{busy ? 'Guardando...' : 'Crear cuenta'}</button></div>
    </form>
  </section></div>
}
