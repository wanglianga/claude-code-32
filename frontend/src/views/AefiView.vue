<template>
  <div class="page">
    <div v-if="toast" class="toast" :class="{ err: toastErr }">{{ toast }}</div>

    <div class="card row" style="justify-content: space-between">
      <div><h2 style="margin:0">疑似预防接种异常反应（AEFI）随访</h2>
        <span class="muted small">关联接种批号 · 症状 · 就医记录 · 最终判断 · 区级上报，可按儿童/疫苗/批号追踪</span></div>
      <button @click="openCreate">手工报卡（家长咨询/随访发现）</button>
    </div>

    <div class="card row">
      <select v-model="filter.childId" @change="search" style="max-width:240px">
        <option value="">全部儿童</option>
        <option v-for="c in children" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <select v-model="filter.vaccineCode" @change="search" style="max-width:240px">
        <option value="">全部疫苗</option>
        <option v-for="v in vaccines" :key="v.code" :value="v.code">{{ v.name }}</option>
      </select>
      <input v-model="filter.batchNo" placeholder="按批号筛选" @keyup.enter="search" style="max-width:180px" />
      <label class="row small"><input type="checkbox" v-model="filter.openOnly" @change="search" style="width:auto"/> 仅看未结案</label>
      <button class="btn-ghost btn-sm" @click="search">查询</button>
    </div>

    <div class="card">
      <table>
        <thead><tr><th>儿童</th><th>疫苗</th><th>批号</th><th>接种日期</th><th>症状</th><th>发病</th><th>来源</th><th>状态</th><th>最终判断</th><th>区级</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in cases" :key="c.id">
            <td>{{ c.child?.name }}</td>
            <td class="small">{{ c.vaccineName }} 第{{ c.doseNo }}剂</td>
            <td class="small">{{ c.batchNo }}</td>
            <td>{{ c.vaccinationDate }}</td>
            <td class="small" style="max-width:200px">{{ c.symptoms }}</td>
            <td>{{ c.onsetDate }}</td>
            <td class="small">{{ c.source }}</td>
            <td><span class="badge" :class="aefiBadge(c.status).cls">{{ aefiBadge(c.status).text }}</span></td>
            <td class="small">{{ c.finalConclusion || '—' }}</td>
            <td><span v-if="c.districtReported" class="badge ok">已上报</span><span v-else class="muted">—</span></td>
            <td><button class="btn-sm" @click="openCase(c)">追踪/随访</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 个案详情 -->
    <div v-if="current" class="modal-mask" @click.self="current = null">
      <div class="modal" style="width:820px">
        <h3>AEFI 个案 — {{ current.child?.name }} {{ current.vaccineName }}</h3>
        <div class="grid grid-2">
          <div>
            <p class="small">批号：<b>{{ current.batchNo }}</b>｜第{{ current.doseNo }}剂｜接种 {{ current.vaccinationDate }}｜发病 {{ current.onsetDate }}</p>
            <p class="small">症状：{{ current.symptoms }}</p>
            <p class="small" v-if="current.symptomDetail">详情：{{ current.symptomDetail }}</p>
            <p class="small">就医医院：{{ current.hospital || '—' }}</p>
            <p class="small">就医记录：{{ current.medicalRecord || '—' }}</p>
            <p class="small">最终判断：<b>{{ current.finalConclusion || '待判断' }}</b>
              <span v-if="current.conclusionNote">（{{ current.conclusionNote }}）</span></p>
          </div>
          <div>
            <h4>随访记录（{{ followUps.length }}）</h4>
            <div class="timeline" style="max-height:260px; overflow:auto">
              <div class="tl-item" v-for="f in followUps" :key="f.id">
                <div class="date">{{ f.followDate }} · {{ methodText(f.method) }} · {{ f.followUserName }}</div>
                <div class="small">体温 {{ f.temperature || '—' }}℃；{{ f.symptomsStatus }}
                  <span class="badge" :class="outcomeCls(f.outcome)">{{ outcomeText(f.outcome) }}</span></div>
                <div class="small muted">指导：{{ f.advice }}；下次随访 {{ f.nextFollowDate || '无' }}</div>
              </div>
            </div>
          </div>
        </div>

        <h4 class="mt16">新增随访</h4>
        <div class="grid grid-3">
          <label class="field"><span>随访日期</span><input type="date" v-model="fu.followDate" /></label>
          <label class="field"><span>方式</span><select v-model="fu.method">
            <option value="PHONE">电话</option><option value="ONSITE">门诊</option><option value="HOME">入户</option></select></label>
          <label class="field"><span>体温(℃)</span><input type="number" step="0.1" v-model.number="fu.temperature" /></label>
          <label class="field"><span>症状/转归描述</span><input v-model="fu.symptomsStatus" /></label>
          <label class="field"><span>处理指导</span><input v-model="fu.advice" /></label>
          <label class="field"><span>转归</span><select v-model="fu.outcome">
            <option value="ONGOING">持续</option><option value="RESOLVED">已好转</option>
            <option value="WORSENED">加重要就医</option><option value="HOSPITALIZED">已住院</option></select></label>
          <label class="field"><span>下次随访日期（到期自动提醒）</span><input type="date" v-model="fu.nextFollowDate" /></label>
        </div>
        <div class="row">
          <button @click="addFollowUp">保存随访</button>
        </div>

        <h4 class="mt16">门诊处理（医生/管理员）</h4>
        <div class="row" v-if="canDoctor">
          <button class="btn-ghost" @click="editMedical">登记就医记录</button>
          <button class="btn-ghost" @click="concludeOpen = true">最终判断/结案</button>
          <button class="btn-danger" :disabled="current.districtReported" @click="reportDistrict">生成区级上报</button>
        </div>

        <div v-if="concludeOpen" class="mt8">
          <div class="row">
            <select v-model="conclusion.value" style="max-width:200px">
              <option v-for="x in conclusions" :key="x" :value="x">{{ x }}</option>
            </select>
            <label class="row small"><input type="checkbox" v-model="conclusion.closeCase" style="width:auto"/> 同时结案</label>
          </div>
          <label class="field mt8"><span>判断说明</span><input v-model="conclusion.note" /></label>
          <button class="btn-sm" @click="submitConclusion">提交最终判断</button>
        </div>

        <div class="mt16" style="text-align:right"><button class="btn-ghost" @click="current = null">关闭</button></div>
      </div>
    </div>

    <!-- 手工报卡 -->
    <div v-if="creating" class="modal-mask" @click.self="creating = false">
      <div class="modal">
        <h3>手工登记 AEFI</h3>
        <div class="grid grid-2">
          <label class="field"><span>儿童</span><select v-model="nc.childId">
            <option :value="null">选择儿童</option>
            <option v-for="c in children" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
          <label class="field"><span>疫苗</span><select v-model="nc.vaccineCode">
            <option v-for="v in vaccines" :key="v.code" :value="v.code">{{ v.name }}</option></select></label>
          <label class="field"><span>批号</span><input v-model="nc.batchNo" /></label>
          <label class="field"><span>剂次</span><input type="number" v-model.number="nc.doseNo" /></label>
          <label class="field"><span>接种日期</span><input type="date" v-model="nc.vaccinationDate" /></label>
          <label class="field"><span>发病日期</span><input type="date" v-model="nc.onsetDate" /></label>
        </div>
        <label class="field"><span>症状（发热/皮疹等）</span><input v-model="nc.symptoms" /></label>
        <label class="field"><span>详细经过</span><textarea v-model="nc.symptomDetail" rows="2"></textarea></label>
        <div class="row" style="justify-content:flex-end">
          <button class="btn-ghost" @click="creating = false">取消</button>
          <button @click="submitCreate">登记并推送随访</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api, AEFI_STATUS, getUser } from '../api'

