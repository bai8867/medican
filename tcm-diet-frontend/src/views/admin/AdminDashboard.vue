<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, shallowRef, nextTick } from 'vue'
import * as echarts from 'echarts'
import * as XLSX from 'xlsx'
import { ElMessage } from 'element-plus'
import { getCurrentSeasonCode, getSeasonLabel, SEASON_OPTIONS } from '@/utils/season.js'
import {
  getPlatformTotalCollectCount,
  getTopRecipesByCollect,
  getSeasonalTopRecipes,
  MOCK_PLATFORM_USER_TOTAL,
  MOCK_CONSTITUTION_USER_COUNTS,
  formatIngredientList,
  formatSuitConstitutions,
} from '@/utils/dashboardMockData.js'
import {
  fetchDashboardOverview,
  fetchDashboardConstitutionDistribution,
} from '@/api/adminDashboard.js'

const overviewLoading = ref(true)
const overview = ref({
  totalCollectCount: 0,
  hotTop3: [],
  userTotal: 0,
})
const constitutionPie = ref([])
const pieSubtext = ref('加载中…')

const totalCollect = computed(() => overview.value.totalCollectCount)
const top3 = computed(() => overview.value.hotTop3)
const userTotal = computed(() => overview.value.userTotal)

/** 应季推荐表：默认当前季节，可手动切换 */
const seasonalPanelSeason = ref(getCurrentSeasonCode())
const seasonalPanelLabel = computed(() => getSeasonLabel(seasonalPanelSeason.value))
const seasonalList = computed(() => getSeasonalTopRecipes(seasonalPanelSeason.value, 5))

const chartSeason = ref('')
const barRef = shallowRef(null)
const pieRef = shallowRef(null)
let barChart = null
let pieChart = null
let roBar = null
let roPie = null

const barData = computed(() => {
  const code = chartSeason.value || null
  return getTopRecipesByCollect(10, code)
})

async function loadDashboard() {
  overviewLoading.value = true
  try {
    const [o, c] = await Promise.all([
      fetchDashboardOverview(),
      fetchDashboardConstitutionDistribution(),
    ])
    overview.value = {
      totalCollectCount: Number(o?.totalCollectCount) || 0,
      hotTop3: Array.isArray(o?.hotTop3) ? o.hotTop3 : [],
      userTotal: Number(o?.userTotal) || 0,
    }
    constitutionPie.value = Array.isArray(c?.items) ? c.items : []
    pieSubtext.value = '数据来源：用户表体质字段统计'
  } catch {
    overview.value = {
      totalCollectCount: getPlatformTotalCollectCount(),
      hotTop3: getTopRecipesByCollect(3, null).map((r) => ({
        id: r.id,
        name: r.name,
        collectCount: Number(r.collectCount) || 0,
      })),
      userTotal: MOCK_PLATFORM_USER_TOTAL,
    }
    constitutionPie.value = MOCK_CONSTITUTION_USER_COUNTS.map((x) => ({ ...x }))
    pieSubtext.value = '统计接口不可用，已使用本地示意数据'
  } finally {
    overviewLoading.value = false
  }
}

function buildBarOption() {
  const rows = barData.value
  const names = rows.map((r) => r.name).reverse()
  const values = rows.map((r) => Number(r.collectCount) || 0).reverse()
  const sub = chartSeason.value ? `${getSeasonLabel(chartSeason.value)}季` : '全部季节'
  return {
    title: {
      text: `药膳收藏 TOP10（${sub}）`,
      left: 0,
      textStyle: { fontSize: 14, fontWeight: 600 },
    },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 24, top: 40, bottom: 12, containLabel: true },
    xAxis: { type: 'value', name: '收藏数' },
    yAxis: { type: 'category', data: names },
    series: [
      {
        type: 'bar',
        data: values,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#8fbc6b' },
            { offset: 1, color: '#5a8f3a' },
          ]),
        },
        label: { show: true, position: 'right', fontSize: 11 },
      },
    ],
  }
}

function buildPieOption() {
  const data =
    constitutionPie.value.length > 0 ? constitutionPie.value : MOCK_CONSTITUTION_USER_COUNTS
  return {
    title: {
      text: '用户体质分布',
      subtext: pieSubtext.value,
      left: 'center',
      textStyle: { fontSize: 14, fontWeight: 600 },
      subtextStyle: { fontSize: 11, color: '#888' },
    },
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 人（{d}%）' },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 4,
      top: 'middle',
      textStyle: { fontSize: 11 },
    },
    series: [
      {
        name: '体质',
        type: 'pie',
        radius: ['38%', '62%'],
        center: ['40%', '52%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 1 },
        label: { formatter: '{b}\n{d}%', fontSize: 11 },
        data,
      },
    ],
  }
}

function initCharts() {
  if (barRef.value && !barChart) {
    barChart = echarts.init(barRef.value)
    barChart.setOption(buildBarOption())
    roBar = new ResizeObserver(() => barChart?.resize())
    roBar.observe(barRef.value)
  }
  if (pieRef.value && !pieChart) {
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption(buildPieOption())
    roPie = new ResizeObserver(() => pieChart?.resize())
    roPie.observe(pieRef.value)
  }
}

function disposeCharts() {
  roBar?.disconnect()
  roPie?.disconnect()
  roBar = null
  roPie = null
  barChart?.dispose()
  pieChart?.dispose()
  barChart = null
  pieChart = null
}

watch(barData, () => {
  barChart?.setOption(buildBarOption(), true)
})

