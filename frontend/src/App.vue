<template>
  <router-view v-if="$route.path === '/login'" />
  <template v-else>
    <div class="topbar">
      <span class="brand">🏥 社区儿童疫苗补种与异常反应随访平台</span>
      <span class="spacer"></span>
      <div style="position: relative;">
        <span class="bell" @click="showBell = !showBell">🔔<span v-if="unread" class="dot">{{ unread > 99 ? '99+' : unread }}</span></span>
        <div v-if="showBell" class="notif-panel" @click.stop>
          <div class="row" style="justify-content: space-between; padding: 4px 8px 8px;">
            <b>状态推送与提醒</b>
            <button class="btn-sm btn-ghost" @click="readAll">全部已读</button>
          </div>
          <div v-if="!notifications.length" class="muted small" style="padding: 12px;">暂无通知</div>
          <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{ unread: !n.isRead }" @click="open(n)">
            <div class="t">
              <span :class="'badge ' + levelCls(n.level)" style="margin-right:6px">{{ typeName(n.type) }}</span>
              {{ n.title }}
            </div>
            <div class="c">{{ n.content }}</div>
            <div class="c">{{ n.createdAt }}</div>
          </div>
        </div>
      </div>
      <span class="user">{{ user?.name }}（{{ user?.roleName }}）</span>
      <button @click="logout">退出</button>
    </div>
    <router-view />
  </template>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api, getUser, clearSession, NOTIFY_TYPE } from './api'

const router = useRouter()
const user = ref(getUser())
const notifications = ref([])
const unread = ref(0)
const showBell = ref(false)
let timer

async function load() {
  try {
    const [list, cnt] = await Promise.all([
      api.get('/api/notifications'),
      api.get('/api/notifications/unread-count')
    ])
    notifications.value = list
    unread.value = cnt.count
  } catch (e) { /* 忽略轮询错误 */ }
}
function typeName(t) { return NOTIFY_TYPE[t] || t }
function levelCls(l) { return l === 'URGENT' ? 'danger' : l === 'WARN' ? 'warn' : 'muted' }
function open(n) {
  if (!n.isRead) api.post('/api/notifications/' + n.id + '/read').then(load)
  showBell.value = false
}
async function readAll() { await api.post('/api/notifications/read-all'); load() }
function logout() { clearSession(); router.push('/login') }

onMounted(() => { load(); timer = setInterval(load, 15000) })
onUnmounted(() => clearInterval(timer))
</script>