const user = getUser()
const canDoctor = ['DOCTOR', 'ADMIN'].includes(user.role)
const toast = ref(''); const toastErr = ref(false)
const children = ref([]); const vaccines = ref([])
const cases = ref([]); const current = ref(null); const followUps = ref([])
const creating = ref(false)
const concludeOpen = ref(false)
const filter = reactive({ childId: '', vaccineCode: '', batchNo: '', openOnly: true })
const fu = reactive({ followDate: today(), method: 'PHONE', temperature: null, symptomsStatus: '', advice: '', outcome: 'ONGOING', nextFollowDate: '' })
const nc = reactive({ childId: null, vaccineCode: '', batchNo: '', doseNo: 1, vaccinationDate: today(), onsetDate: today(), symptoms: '', symptomDetail: '' })
const conclusion = reactive({ value: '一般反应', note: '', closeCase: true })
const conclusions = ['一般反应', '异常反应', '偶合症', '心因性反应', '不能排除', '无因果关系']

function today() { return new Date().toISOString().slice(0, 10) }
function showToast(m, e) { toast.value = m; toastErr.value = !!e; setTimeout(() => toast.value = '', 3500) }
function aefiBadge(s) { return AEFI_STATUS[s] || { text: s, cls: 'muted' } }
function methodText(m) { return { PHONE: '电话随访', ONSITE: '门诊随访', HOME: '入户随访' }[m] || m }
function outcomeText(o) { return { RESOLVED: '已好转', ONGOING: '持续', WORSENED: '加重要就医', HOSPITALIZED: '已住院' }[o] || o }
function outcomeCls(o) { return o === 'RESOLVED' ? 'ok' : o === 'WORSENED' || o === 'HOSPITALIZED' ? 'danger' : 'warn' }