watch([constitutionPie, pieSubtext], () => {
  pieChart?.setOption(buildPieOption(), true)
})

onMounted(async () => {
  await loadDashboard()
  await nextTick()
  initCharts()
})

onBeforeUnmount(() => {
  disposeCharts()
})

function formatExportDateCompact(d = new Date()) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${day}`
}

function exportSeasonalExcel() {
  const seasonCol = `${seasonalPanelLabel.value}季`
  const rows = seasonalList.value.map((r) => ({
    药膳名称: r.name,
    食材清单: formatIngredientList(r) || '—',
    功效: r.effect || r.summary || '—',
    适用体质: formatSuitConstitutions(r),
    季节: seasonCol,
  }))
  if (!rows.length) {
    ElMessage.warning('所选季节暂无药膳数据')
    return
  }
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '应季菜谱')
  const fname = `应季菜谱_${seasonCol}_${formatExportDateCompact()}.xlsx`
  XLSX.writeFile(wb, fname)
  ElMessage.success('已导出 Excel')
}
</script>

<template>
  <div class="dash">
    <el-row v-loading="overviewLoading" :gutter="16" class="dash__metrics">
      <el-col :xs="24" :sm="8">
        <div class="metric ui-card ui-card--static">
          <div class="metric__label">总收藏数</div>
          <div class="metric__value">{{ totalCollect.toLocaleString() }}</div>
          <div class="metric__desc">全站药膳收藏量合计（接口汇总）</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="metric ui-card ui-card--static metric--top3">
          <div class="metric__label">热门药膳 TOP3</div>
          <ol class="metric__top3">
            <li v-for="(r, i) in top3" :key="r.id">
              <span class="metric__rank">{{ i + 1 }}</span>
              <span class="metric__name">{{ r.name }}</span>
              <span class="metric__count">{{ Number(r.collectCount).toLocaleString() }} 收藏</span>
            </li>
          </ol>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="metric ui-card ui-card--static">
          <div class="metric__label">用户总数</div>
          <div class="metric__value">{{ userTotal.toLocaleString() }}</div>
          <div class="metric__desc">注册用户数（用户表统计）</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="dash__row2">
      <el-col :xs="24" :lg="14">
        <div class="panel ui-card ui-card--static">
          <div class="panel__head">
            <div>
              <h2 class="panel__title">应季推荐</h2>
              <p class="panel__sub panel__sub--season">
                <span>查看季节：</span>
                <el-select
                  v-model="seasonalPanelSeason"
                  size="small"
                  class="panel__season-select"
                  aria-label="应季推荐季节"
                >
                  <el-option
                    v-for="s in SEASON_OPTIONS"
                    :key="s.code"
                    :label="`${s.label}季`"
                    :value="s.code"
                  />
                </el-select>
                <span> · 按收藏数取 TOP5，含适用体质</span>
              </p>
            </div>
            <el-button type="primary" @click="exportSeasonalExcel">导出应季菜谱清单</el-button>
          </div>
          <el-table :data="seasonalList" stripe border class="panel__table">
            <el-table-column label="#" width="48" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="name" label="药膳名称" min-width="120" />
            <el-table-column label="收藏数" width="100" align="right">
              <template #default="{ row }">
                {{ Number(row.collectCount).toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column label="适用体质" min-width="160">
              <template #default="{ row }">
                {{ formatSuitConstitutions(row) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :lg="10">
        <div class="panel ui-card ui-card--static panel--chart">
          <div ref="pieRef" class="dash__echart dash__echart--pie" />
        </div>
      </el-col>
    </el-row>

    <div class="panel ui-card ui-card--static">
      <div class="panel__head panel__head--chart">
        <h2 class="panel__title">热门收藏药膳</h2>
        <div class="panel__chart-filters">
          <span class="panel__filter-label">季节</span>
          <el-select
            v-model="chartSeason"
            placeholder="全部季节"
            clearable
            size="small"
            class="panel__chart-season"
            aria-label="热门收藏图表季节筛选"
          >
            <el-option label="全部季节" value="" />
            <el-option
              v-for="s in SEASON_OPTIONS"
              :key="s.code"
              :label="`${s.label}季`"
              :value="s.code"
            />
          </el-select>
        </div>
      </div>
      <div ref="barRef" class="dash__echart dash__echart--bar" />
    </div>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  max-width: 1200px;
  margin: 0 auto;
}

.dash__metrics {
  margin-bottom: 0;
}

.metric {
  height: 100%;
  min-height: 132px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.metric__value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.metric__desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: auto;
}

.metric--top3 .metric__label {
  margin-bottom: 4px;
}

.metric__top3 {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric__top3 li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-md);
}

.metric__rank {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--color-primary) 15%, transparent);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.metric__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric__count {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
  flex-wrap: wrap;
}

.panel__head--chart {
  align-items: center;
}

.panel__title {
  margin: 0 0 4px;
  font-size: var(--font-size-md);
  font-weight: 600;
}

.panel__sub {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.panel__sub--season {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.panel__season-select {
  width: 100px;
}

.panel__chart-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel__filter-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.panel__chart-season {
  width: 132px;
}

.panel__table {
  width: 100%;
}

.panel--chart {
  min-height: 360px;
}

.dash__echart {
  width: 100%;
}

.dash__echart--pie {
  height: 360px;
}

.dash__echart--bar {
  height: 420px;
}

.dash__row2 {
  align-items: stretch;
}

.dash__row2 .el-col {
  display: flex;
}

.dash__row2 .panel {
  flex: 1;
  width: 100%;
}
</style>
