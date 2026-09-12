const TOKEN_KEY = 'vax_token'
const USER_KEY = 'vax_user'

export function getToken() { return localStorage.getItem(TOKEN_KEY) }
export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}
export function setSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

async function request(method, url, body) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = 'Bearer ' + token
  const resp = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let json
  try { json = await resp.json() } catch (e) { throw new Error('服务器响应异常 (' + resp.status + ')') }
  if (!resp.ok || json.code !== 0) {
    if (resp.status === 401) { clearSession(); location.hash = '#/login' }
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

export const api = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body || {}),
  put: (url, body) => request('PUT', url, body || {})
}

export const PLAN_STATUS = {
  DUE: { text: '可预约', cls: 'ok' },
  OVERDUE: { text: '漏种待补种', cls: 'warn' },
  DONE: { text: '已完成', cls: 'done' },
  WAIT_INTERVAL: { text: '间隔未满', cls: 'muted' },
  WAIT_STOCK: { text: '等待库存', cls: 'danger' },
  CONTRA: { text: '禁忌暂缓', cls: 'danger' },
  REVIEW: { text: '待医生复核', cls: 'warn' },
  EXPIRED: { text: '超龄不补种', cls: 'muted' }
}

export const APPT_STATUS = {
  BOOKED: { text: '已预约', cls: 'ok' },
  CHECKED_IN: { text: '已到诊', cls: 'warn' },
  VACCINATED: { text: '已接种', cls: 'done' },
  CANCELLED: { text: '家长取消', cls: 'muted' },
  CANCELLED_CLINIC: { text: '门诊取消', cls: 'muted' },
  NO_SHOW: { text: '爽约', cls: 'danger' }
}

export const AEFI_STATUS = {
  OPEN: { text: '待随访', cls: 'danger' },
  FOLLOWING: { text: '随访中', cls: 'warn' },
  REPORTED: { text: '已区级上报', cls: 'ok' },
  CLOSED: { text: '已结案', cls: 'done' }
}

export const NOTIFY_TYPE = {
  OVERDUE: '漏种提醒', MIGRATION: '迁入记录', STOCK: '库存预警', CANCEL: '取消通知',
  AEFI: '异常反应', REMIND: '接种提醒', FOLLOWUP: '随访到期', CONSULT: '咨询',
  PREVAX: '接种前提醒', CONTRA: '禁忌', REVIEW: '医生复核', INFO: '通知'
}

export function fmtDate(d) {
  if (!d) return '-'
  return String(d).substring(0, 10)
}
export function fmtDateTime(d) { return d || '-' }
