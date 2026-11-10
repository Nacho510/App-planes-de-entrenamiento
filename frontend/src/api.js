import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 10000 })
let token = null
export function setToken(value) { token = value }
api.interceptors.request.use(config => {
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
export default api
