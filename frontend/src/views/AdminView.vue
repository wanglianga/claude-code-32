<template>
  <div class="page">
    <div v-if="toast" class="toast" :class="{ err: toastErr }">{{ toast }}</div>

    <div class="card">
      <h2 style="margin:0">门诊管理与总览</h2>
      <span class="muted small">库存与冷链、门诊容量、医生排班，以及漏种/异常反应的全局态势</span>
    </div>

    <div class="kpi mb8">
      <div class="item"><div class="num">{{ kpi.overdue }}</div><div class="lbl">漏种剂次</div></div>
      <div class="item"><div class="num">{{ kpi.due }}</div><div class="lbl">可预约剂次</div></div>
      <div class="item"><div class="num">{{ kpi.lowStock }}</div><div class="lbl">低库存批号</div></div>
      <div class="item"><div class="num">{{ kpi.brokenCold }}</div><div class="lbl">冷链异常批号</div></div>
      <div class="item"><div class="num">{{ kpi.openAefi }}</div><div class="lbl">未结案 AEFI</div></div>
      <div class="item"><div class="num">{{ kpi.todayAppt }}</div><div class="lbl">今日预约</div></div>
    </div>

    <div class="tabs" v-if="isAdmin">
      <div class="tab" :class="{ active: tab === 'stock' }" @click="tab = 'stock'">库存与冷链</div>
      <div class="tab" :class="{ active: tab === 'clinic' }" @click="tab = 'clinic'">门诊容量与排班</div>
      <div class="tab" :class="{ active: tab === 'overview' }" @click="tab = 'overview'">漏种/异常清单</div>
    </div>
    <div class="tabs" v-else>
      <div class="tab active">随访工作态势</div>
    </div>

    <!-- 库存 -->
    <div v-if="isAdmin && tab === 'stock'">
      <div class="card">
        <div class="row" style="justify-content:space-between">
          <h3>疫苗批号库存（近效期先出）</h3>
          <button @click="openBatch">新批号入库（到货自动开放补种）</button>
        </div>
        <table>
          <thead><tr><th>批号</th><th>疫苗</th><th>库存</th><th>安全线</th><th>效期</th><th>到货</th><th>冷链</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="b in batches" :key="b.id">
              <td>{{ b.batchNo }}</td><td>{{ b.vaccineName }}</td>
              <td><b :class="b.quantity <= b.safetyStock ? 'style-danger' : ''">{{ b.quantity }}</b></td>
              <td>{{ b.safetyStock }}</td><td>{{ b.expiryDate }}</td><td>{{ b.arrivalDate }}</td>
              <td><span class="badge" :class="coldCls(b)">{{ coldText(b.coldChainStatus) }}</span>
                <div class="small muted">{{ b.coldChainNote }}</div></td>
              <td><span class="badge" :class="available(b) ? 'ok' : 'muted'">{{ available(b) ? '可发放' : '不可发放' }}</span></td>
              <td class="row">
                <button class="btn-sm btn-ghost" @click="adjust(b)">调整库存</button>
                <button class="btn-sm btn-danger" v-if="b.coldChainStatus === 'NORMAL'" @click="setCold(b, 'BROKEN')">冷链中断封存</button>
                <button class="btn-sm" v-else @click="setCold(b, 'NORMAL')">恢复正常</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 容量排班 -->
    <div v-if="isAdmin && tab === 'clinic'">
      <div class="grid grid-2">
        <div class="card">
          <h3>门诊容量（未来 14 天）</h3>
          <table>
            <thead><tr><th>日期</th><th>时段</th><th>容量</th><th>已约</th><th>余号</th><th>开放</th></tr></thead>
            <tbody>
              <tr v-for="c in capacities.slice(0, 40)" :key="c.id">
                <td>{{ c.clinicDate }}</td><td>{{ c.timeSlot }}</td><td>{{ c.maxCapacity }}</td>
                <td>{{ c.bookedCount }}</td><td>{{ c.maxCapacity - c.bookedCount }}</td>
                <td><span class="badge" :class="c.open ? 'ok' : 'muted'">{{ c.open ? '是' : '否' }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="card">
          <h3>医生排班</h3>
          <table>
            <thead><tr><th>日期</th><th>时段</th><th>医生</th><th>值班</th></tr></thead>
            <tbody>
              <tr v-for="s in schedules.slice(0, 40)" :key="s.id">
                <td>{{ s.workDate }}</td><td>{{ s.timeSlot }}</td><td>{{ s.doctorName }}</td>
                <td><span class="badge" :class="s.onDuty ? 'ok' : 'muted'">{{ s.onDuty ? '在岗' : '停诊' }}</span></td>
              </tr>
            </tbody>
          </table>
          <p class="muted small mt8">容量与排班由初始化数据按工作日生成；“14:00-14:30”时段刻意未排班，用于演示无排班不可约。</p>
        </div>
      </div>
    </div>

    <!-- 总览清单 -->
    <div v-if="tab === 'overview'">
      <div class="grid grid-2">
        <div class="card">
          <h3>漏种 / 等待库存剂次</h3>
          <table>
            <tbody>
              <tr v-for="p in problemPlans" :key="p.id">
                <td>{{ p.child?.name }}</td><td>{{ p.vaccineName }} 第{{ p.doseNo }}剂</td>
                <td><span class="badge" :class="p.status === 'OVERDUE' ? 'warn' : 'danger'">
                  {{ p.status === 'OVERDUE' ? '漏种' : p.status === 'WAIT_STOCK' ? '等待库存' : p.status }}
                </span></td>
                <td class="small muted">{{ p.remark }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="card">
          <h3>未结案 AEFI</h3>
          <table>
            <tbody>
              <tr v-for="a in openAefi" :key="a.id">
                <td>{{ a.child?.name }}</td><td class="small">{{ a.vaccineName }}（{{ a.batchNo }}）</td>
                <td class="small">{{ a.symptoms }}</td>
                <td><span class="badge warn">{{ aefiStatus(a.status) }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 入库弹窗 -->
    <div v-if="batchForm" class="modal-mask" @click.self="batchForm = null">
      <div class="modal">
        <h3>新批号入库</h3>
        <div class="grid grid-2">
          <label class="field"><span>疫苗</span><select v-model="batchForm.vaccineCode" @change="syncVaccineName">
            <option value="">选择疫苗</option>
            <option v-for="v in vaccines" :key="v.code" :value="v.code">{{ v.name }}</option></select></label>
          <label class="field"><span>批号</span><input v-model="batchForm.batchNo" /></label>
          <label class="field"><span>数量</span><input type="number" v-model.number="batchForm.quantity" /></label>
          <label class="field"><span>安全库存</span><input type="number" v-model.number="batchForm.safetyStock" /></label>
          <label class="field"><span>效期至</span><input type="date" v-model="batchForm.expiryDate" /></label>
          <label class="field"><span>到货日期</span><input type="date" v-model="batchForm.arrivalDate" /></label>
        </div>
        <div class="row" style="justify-content:flex-end">
          <button class="btn-ghost" @click="batchForm = null">取消</button>
          <button @click="submitBatch">入库并重算补种计划</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api, getUser, AEFI_STATUS } from '../api'

const isAdmin = getUser().role === 'ADMIN'
const tab = ref(isAdmin ? 'stock' : 'overview')
const toast = ref(''); const toastErr = ref(false)
const batches = ref([]); const vaccines = ref([]); const capacities = ref([]); const schedules = ref([])
const children = ref([])
const kpi = reactive({ overdue: 0, due: 0, lowStock: 0, brokenCold: 0, openAefi: 0, todayAppt: 0 })
const problemPlans = ref([]); const openAefi = ref([])
const batchForm = ref(null)

function showToast(m, e) { toast.value = m; toastErr.value = !!e; setTimeout(() => toast.value = '', 3500) }
function coldText(s) { return { NORMAL: '正常', BROKEN: '中断封存', DECOMMISSIONED: '报废' }[s] || s }
function coldCls(b) {
  if (b.coldChainStatus !== 'NORMAL') return 'danger'
  const days = Math.floor((new Date(b.expiryDate) - new Date()) / 86400000)
  return days < 90 ? 'warn' : 'ok'
}
function available(b) {
  return b.quantity > 0 && b.coldChainStatus === 'NORMAL' && new Date(b.expiryDate) >= new Date(new Date().toDateString())
}
function aefiStatus(s) { return AEFI_STATUS[s]?.text || s }

async function loadAll() {
  batches.value = await api.get('/api/catalog/batches')
  vaccines.value = await api.get('/api/catalog/vaccines')
  capacities.value = await api.get('/api/catalog/capacities')
  schedules.value = await api.get('/api/catalog/schedules')
  children.value = await api.get('/api/children')
  const today = new Date().toISOString().slice(0, 10)
  kpi.todayAppt = (await api.get('/api/appointments/daily?date=' + today)).length
  kpi.lowStock = batches.value.filter(b => b.quantity <= b.safetyStock).length
  kpi.brokenCold = batches.value.filter(b => b.coldChainStatus !== 'NORMAL').length
  const aefi = await api.get('/api/aefi?openOnly=true')
  kpi.openAefi = aefi.length
  openAefi.value = aefi

  let overdue = 0, due = 0
  problemPlans.value = []
  for (const c of children.value) {
    const plans = await api.get('/api/plans/child/' + c.id)
    plans.forEach(p => {
      if (p.status === 'OVERDUE') { overdue++; problemPlans.value.push(p) }
      if (p.status === 'DUE') due++
      if (p.status === 'WAIT_STOCK') problemPlans.value.push(p)
    })
  }
  kpi.overdue = overdue; kpi.due = due
}
function openBatch() {
  batchForm.value = reactive({ vaccineCode: '', vaccineName: '', batchNo: '', quantity: 10, safetyStock: 6,
    expiryDate: '', arrivalDate: new Date().toISOString().slice(0, 10), coldChainStatus: 'NORMAL' })
}
function syncVaccineName(e) {
  const v = vaccines.value.find(x => x.code === batchForm.value.vaccineCode)
  if (v) batchForm.value.vaccineName = v.name
}
async function submitBatch() {
  const f = batchForm.value
  if (!f.vaccineCode || !f.batchNo || !f.expiryDate) return showToast('请完善疫苗/批号/效期', true)
  await api.post('/api/catalog/batches', { ...f })
  batchForm.value = null
  showToast('批号已入库，等待该疫苗的补种计划已自动重新开放预约')
  await loadAll()
}
async function adjust(b) {
  const v = prompt('调整 ' + b.batchNo + ' 的库存数量为', b.quantity)
  if (v === null) return
  await api.put('/api/catalog/batches/' + b.id, { quantity: Number(v) })
  showToast('库存已调整，相关计划已重算')
  await loadAll()
}
async function setCold(b, status) {
  const note = prompt('冷链情况说明', status === 'BROKEN' ? '冷链温度异常，封存暂停使用' : '经检测复核恢复正常')
  if (note === null) return
  await api.put('/api/catalog/batches/' + b.id, { coldChainStatus: status, coldChainNote: note })
  showToast('冷链状态已更新，相关计划与可约时段已重算')
  await loadAll()
}

onMounted(loadAll)
</script>
<style scoped>.style-danger { color: var(--danger); }</style>
