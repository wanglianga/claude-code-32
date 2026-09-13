<template>
  <div class="page">
    <div v-if="toast" class="toast" :class="{ err: toastErr }">{{ toast }}</div>

    <div class="card row" style="justify-content: space-between">
      <div><h2 style="margin:0">预防接种门诊工作台</h2>
        <span class="muted small">容量 · 批号 · 冷链 · 排班 · 健康状态综合核验</span></div>
      <div class="row">
        <input type="date" v-model="dailyDate" @change="loadDaily" />
        <button class="btn-ghost btn-sm" @click="loadDaily">刷新当日</button>
      </div>
    </div>

    <div class="kpi mb8">
      <div class="item"><div class="num">{{ daily.length }}</div><div class="lbl">当日预约</div></div>
      <div class="item"><div class="num">{{ daily.filter(a=>a.status==='CHECKED_IN').length }}</div><div class="lbl">待核验到诊</div></div>
      <div class="item"><div class="num">{{ daily.filter(a=>a.status==='VACCINATED').length }}</div><div class="lbl">已接种</div></div>
      <div class="item"><div class="num">{{ reviewPlans.length }}</div><div class="lbl">待医生复核</div></div>
      <div class="item"><div class="num">{{ openConsults.length }}</div><div class="lbl">咨询待回复</div></div>
    </div>

    <div class="tabs">
      <div class="tab" :class="{ active: tab === 'daily' }" @click="switchTab('daily')">今日门诊</div>
      <div class="tab" :class="{ active: tab === 'verify' }" @click="switchTab('verify')">到诊核验与接种</div>
      <div class="tab" :class="{ active: tab === 'review' }" @click="switchTab('review')" v-if="isDoctor">医生复核</div>
      <div class="tab" :class="{ active: tab === 'migration' }" @click="switchTab('migration')">接种本核验队列<span v-if="queue.length" class="badge danger" style="margin-left:6px">{{ queue.length }}</span></div>
      <div class="tab" :class="{ active: tab === 'consult' }" @click="switchTab('consult')">家长咨询</div>
      <div class="tab" :class="{ active: tab === 'lookup' }" @click="switchTab('lookup')">儿童档案查询</div>
    </div>

    <!-- 今日门诊 -->
    <div v-if="tab === 'daily'" class="card">
      <table>
        <thead><tr><th>时段</th><th>儿童</th><th>疫苗/剂次</th><th>批号</th><th>医生</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="a in daily" :key="a.id">
            <td>{{ a.appointmentDate }} {{ a.timeSlot }}</td>
            <td>{{ a.child?.name }}</td>
            <td>{{ a.vaccineName }} 第{{ a.doseNo }}剂</td>
            <td class="small">{{ a.reservedBatchNo }}</td>
            <td>{{ a.doctorName }}</td>
            <td><span class="badge" :class="ast(a.status).cls">{{ ast(a.status).text }}</span></td>
            <td class="row">
              <button v-if="a.status === 'BOOKED'" class="btn-sm" @click="checkIn(a)">到诊签到</button>
              <button v-if="a.status === 'CHECKED_IN'" class="btn-sm" @click="goVerify(a)">去核验接种</button>
              <button v-if="a.status === 'VACCINATED'" class="btn-sm btn-ghost" @click="goRecord(a)">接种/留观记录</button>
              <button v-if="a.status !== 'VACCINATED'" class="btn-sm btn-danger" @click="clinicCancel(a)">门诊取消</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 到诊核验 -->
    <div v-if="tab === 'verify'">
      <div class="card row">
        <span>选择已到诊预约：</span>
        <select v-model="verifyApptId" @change="loadChecklist" style="max-width:520px">
          <option value="">— 请选择 —</option>
          <option v-for="a in daily.filter(x => x.status === 'CHECKED_IN' || x.status === 'VACCINATED')" :key="a.id" :value="a.id">
            {{ a.child?.name }}｜{{ a.vaccineName }} 第{{ a.doseNo }}剂｜{{ a.timeSlot }}
          </option>
        </select>
      </div>

      <div v-if="checklist && verifyApptId">
        <div class="grid grid-2">
          <div class="card">
            <h3>儿童与健康信息</h3>
            <p><b>{{ checklist.appointment.child?.name }}</b>
              （{{ checklist.appointment.child?.gender === 'M' ? '男' : '女' }}，
              生于 {{ checklist.appointment.child?.birthDate }}）</p>
            <p class="small">近期健康状态：<b :class="healthRisk ? 'style-danger' : ''">{{ checklist.appointment.child?.healthStatus || '无申报' }}</b>
              <span class="muted">（{{ checklist.appointment.child?.healthUpdatedAt || '未更新' }}）</span></p>
            <div v-if="checklist.preVaccineAlerts?.length" style="background:var(--warn-bg); border-radius:8px; padding:10px">
              <b>⚠ 下次接种前家长咨询提醒：</b>
              <div v-for="c in checklist.preVaccineAlerts" :key="c.id" class="small mt8">
                【{{ c.topic }}】{{ c.alertNote || c.reply }}
              </div>
            </div>
            <div v-if="checklist.appointment.plan && checklist.appointment.plan.vaccineCode !== checklist.appointment.vaccineCode"
                 class="badge warn mt8">同组换苗接种：计划 {{ checklist.appointment.plan.vaccineName }} → 实际 {{ checklist.appointment.vaccineName }}</div>
            <div v-if="checklist.appointment.plan?.adjustReason" class="mt8"
                 style="background:var(--primary-light);border-radius:8px;padding:10px">
              <b>📌 本剂安排说明（护士接诊须知）</b>
              <div class="small mt8">{{ checklist.appointment.plan.adjustReason }}</div>
            </div>
          </div>

          <div class="card" v-if="!record">
            <h3>护士五项核验</h3>
            <label class="row"><input type="checkbox" v-model="form.identityVerified" style="width:auto"/> ① 儿童身份核验（姓名/出生日期/证件）</label>
            <label class="row mt8"><input type="checkbox" v-model="form.batchVerified" style="width:auto"/> ② 疫苗批号与外观核验</label>
            <label class="row mt8"><input type="checkbox" v-model="form.consentSigned" style="width:auto"/> ③ 知情同意书已签署</label>
            <label class="row mt8"><input type="checkbox" v-model="form.recentFeverChecked" style="width:auto"/> ④ 近期发热筛查已完成</label>
            <label class="row mt8"><input type="checkbox" v-model="form.contraindicationChecked" style="width:auto"/> ⑤ 禁忌症筛查已完成</label>
            <div class="row mt8">
              <span class="small">近期发热：</span>
              <label class="row small"><input type="radio" value="false" v-model="form.recentFever" style="width:auto"/>无</label>
              <label class="row small"><input type="radio" value="true" v-model="form.recentFever" style="width:auto"/>有（应暂缓）</label>
            </div>
            <label class="field mt8"><span>实际发放批号（扫码/选择，需冷链正常、在效期）</span>
              <select v-model="form.actualBatchNo">
                <option value="">— 选择批号 —</option>
                <option v-for="b in usableBatches" :key="b.batchNo" :value="b.batchNo">
                  {{ b.batchNo }}（剩{{ b.quantity }}支，效期{{ b.expiryDate }}，冷链{{ b.coldChainStatus }}）
                </option>
              </select></label>
            <label class="field"><span>核验备注</span><input v-model="form.verifyNote" /></label>
            <div class="row">
              <button @click="doVaccinate">核验通过并接种（扣库存，进入 30 分钟留观）</button>
              <button class="btn-danger" @click="withhold = true">暂缓不接种</button>
            </div>
            <div v-if="withhold" class="mt8">
              <label class="field"><span>暂缓原因（发热/禁忌等，将推送医生与随访人员）</span>
                <textarea v-model="form.withholdReason" rows="2"></textarea></label>
              <button class="btn-danger" @click="doWithhold">确认暂缓</button>
              <button class="btn-ghost" @click="withhold = false">返回</button>
            </div>
          </div>

          <div class="card" v-if="record && record.status !== 'OBSERVING'">
            <h3>接种记录状态：<span class="badge" :class="recBadge(record.status).cls">{{ recBadge(record.status).text }}</span></h3>
            <p class="small">批号 {{ record.batchNo }}；接种日期 {{ record.vaccinationDate }}；护士 {{ record.nurseName }}</p>
            <p class="small" v-if="record.verifyNote">备注：{{ record.verifyNote }}</p>
            <button class="btn-sm" v-if="isDoctor && record.status !== 'WITHHELD'" @click="showReview = true">医生复核意见</button>
          </div>
        </div>

        <!-- 留观 -->
        <div class="card" v-if="record && record.status === 'OBSERVING'">
          <h3>留观记录（{{ record.observationMinutes }} 分钟）</h3>
          <p class="small">留观开始：{{ record.observationStartTime }}　现场反应请在结束时记录。</p>
          <div class="grid grid-3">
            <label class="field"><span>现场反应</span>
              <select v-model="obs.onSiteReaction">
                <option>无异常</option><option>局部红肿</option><option>低热</option><option>皮疹</option><option>哭闹/精神差</option>
              </select></label>
          </div>
          <label class="row"><input type="checkbox" v-model="obs.guardianConfirmed" style="width:auto"/> 家长已现场确认留观与反应情况</label>
          <label class="row mt8"><input type="checkbox" v-model="obs.abnormal" style="width:auto"/> 发现异常反应（发热/皮疹等），自动建立 AEFI 并推送随访</label>
          <label class="field mt8" v-if="obs.abnormal"><span>异常症状描述</span><input v-model="obs.abnormalSymptoms" placeholder="如：体温 38.6℃，躯干散在皮疹" /></label>
          <button @click="doObserve">结束留观并归档</button>
        </div>

        <!-- 异常待评估 -->
        <div class="card" v-if="record && record.status === 'ABNORMAL'">
          <h3>⚠ 现场异常已转 AEFI 个案</h3>
          <p class="small">反应：{{ record.onSiteReaction }}。若经医生评估排除异常反应，可转正常完成。</p>
          <button v-if="isDoctor" @click="resolveAbnormal">医生评估无异常，转正常完成</button>
        </div>
      </div>

      <!-- 医生复核弹窗 -->
      <div v-if="showReview" class="modal-mask" @click.self="showReview = false">
        <div class="modal">
          <h3>医生复核意见</h3>
          <label class="field"><span>是否同意接种</span>
            <select v-model="reviewForm.approved"><option :value="true">同意</option><option :value="false">建议暂缓</option></select></label>
          <label class="field"><span>意见</span><textarea v-model="reviewForm.note" rows="3"></textarea></label>
          <div class="row" style="justify-content:flex-end"><button class="btn-ghost" @click="showReview = false">取消</button>
            <button @click="submitReview">提交</button></div>
        </div>
      </div>
    </div>

    <!-- 医生复核计划 -->
    <div v-if="tab === 'review'">
      <div class="card">
        <h3>待复核计划（过敏史 / 禁忌症）</h3>
        <table>
          <thead><tr><th>儿童</th><th>疫苗剂次</th><th>状态</th><th>系统说明</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="p in reviewPlans" :key="p.id">
              <td>{{ p.child?.name }}</td><td>{{ p.vaccineName }} 第{{ p.doseNo }}剂</td>
              <td><span class="badge warn">{{ p.status === 'REVIEW' ? '待医生复核' : '禁忌暂缓' }}</span></td>
              <td class="small muted">{{ p.remark }}</td>
              <td><button class="btn-sm" @click="approvePlan(p)">复核同意，开放预约</button></td>
            </tr>
          </tbody>
        </table>
        <p v-if="!reviewPlans.length" class="muted small">暂无待复核计划。</p>
      </div>
    </div>

    <!-- 迁入接种本人工核验队列 -->
    <div v-if="tab === 'migration'">
      <div class="card row" style="justify-content:space-between">
        <h3 style="margin:0">外地接种本人工核验队列（防重复接种 / 防漏种）</h3>
        <button class="btn-ghost btn-sm" @click="loadQueue">刷新队列</button>
      </div>
      <div v-if="!queue.length" class="card muted small">队列为空，没有待核验或模糊的迁入记录。</div>
      <div class="card" v-for="row in queue" :key="row.prior.id">
        <div class="row" style="justify-content:space-between; align-items:flex-start">
          <div>
            <b>{{ row.childName }}</b>
            <span class="muted small">（生于 {{ row.birthDate }}）</span>
            <span class="badge" :class="priorSt(row.prior.verifyStatus).cls" style="margin-left:8px">
              {{ priorSt(row.prior.verifyStatus).text }}
            </span>
            <span v-if="row.prior.migrationDocId" class="small" style="margin-left:8px">
              <a href="#" @click.prevent="viewDoc(row.prior.migrationDocId)">🔍 查看接种本原件</a>
            </span>
          </div>
          <div class="muted small">置信度 {{ row.prior.confidence != null ? Math.round(row.prior.confidence * 100) + '%' : '-' }}</div>
        </div>
        <div class="grid grid-3 mt8">
          <label class="field"><span>疫苗（模糊时请按原件纠正）</span>
            <select v-model="editMap[row.prior.id].vaccineCode">
              <option value="">未识别—请选择</option>
              <option v-for="v in vaccines" :key="v.code" :value="v.code">{{ v.name }}</option>
            </select></label>
          <label class="field"><span>剂次</span><input type="number" v-model.number="editMap[row.prior.id].doseNo" /></label>
          <label class="field"><span>接种日期</span><input type="date" v-model="editMap[row.prior.id].vaccinationDate" /></label>
          <label class="field"><span>批号</span><input v-model="editMap[row.prior.id].batchNo" placeholder="原件不清可留空" /></label>
          <label class="field"><span>原接种单位</span><input v-model="editMap[row.prior.id].clinicName" /></label>
          <label class="field"><span>核验备注</span><input v-model="editMap[row.prior.id].note" placeholder="与原件核对一致 / 印章不清…" /></label>
        </div>
        <div class="small muted">
          识别原文：{{ row.prior.vaccineName }}
          第{{ row.prior.doseNo || '?' }}剂<span v-if="!row.prior.doseNo">（原件：{{ row.prior.rawDoseText || '不清' }}）</span>
          {{ row.prior.vaccinationDate || '日期模糊' }}<span v-if="!row.prior.vaccinationDate">（原件：{{ row.prior.rawDateText || '不清' }}）</span>
          批号 {{ row.prior.batchNo || '模糊' }}
        </div>
        <div v-if="row.prior.verifyStatus === 'REJECTED'" class="small" style="color:var(--danger)">驳回原因：{{ row.prior.reviewNote }}</div>
        <div class="row mt8">
          <button @click="confirmPrior(row.prior)">✓ 确认采信（跳过该剂并重算补种计划）</button>
          <button class="btn-danger" @click="rejectPrior(row.prior)">不予采信（该剂转为追加补种）</button>
        </div>
      </div>
    </div>

    <!-- 咨询 -->
    <div v-if="tab === 'consult'">
      <div class="card">
        <h3>家长咨询处理（可标记为“下次接种前提醒”）</h3>
        <div v-for="c in consultations" :key="c.id" style="border-bottom:1px solid var(--border); padding:10px 0">
          <div class="row" style="justify-content:space-between">
            <b>{{ c.child?.name }}｜{{ c.topic }}</b>
            <span class="badge" :class="c.status === 'OPEN' ? 'warn' : 'done'">{{ c.status === 'OPEN' ? '待回复' : '已回复' }}</span>
          </div>
          <p class="small">{{ c.question }}</p>
          <textarea v-if="c.status === 'OPEN'" :value="replyMap[c.id]?.reply || ''" placeholder="回复内容…" rows="2"
            @input="onReplyInput(c.id, $event)"></textarea>
          <div v-if="c.status === 'OPEN'" class="row mt8">
            <label class="row small"><input type="checkbox" v-model="alertMap[c.id]" style="width:auto"/> 列入该儿童下次接种前提醒</label>
            <button class="btn-sm" @click="reply(c)">回复</button>
          </div>
          <div v-else class="small" style="background:#f4f9f9; padding:8px; border-radius:6px">
            {{ c.repliedByName }}：{{ c.reply }}
            <span v-if="c.preVaccineAlert" class="badge warn">接种前提醒</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 档案查询 -->
    <div v-if="tab === 'lookup'">
      <div class="card row">
        <select v-model="lookupId" @change="loadLookup" style="max-width:300px">
          <option value="">选择儿童</option>
          <option v-for="c in children" :key="c.id" :value="c.id">{{ c.name }}（{{ c.birthDate }}）</option>
        </select>
      </div>
      <div class="card" v-if="lookup">
        <h3>健康档案时间线 — {{ lookup.child.name }}</h3>
        <p class="small">过敏史：{{ (lookup.allergies || []).map(a => a.allergen).join('、') || '无' }}；
          禁忌：{{ (lookup.contraindications || []).map(x => x.contraType).join('、') || '无' }}</p>
        <div class="timeline">
          <div class="tl-item" v-for="(x, i) in lookup.timeline" :key="i">
            <div><span class="badge muted">{{ x.typeName }}</span> <b>{{ x.title }}</b>
              <span class="date"> · {{ x.date }}</span></div>
            <div class="small muted">{{ x.detail }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { api, APPT_STATUS, PRIOR_STATUS, getUser, previewFile } from '../api'

const isDoctor = getUser()?.role === 'DOCTOR'
const tab = ref('daily')
const toast = ref(''); const toastErr = ref(false)
const dailyDate = ref(new Date().toISOString().slice(0, 10))
const daily = ref([])
const children = ref([])
const vaccines = ref([])
const queue = ref([])
const editMap = reactive({})
const reviewPlans = ref([])
const consultations = ref([])
const openConsults = computed(() => consultations.value.filter(c => c.status === 'OPEN'))
const verifyApptId = ref('')
const checklist = ref(null)
const record = ref(null)
const batches = ref([])
const withhold = ref(false)
const showReview = ref(false)
const reviewForm = reactive({ approved: true, note: '' })
const lookupId = ref('')
const lookup = ref(null)
const replyMap = reactive({})
const alertMap = reactive({})

const form = reactive({
  identityVerified: false, batchVerified: false, consentSigned: false,
  recentFeverChecked: false, recentFever: 'false', contraindicationChecked: false,
  actualBatchNo: '', verifyNote: '', withholdReason: ''
})
const obs = reactive({ onSiteReaction: '无异常', guardianConfirmed: false, abnormal: false, abnormalSymptoms: '' })

function ast(s) { return APPT_STATUS[s] || { text: s, cls: 'muted' } }
function priorSt(s) { return PRIOR_STATUS[s] || { text: s, cls: 'muted' } }
function recBadge(s) {
  return { COMPLETED: { text: '完成留观', cls: 'ok' }, ABNORMAL: { text: '现场异常', cls: 'danger' },
    WITHHELD: { text: '暂缓未种', cls: 'warn' }, OBSERVING: { text: '留观中', cls: 'warn' } }[s] || { text: s, cls: 'muted' }
}
function showToast(m, e) { toast.value = m; toastErr.value = !!e; setTimeout(() => toast.value = '', 3500) }
const healthRisk = computed(() => /发热|发烧|皮疹|呕吐|腹泻/.test(checklist.value?.appointment?.child?.healthStatus || ''))
const usableBatches = computed(() => {
  const code = checklist.value?.appointment?.vaccineCode
  const today = dailyDate.value
  return batches.value.filter(b => b.vaccineCode === code && b.quantity > 0
    && b.coldChainStatus === 'NORMAL' && b.expiryDate >= today)
})

async function loadDaily() {
  daily.value = await api.get('/api/appointments/daily?date=' + dailyDate.value)
  if (verifyApptId.value) await loadChecklist()
}
async function loadChildren() { children.value = await api.get('/api/children') }
async function loadReview() {
  const all = []
  for (const c of children.value) {
    const ps = await api.get('/api/plans/child/' + c.id)
    ps.filter(p => p.status === 'REVIEW' || p.status === 'CONTRA').forEach(p => all.push(p))
  }
  reviewPlans.value = all
}
async function loadConsults() { consultations.value = await api.get('/api/consultations/open') }
async function loadQueue() {
  const rows = await api.get('/api/migration/queue')
  // 先准备好每行的编辑模型，再上屏，避免中间帧 v-model 取到 undefined
  for (const row of rows) {
    const p = row.prior
    if (!editMap[p.id]) {
      editMap[p.id] = {
        vaccineCode: p.vaccineCode || '', doseNo: p.doseNo ?? '',
        vaccinationDate: p.vaccinationDate || '', batchNo: p.batchNo || '',
        clinicName: p.clinicName || '', note: ''
      }
    }
  }
  queue.value = rows
}
async function viewDoc(docId) {
  try {
    const url = await previewFile('/api/migration-docs/' + docId + '/file')
    window.open(url, '_blank')
  } catch (e) { showToast(e.message, true) }
}
async function confirmPrior(p) {
  const e = editMap[p.id]
  if (!e.vaccineCode) return showToast('记录未能识别疫苗，请先在下拉中纠正为正确疫苗', true)
  await api.post('/api/migration/priors/' + p.id + '/confirm', { ...e })
  showToast('已核验采信：该剂跳过，补种计划已重算并重新开放后续预约时段')
  await loadQueue(); await loadReview()
}
async function rejectPrior(p) {
  const note = prompt('不予采信原因（将通知家长补证或补种；如原件模糊，请先在上方纠正疫苗/剂次/日期）', '接种本印章不清，无法核实')
  if (note === null) return
  const e = editMap[p.id] || {}
  await api.post('/api/migration/priors/' + p.id + '/reject', { ...e, note })
  showToast('已驳回' + (e.doseNo ? '：该剂转为追加补种且原因已绑定到具体剂次' : '：剂次不明，未绑定具体补种剂（请补证后处理）'))
  await loadQueue(); await loadReview()
}

async function switchTab(k) {
  tab.value = k
  if (k === 'review') loadReview()
  if (k === 'consult') loadConsults()
  if (k === 'migration') loadQueue()
}
async function checkIn(a) {
  await api.post('/api/appointments/' + a.id + '/check-in')
  showToast(a.child.name + ' 已签到')
  await loadDaily()
}
function goVerify(a) { verifyApptId.value = a.id; tab.value = 'verify'; loadChecklist() }
async function goRecord(a) {
  verifyApptId.value = a.id; tab.value = 'verify'; await loadChecklist()
}
async function loadChecklist() {
  if (!verifyApptId.value) return
  checklist.value = await api.get('/api/vaccinations/appointments/' + verifyApptId.value + '/checklist')
  record.value = checklist.value.record && checklist.value.record.id ? checklist.value.record : null
  Object.assign(form, { identityVerified: false, batchVerified: false, consentSigned: false,
    recentFeverChecked: false, recentFever: 'false', contraindicationChecked: false,
    actualBatchNo: checklist.value.appointment.reservedBatchNo || '', verifyNote: '', withholdReason: '' })
  Object.assign(obs, { onSiteReaction: '无异常', guardianConfirmed: false, abnormal: false, abnormalSymptoms: '' })
}
async function doVaccinate() {
  try {
    const payload = { ...form, recentFever: form.recentFever === 'true', withhold: false }
    record.value = await api.post('/api/vaccinations/appointments/' + verifyApptId.value + '/verify', payload)
    showToast('接种完成，已扣减批号库存，进入 30 分钟留观')
    await loadDaily()
  } catch (e) { showToast(e.message, true) }
}
async function doWithhold() {
  if (!form.withholdReason) return showToast('请填写暂缓原因', true)
  await api.post('/api/vaccinations/appointments/' + verifyApptId.value + '/verify',
    { ...form, withhold: true })
  withhold.value = false
  showToast('已暂缓并推送医生/随访人员，号源已释放')
  await loadDaily(); verifyApptId.value = ''; checklist.value = null
}
async function doObserve() {
  try {
    record.value = await api.post('/api/vaccinations/' + record.value.id + '/observation', { ...obs })
    showToast(obs.abnormal ? '已建立 AEFI 个案并推送随访人员' : '留观完成，已写入儿童健康档案与后续补种计划')
    await loadDaily()
  } catch (e) { showToast(e.message, true) }
}
async function resolveAbnormal() {
  record.value = await api.post('/api/vaccinations/' + record.value.id + '/resolve-abnormal')
  showToast('已转正常完成并回写计划')
}
async function clinicCancel(a) {
  const reason = prompt('门诊取消原因（将通知家长）')
  if (!reason) return
  await api.post('/api/appointments/' + a.id + '/cancel', { reason })
  await loadDaily()
}
async function approvePlan(p) {
  const note = prompt('复核意见（同意接种原因/注意事项）', '已核对过敏史与禁忌，可在留观加强观察下接种')
  if (note === null) return
  await api.post('/api/plans/' + p.id + '/approve', { note })
  showToast('已开放该剂次预约')
  loadReview()
}
async function submitReview() {
  await api.post('/api/vaccinations/' + record.value.id + '/doctor-review', { ...reviewForm })
  showReview.value = false
  showToast('复核意见已记录')
  await loadChecklist()
}
function onReplyInput(id, e) { replyMap[id] = { reply: e.target.value } }
async function reply(c) {
  const replyText = replyMap[c.id]?.reply
  if (!replyText) return showToast('请填写回复', true)
  await api.put('/api/consultations/' + c.id + '/reply',
    { reply: replyText, preVaccineAlert: !!alertMap[c.id], alertNote: replyText })
  showToast('已回复家长' + (alertMap[c.id] ? '，并登记接种前提醒' : ''))
  loadConsults()
}
async function loadLookup() {
  if (!lookupId.value) { lookup.value = null; return }
  lookup.value = await api.get('/api/children/' + lookupId.value + '/health-record')
}

onMounted(async () => {
  batches.value = await api.get('/api/catalog/batches')
  vaccines.value = await api.get('/api/catalog/vaccines')
  await loadChildren()
  await loadDaily()
  loadConsults()
  if (isDoctor) loadReview()
})
</script>
<style scoped>
.style-danger { color: var(--danger); }
</style>