async function search() {
  const p = new URLSearchParams()
  if (filter.childId) p.set('childId', filter.childId)
  if (filter.vaccineCode) p.set('vaccineCode', filter.vaccineCode)
  if (filter.batchNo) p.set('batchNo', filter.batchNo)
  p.set('openOnly', filter.openOnly)
  cases.value = await api.get('/api/aefi?' + p.toString())
}
async function openCase(c) {
  current.value = c
  concludeOpen.value = false
  followUps.value = await api.get('/api/aefi/' + c.id + '/follow-ups')
}
async function addFollowUp() {
  if (!fu.symptomsStatus) return showToast('请填写症状转归', true)
  await api.post('/api/aefi/' + current.value.id + '/follow-ups', { ...fu })
  showToast('随访已保存' + (fu.outcome === 'RESOLVED' ? '，已提醒医生做最终判断' : ''))
  await openCase((await api.get('/api/aefi?openOnly=false')).find(x => x.id === current.value.id) || current.value)
  await search()
}
async function editMedical() {
  const hospital = prompt('就诊医院', current.value.hospital || '')
  if (hospital === null) return
  const medicalRecord = prompt('诊断与就医记录', current.value.medicalRecord || '')
  if (medicalRecord === null) return
  await api.put('/api/aefi/' + current.value.id + '/medical', { hospital, medicalRecord })
  showToast('就医记录已保存')
  const fresh = (await api.get('/api/aefi?openOnly=false')).find(x => x.id === current.value.id)
  current.value = fresh
}
async function submitConclusion() {
  await api.put('/api/aefi/' + current.value.id + '/conclusion', { ...conclusion })
  concludeOpen.value = false
  showToast('最终判断已提交')
  await search()
  const fresh = cases.value.find(x => x.id === current.value.id)
  if (fresh) current.value = fresh
}
async function reportDistrict() {
  await api.post('/api/aefi/' + current.value.id + '/report-district')
  showToast('已生成区级上报材料并通知管理员')
  await search()
  const fresh = cases.value.find(x => x.id === current.value.id)
  if (fresh) current.value = fresh
}
function openCreate() { creating.value = true }
async function submitCreate() {
  if (!nc.childId || !nc.vaccineCode || !nc.batchNo || !nc.symptoms) return showToast('请完善儿童/疫苗/批号/症状', true)
  await api.post('/api/aefi', { ...nc, source: '家长咨询' })
  creating.value = false
  showToast('AEFI 已登记，医生/护士/随访人员均已收到推送')
  await search()
}

onMounted(async () => {
  children.value = await api.get('/api/children')
  vaccines.value = await api.get('/api/catalog/vaccines')
  await search()
})
</script>
