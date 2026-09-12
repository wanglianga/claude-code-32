<template>
  <div class="page">
    <div v-if="toast" class="toast" :class="{ err: toastErr }">{{ toast }}</div>

    <div class="card row" style="justify-content: space-between">
      <div>
        <h2 style="margin:0">家长服务台</h2>
        <span class="muted small">为孩子建立档案、查看补种计划、预约接种、跟踪异常反应与咨询</span>
      </div>
      <div class="row">
        <select v-model="childId" style="width:220px" @change="onChildChange">
          <option v-for="c in children" :key="c.id" :value="c.id">
            {{ c.name }}（{{ c.gender === 'M' ? '男' : '女' }}，{{ c.birthDate }}）
          </option>
        </select>
        <button class="btn-ghost" @click="showCreate = true">＋ 建立儿童档案</button>
      </div>
    </div>

    <div class="tabs">
      <div v-for="t in tabs" :key="t.k" class="tab" :class="{ active: tab === t.k }" @click="switchTab(t.k)">{{ t.label }}</div>
    </div>

    <!-- ============ 接种计划 ============ -->
    <div v-if="tab === 'plan'">
      <div class="card">
        <div class="row" style="justify-content: space-between">
          <h3>接种 / 补种计划</h3>
          <button class="btn-ghost btn-sm" @click="loadPlans">重新计算</button>
        </div>
        <table>
          <thead><tr><th>疫苗</th><th>剂次</th><th>状态</th><th>建议接种</th><th>最早可种</th><th>年龄上限</th><th>说明</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="p in plans" :key="p.id">
              <td>{{ p.vaccineName }}<div class="muted small">{{ p.vaccineCode }}</div></td>
              <td>第{{ p.doseNo }}剂</td>
              <td><span class="badge" :class="st(p.status).cls">{{ st(p.status).text }}</span></td>
              <td>{{ fmtDate(p.dueDate) }}</td>
              <td>{{ fmtDate(p.earliestDate) }}</td>
              <td>{{ fmtDate(p.ageLimitDate) }}</td>
              <td class="small muted" style="max-width:300px">{{ p.remark }}
                <div v-if="p.adjustReason" class="mt8" style="color:var(--primary)">📌 {{ p.adjustReason }}</div></td>
              <td>
                <button v-if="p.status === 'DUE' || p.status === 'OVERDUE'" class="btn-sm" @click="openBooking(p)">预约</button>
                <span v-else-if="p.status === 'DONE'" class="small muted">{{ fmtDate(p.completedDate) }} 已完成</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ============ 我的预约 ============ -->
    <div v-if="tab === 'appt'">
      <div class="card">
        <h3>我的预约</h3>
        <table>
          <thead><tr><th>儿童</th><th>疫苗/剂次</th><th>日期时段</th><th>医生</th><th>预留批号</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="a in appointments" :key="a.id">
              <td>{{ a.child?.name }}</td>
              <td>{{ a.vaccineName }} 第{{ a.doseNo }}剂</td>
              <td>{{ a.appointmentDate }} {{ a.timeSlot }}</td>
              <td>{{ a.doctorName || '-' }}</td>
              <td class="small">{{ a.reservedBatchNo || '-' }}</td>
              <td><span class="badge" :class="ast(a.status).cls">{{ ast(a.status).text }}</span>
                <div v-if="a.cancelReason" class="small muted">{{ a.cancelReason }}</div></td>
              <td>
                <button v-if="a.status === 'BOOKED' || a.status === 'CHECKED_IN'" class="btn-sm btn-danger" @click="cancel(a)">取消预约</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ============ 儿童档案/健康申报 ============ -->
    <div v-if="tab === 'profile'">
      <div class="grid grid-2" v-if="child">
        <div class="card">
          <h3>基础与健康状态</h3>
          <p><b>{{ child.name }}</b>（{{ child.gender === 'M' ? '男' : '女' }}） 出生日期 {{ child.birthDate }}</p>
          <p class="muted small" v-if="child.moveInDate">迁入日期 {{ child.moveInDate }}：{{ child.migrationNote }}</p>
          <label class="field"><span>近期健康状态申报（发热/皮疹等，门诊预约与到诊核验会读取）</span>
            <textarea v-model="healthText" rows="3"></textarea></label>
          <button @click="saveHealth">保存健康状态</button>
        </div>
        <div class="card">
          <h3>过敏史</h3>
          <table><tbody>
            <tr v-for="x in record?.allergies || []" :key="x.id"><td><b>{{ x.allergen }}</b><div class="small muted">{{ x.reaction }} {{ x.note }}</div></td></tr>
          </tbody></table>
          <div class="row mt8">
            <input v-model="newAllergy.allergen" placeholder="过敏原，如 酵母/鸡蛋/明胶" />
            <input v-model="newAllergy.reaction" placeholder="反应表现" />
            <button @click="addAllergy">添加</button>
          </div>
          <h3 class="mt16">禁忌症</h3>
          <table><tbody>
            <tr v-for="x in record?.contraindications || []" :key="x.id">
              <td><b>{{ x.contraType }}</b><span class="muted small">（{{ x.vaccineCode }}）</span><div class="small muted">{{ x.description }}</div></td>
            </tr>
          </tbody></table>
          <p class="muted small">禁忌症由门诊医护评估登记。</p>
          <h3 class="mt16">迁入 / 既往接种登记</h3>
          <div class="row">
            <select v-model="newPrior.vaccineCode" style="max-width:180px">
              <option value="">选择疫苗</option>
              <option v-for="v in vaccines" :key="v.code" :value="v.code">{{ v.name }}</option>
            </select>
            <input type="number" min="1" v-model.number="newPrior.doseNo" placeholder="剂次" style="max-width:80px" />
            <input type="date" v-model="newPrior.vaccinationDate" />
            <input v-model="newPrior.clinicName" placeholder="原接种单位" />
            <button @click="addPrior">登记待核验</button>
          </div>
          <p class="muted small">迁入记录需护士/医生核验接种证后才计入程序。</p>

          <h3 class="mt16">上传外地接种本（自动识别疫苗/剂次/日期/批号）</h3>
          <div class="row">
            <input type="file" ref="fileInput" accept="image/*,.txt" style="max-width:300px" />
            <button @click="uploadDoc">上传并识别</button>
            <button class="btn-ghost btn-sm" @click="downloadTemplate">下载文本接种本模板</button>
          </div>
          <p class="muted small">支持拍照图片（演示环境模拟 OCR，含一条故意模糊记录演示人工队列）或 .txt 文本本（每行：疫苗代码,剂次,日期,批号,单位,置信度）。</p>
          <table class="mt8" v-if="migrationRecords.length">
            <thead><tr><th>疫苗</th><th>剂次</th><th>接种日期</th><th>批号</th><th>原单位</th><th>置信度</th><th>核验状态</th><th>医生备注</th></tr></thead>
            <tbody>
              <tr v-for="p in migrationRecords" :key="p.id">
                <td>{{ p.vaccineName }}<div class="muted small">{{ p.vaccineCode || '未识别' }}</div></td>
                <td>第{{ p.doseNo }}剂</td><td>{{ p.vaccinationDate }}</td>
                <td class="small">{{ p.batchNo || '模糊' }}</td><td class="small">{{ p.clinicName }}</td>
                <td>{{ p.confidence != null ? Math.round(p.confidence * 100) + '%' : '-' }}</td>
                <td><span class="badge" :class="priorSt(p.verifyStatus).cls">{{ priorSt(p.verifyStatus).text }}</span></td>
                <td class="small muted">{{ p.reviewNote }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- ============ 健康档案时间线 ============ -->
    <div v-if="tab === 'record'">
      <div class="card">
        <h3>儿童维度健康档案（库存→预约→接种→随访 全链路可解释）</h3>
        <div class="timeline">
          <div class="tl-item" v-for="(x, i) in record?.timeline || []" :key="i">
            <div><span class="badge muted">{{ x.typeName }}</span> <b>{{ x.title }}</b>
              <span class="date"> · {{ fmtDate(x.date) }}</span></div>
            <div class="small muted">{{ x.detail }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 家长咨询 ============ -->
    <div v-if="tab === 'consult'">
      <div class="grid grid-2">
        <div class="card">
          <h3>发起咨询</h3>
          <label class="field"><span>主题</span><input v-model="ask.topic" placeholder="如：接种后发热皮疹怎么办" /></label>
          <label class="field"><span>问题内容</span><textarea v-model="ask.question" rows="4"></textarea></label>
          <button @click="askConsult">提交给预防接种医生/随访人员</button>
        </div>
        <div class="card">
          <h3>咨询记录</h3>
          <div v-for="c in consultations" :key="c.id" style="border-bottom:1px solid var(--border); padding:8px 0">
            <b>{{ c.topic }}</b>
            <span class="badge" :class="c.status === 'OPEN' ? 'warn' : 'done'">{{ c.status === 'OPEN' ? '待回复' : '已回复' }}</span>
            <span v-if="c.preVaccineAlert" class="badge warn">接种前提醒</span>
            <div class="small mt8">{{ c.question }}</div>
            <div v-if="c.reply" class="small mt8" style="background:#f4f9f9; padding:8px; border-radius:6px">
              {{ c.repliedByName }}（{{ c.repliedByRole }}）：{{ c.reply }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 预约弹窗 ============ -->
    <div v-if="booking" class="modal-mask" @click.self="booking = null">
      <div class="modal">
        <h3>选择可约时段 — {{ booking.plan.vaccineName }} 第{{ booking.plan.doseNo }}剂</h3>
        <div class="row mb8">
          <span class="muted small">实际接种疫苗：</span>
          <select v-model="bookingVaccine" @change="loadSlots" style="max-width:300px">
            <option :value="booking.plan.vaccineCode">{{ booking.plan.vaccineName }}（本苗）</option>
            <option v-for="code in altVaccines" :key="code" :value="code">{{ vaccineName(code) }}（同组替代苗）</option>
          </select>
          <button class="btn-ghost btn-sm" @click="loadSlots">刷新时段</button>
        </div>
        <table>
          <thead><tr><th>日期</th><th>时段</th><th>余号</th><th>值班医生</th><th>可发批号/效期/冷链</th><th>判断</th></tr></thead>
          <tbody>
            <tr v-for="(s, i) in slots" :key="i">
              <td>{{ s.date }}</td><td>{{ s.timeSlot }}</td><td>{{ s.remaining }}/{{ s.capacity }}</td>
              <td>{{ s.doctorName || '无排班' }}</td>
              <td class="small">
                <template v-if="s.batchNo">{{ s.batchNo }}<br/>效期 {{ fmtDate(s.batchExpiry) }} ·
                  <span :class="s.coldChainStatus === 'NORMAL' ? 'badge ok' : 'badge danger'">冷链{{ s.coldChainStatus === 'NORMAL' ? '正常' : s.coldChainStatus }}</span>
                  <span v-if="s.substituted" class="badge warn">换苗</span>
                </template>
                <span v-else class="muted">无可用批号</span>
              </td>
              <td>
                <button v-if="s.bookable" class="btn-sm" @click="doBook(s)">预约</button>
                <div v-else class="small muted" style="max-width:200px">
                  <div v-for="(r, j) in s.reasons" :key="j">· {{ r }}</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="mt16" style="text-align:right"><button class="btn-ghost" @click="booking = null">关闭</button></div>
      </div>
    </div>

    <!-- ============ 新建儿童弹窗 ============ -->
    <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
      <div class="modal">
        <h3>建立儿童档案</h3>
        <div class="grid grid-2">
          <label class="field"><span>姓名</span><input v-model="createForm.name" /></label>
          <label class="field"><span>性别</span><select v-model="createForm.gender"><option value="M">男</option><option value="F">女</option></select></label>
          <label class="field"><span>出生日期</span><input type="date" v-model="createForm.birthDate" /></label>
          <label class="field"><span>迁入日期（本地出生可不填）</span><input type="date" v-model="createForm.moveInDate" /></label>
        </div>
        <label class="field"><span>迁入说明 / 接种证情况</span><input v-model="createForm.migrationNote" /></label>
        <label class="field"><span>近期健康状态</span><input v-model="createForm.healthStatus" placeholder="如：无 / 三天前发热已退" /></label>
        <div class="row" style="justify-content:flex-end"><button class="btn-ghost" @click="showCreate = false">取消</button>
          <button @click="createChild">建立并生成计划</button></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api, PLAN_STATUS, APPT_STATUS, PRIOR_STATUS, fmtDate } from '../api'

const tabs = [
  { k: 'plan', label: '接种/补种计划' },
  { k: 'appt', label: '我的预约' },
  { k: 'profile', label: '档案与健康申报' },
  { k: 'record', label: '儿童健康档案' },
  { k: 'consult', label: '家长咨询' }
]
const tab = ref('plan')
const children = ref([])
const childId = ref(null)
const child = ref(null)
const plans = ref([])
const appointments = ref([])
const record = ref(null)
const consultations = ref([])
const vaccines = ref([])
const toast = ref('')
const toastErr = ref(false)
const showCreate = ref(false)
const createForm = reactive({ name: '', gender: 'M', birthDate: '', moveInDate: '', migrationNote: '', healthStatus: '' })
const healthText = ref('')
const newAllergy = reactive({ allergen: '', reaction: '' })
const newPrior = reactive({ vaccineCode: '', doseNo: 1, vaccinationDate: '', clinicName: '' })
const migrationRecords = ref([])
const fileInput = ref(null)
const ask = reactive({ topic: '', question: '' })
const booking = ref(null)
const slots = ref([])
const bookingVaccine = ref('')

function st(s) { return PLAN_STATUS[s] || { text: s, cls: 'muted' } }
function ast(s) { return APPT_STATUS[s] || { text: s, cls: 'muted' } }
function priorSt(s) { return PRIOR_STATUS[s] || { text: s, cls: 'muted' } }
function showToast(msg, err) { toast.value = msg; toastErr.value = !!err; setTimeout(() => toast.value = '', 3500) }
function vaccineName(code) { return vaccines.value.find(v => v.code === code)?.name || code }
const altVaccines = ref([])

async function loadChildren() {
  children.value = await api.get('/api/children')
  if (!childId.value && children.value.length) childId.value = children.value[0].id
  if (childId.value) onChildChange()
}
async function onChildChange() {
  child.value = children.value.find(c => c.id === childId.value)
  healthText.value = child.value?.healthStatus || ''
  await Promise.all([loadPlans(), loadRecord(), loadConsultations(), loadMigration()])
}
async function loadPlans() { plans.value = await api.get('/api/plans/child/' + childId.value) }
async function loadMigration() { migrationRecords.value = await api.get('/api/children/' + childId.value + '/migration-records') }
async function loadAppts() { appointments.value = await api.get('/api/appointments/mine') }
async function loadRecord() { record.value = await api.get('/api/children/' + childId.value + '/health-record') }
async function loadConsultations() { consultations.value = await api.get('/api/children/' + childId.value + '/consultations') }
function switchTab(k) {
  tab.value = k
  if (k === 'appt') loadAppts()
  if (k === 'record') loadRecord()
  if (k === 'consult') loadConsultations()
}
async function createChild() {
  if (!createForm.name || !createForm.birthDate) return showToast('请填写姓名与出生日期', true)
  await api.post('/api/children', { ...createForm, moveInDate: createForm.moveInDate || null })
  showCreate.value = false
  Object.assign(createForm, { name: '', gender: 'M', birthDate: '', moveInDate: '', migrationNote: '', healthStatus: '' })
  await loadChildren()
  showToast('档案已建立，系统已生成接种计划')
}
async function saveHealth() {
  await api.put('/api/children/' + childId.value + '/health', { healthStatus: healthText.value })
  child.value.healthStatus = healthText.value
  await loadPlans()
  showToast('健康状态已保存，异常症状已推送门诊')
}
async function addAllergy() {
  if (!newAllergy.allergen) return
  await api.post('/api/children/' + childId.value + '/allergies', { ...newAllergy })
  newAllergy.allergen = ''; newAllergy.reaction = ''
  await Promise.all([loadRecord(), loadPlans()])
  showToast('过敏史已登记，相关疫苗剂次已转医生复核')
}
async function addPrior() {
  if (!newPrior.vaccineCode || !newPrior.vaccinationDate) return showToast('请选择疫苗与接种日期', true)
  await api.post('/api/children/' + childId.value + '/priors', { ...newPrior })
  newPrior.vaccineCode = ''; newPrior.clinicName = ''
  await Promise.all([loadRecord(), loadPlans(), loadMigration()])
  showToast('迁入记录已登记，等待门诊核验')
}
async function uploadDoc() {
  const f = fileInput.value?.files?.[0]
  if (!f) return showToast('请先选择接种本照片或文本文件', true)
  const fd = new FormData(); fd.append('file', f)
  try {
    const res = await api.upload('/api/children/' + childId.value + '/migration-docs', fd)
    const ambiguous = res.records.filter(r => r.verifyStatus === 'AMBIGUOUS').length
    showToast(`识别出 ${res.records.length} 条记录` + (ambiguous ? `，其中 ${ambiguous} 条模糊已进入人工队列` : '，等待医生核验'))
    fileInput.value.value = ''
    await Promise.all([loadMigration(), loadPlans(), loadRecord()])
  } catch (e) { showToast(e.message, true) }
}
function downloadTemplate() {
  const content = '# 每行：疫苗代码,剂次,接种日期,批号,原接种单位,置信度(0-1)\nHEPB,1,2025-10-20,HEPB-OLD-01,外地县医院,0.98\nBCG,1,2025-10-21,BCG-OLD-01,外地县医院,0.61\n'
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob); a.download = '接种本模板.txt'; a.click()
}
async function askConsult() {
  if (!ask.topic || !ask.question) return
  await api.post('/api/consultations', { childId: childId.value, ...ask })
  ask.topic = ''; ask.question = ''
  await loadConsultations()
  showToast('咨询已提交，医生/随访人员将回复')
}
async function openBooking(p) {
  booking.value = { plan: p }
  bookingVaccine.value = p.vaccineCode
  const self = vaccines.value.find(v => v.code === p.vaccineCode)
  altVaccines.value = self ? vaccines.value.filter(v => v.vaccineGroup === self.vaccineGroup && v.code !== self.code).map(v => v.code) : []
  await loadSlots()
}
async function loadSlots() {
  slots.value = await api.get(`/api/appointments/slots?planId=${booking.value.plan.id}&vaccineCode=${bookingVaccine.value}&days=10`)
}
async function doBook(s) {
  try {
    await api.post('/api/appointments', {
      planId: booking.value.plan.id, date: s.date, timeSlot: s.timeSlot, vaccineCode: s.vaccineCode
    })
    booking.value = null
    await Promise.all([loadPlans(), loadAppts()])
    showToast('预约成功，可在“我的预约”查看')
  } catch (e) { showToast(e.message, true) }
}
async function cancel(a) {
  const reason = prompt('请输入取消原因（门诊与随访人员会收到推送）')
  if (reason === null) return
  await api.post('/api/appointments/' + a.id + '/cancel', { reason })
  await loadAppts(); await loadPlans()
  showToast('预约已取消')
}

onMounted(async () => {
  vaccines.value = await api.get('/api/catalog/vaccines')
  await loadChildren()
  await loadAppts()
})
</script>
