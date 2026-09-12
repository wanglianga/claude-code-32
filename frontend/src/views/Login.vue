<template>
  <div class="login-wrap">
    <div class="login-card">
      <h1>社区儿童疫苗补种提醒与<br/>异常反应随访平台</h1>
      <p class="muted">城市社区预防接种门诊 · 演示环境</p>
      <label class="field"><span>用户名</span><input v-model="username" @keyup.enter="login" placeholder="如 parent1" /></label>
      <label class="field"><span>密码</span><input type="password" v-model="password" @keyup.enter="login" /></label>
      <div v-if="error" class="badge danger" style="margin-bottom: 10px">{{ error }}</div>
      <button style="width: 100%; padding: 10px;" @click="login">登 录</button>

      <div class="role-chips">
        <span class="chip" v-for="r in demo" :key="r.u" @click="fill(r)">
          <b>{{ r.label }}</b>：{{ r.u }} / {{ r.p }}
        </span>
      </div>
      <p class="muted small mt16">点击任意角色可自动填充账号密码。</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, setSession } from '../api'
import { homeFor } from '../router'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')

const demo = [
  { label: '家长-周敏', u: 'parent1', p: 'parent123' },
  { label: '家长-吴芳', u: 'parent2', p: 'parent123' },
  { label: '预防接种医生', u: 'doctor', p: 'doctor123' },
  { label: '接种护士', u: 'nurse', p: 'nurse123' },
  { label: '随访人员', u: 'followup', p: 'follow123' },
  { label: '管理员', u: 'admin', p: 'admin123' }
]
function fill(r) { username.value = r.u; password.value = r.p; error.value = '' }

async function login() {
  error.value = ''
  try {
    const data = await api.post('/api/auth/login', { username: username.value, password: password.value })
    setSession(data.token, data.user)
    router.push(homeFor(data.user.role))
  } catch (e) {
    error.value = e.message
  }
}
</script>
