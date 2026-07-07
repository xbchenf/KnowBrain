<template>
  <div>
    <!-- 统计卡片 -->
    <div class="stats-row" v-if="stats">
      <div class="stat-card">
        <div class="stat-num">{{ stats.totalQueries || 0 }}</div>
        <div class="stat-label">总问答</div>
      </div>
      <div class="stat-card faq">
        <div class="stat-num">{{ stats.faqHitRate || 0 }}%</div>
        <div class="stat-label">FAQ 命中率</div>
      </div>
      <div class="stat-card high">
        <div class="stat-num">{{ stats.confidenceDist?.high || 0 }}</div>
        <div class="stat-label">高置信回答</div>
      </div>
      <div class="stat-card low">
        <div class="stat-num">{{ stats.confidenceDist?.low || 0 }}</div>
        <div class="stat-label">低置信回答</div>
      </div>
    </div>

    <!-- 每日趋势 + 排行榜 -->
    <div class="charts-row" v-if="stats">
      <div class="panel trend-panel">
        <h4>近 30 天问答趋势</h4>
        <div class="line-chart-wrap" v-if="trendBars.length > 0">
          <svg class="line-chart" :viewBox="`0 0 ${lineChart.width} ${lineChart.height}`" preserveAspectRatio="xMidYMid meet">
            <!-- Y 轴网格线 -->
            <line
              v-for="(y, i) in lineChart.gridLines"
              :key="'g'+i"
              :x1="lineChart.padL" :y1="y" :x2="lineChart.width - 10" :y2="y"
              stroke="#ebeef5" stroke-width="1"
            />
            <!-- Y 轴刻度 -->
            <text
              v-for="(y, i) in lineChart.gridLines"
              :key="'yt'+i"
              :x="lineChart.padL - 6" :y="y + 4"
              text-anchor="end" fill="#c0c4cc" font-size="10"
            >{{ lineChart.yLabels[i] }}</text>
            <!-- X 轴刻度 -->
            <text
              v-for="(t, i) in lineChart.xTicks"
              :key="'xt'+i"
              :x="t.x" :y="lineChart.height - 2"
              text-anchor="middle" fill="#c0c4cc" font-size="10"
            >{{ t.label }}</text>
            <!-- 折线 -->
            <polyline
              :points="lineChart.linePoints"
              fill="none" stroke="#409EFF" stroke-width="2" stroke-linejoin="round"
            />
            <!-- 数据点 -->
            <circle
              v-for="(p, i) in lineChart.points"
              :key="'dp'+i"
              :cx="p.x" :cy="p.y" r="3"
              fill="#409EFF" stroke="#fff" stroke-width="1.5"
            >
              <title>{{ trendBars[i]?.date }}: {{ trendBars[i]?.count }} 次</title>
            </circle>
            <!-- 鼠标悬浮竖线 + 提示 -->
            <line
              v-for="(p, i) in lineChart.points"
              :key="'hl'+i"
              :x1="p.x" :y1="lineChart.padT" :x2="p.x" :y2="lineChart.height - 20"
              stroke="transparent" stroke-width="8"
              @mouseenter="hoverIdx = i" @mouseleave="hoverIdx = -1"
            />
            <line v-if="hoverIdx >= 0" :x1="lineChart.points[hoverIdx].x" :y1="lineChart.padT"
                  :x2="lineChart.points[hoverIdx].x" :y2="lineChart.height - 20"
                  stroke="#409EFF" stroke-width="1" stroke-dasharray="3,3" opacity="0.5"/>
            <circle v-if="hoverIdx >= 0" :cx="lineChart.points[hoverIdx].x"
                    :cy="lineChart.points[hoverIdx].y" r="5"
                    fill="#409EFF" stroke="#fff" stroke-width="2"/>
            <rect v-if="hoverIdx >= 0"
                  :x="Math.max(4, lineChart.points[hoverIdx].x - 42)"
                  :y="lineChart.points[hoverIdx].y - 28"
                  width="84" height="22" rx="4" fill="rgba(48,49,51,0.85)"/>
            <text v-if="hoverIdx >= 0"
                  :x="lineChart.points[hoverIdx].x" :y="lineChart.points[hoverIdx].y - 12"
                  text-anchor="middle" fill="#fff" font-size="11">
              {{ trendBars[hoverIdx]?.date }} · {{ trendBars[hoverIdx]?.count }} 次
            </text>
          </svg>
        </div>
        <el-empty v-else description="暂无数据" :image-size="48" />
      </div>

      <div class="panel rank-panel">
        <div class="rank-half">
          <h4>🔥 热门问题 Top 10</h4>
          <ol v-if="stats.topQuestions?.length">
            <li v-for="(q, i) in stats.topQuestions" :key="i">
              <span class="rank-text">{{ q.question }}</span>
              <span class="rank-count">{{ q.count }}</span>
            </li>
          </ol>
          <el-empty v-else description="暂无数据" :image-size="40" />
        </div>
        <div class="rank-half">
          <h4>📄 热门文档 Top 10</h4>
          <ol v-if="stats.topSources?.length">
            <li v-for="(s, i) in stats.topSources" :key="i">
              <span class="rank-text">{{ s.title }}</span>
              <span class="rank-count">{{ s.count }}</span>
            </li>
          </ol>
          <el-empty v-else description="暂无数据" :image-size="40" />
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!stats" description="暂无统计数据" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getStats } from '../api'

const stats = ref<any>(null)
const hoverIdx = ref(-1)

const trendBars = computed(() => {
  const arr = stats.value?.dailyTrend || []
  if (!arr.length) return []
  return arr.map((d: any) => ({
    date: d.date,
    count: d.count,
    label: d.date?.substring(5) || ''
  }))
})

// SVG 折线图参数
const LC = { padT: 12, padL: 38, padB: 20, padR: 10 }
const lineChart = computed(() => {
  const bars = trendBars.value
  const w = 800, h = 200
  const plotW = w - LC.padL - LC.padR
  const plotH = h - LC.padT - LC.padB

  const max = Math.max(...bars.map(b => b.count), 1)
  const yMax = Math.ceil(max * 1.2) || 10

  // 网格线（4 条 = 5 段）
  const gridLines: number[] = []
  const yLabels: string[] = []
  for (let i = 0; i <= 4; i++) {
    gridLines.push(LC.padT + (plotH / 4) * i)
    yLabels.push(String(Math.round(yMax - (yMax / 4) * i)))
  }

  // 数据点坐标
  const points = bars.map((b, i) => ({
    x: LC.padL + (bars.length === 1 ? plotW / 2 : (plotW / (bars.length - 1)) * i),
    y: LC.padT + plotH - (b.count / yMax) * plotH
  }))
  const linePoints = points.map(p => `${p.x},${p.y}`).join(' ')

  // X 轴刻度（最多显示 6 个标签，避免重叠）
  const maxTicks = Math.min(bars.length, 6)
  const step = Math.max(1, Math.floor(bars.length / maxTicks))
  const xTicks: { x: number; label: string }[] = []
  for (let i = 0; i < bars.length; i += step) {
    xTicks.push({ x: LC.padL + (plotW / (bars.length - 1 || 1)) * i, label: bars[i].label })
  }

  return { width: w, height: h, padT: LC.padT, padL: LC.padL, gridLines, yLabels, points, linePoints, xTicks }
})

onMounted(async () => {
  try {
    const { data } = await getStats()
    if (data?.code === 200) {
      stats.value = data.data
    }
  } catch { /* ignore */ }
})
</script>

<style scoped>
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.stat-num { font-size: 28px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.stat-card.faq .stat-num { color: #409EFF; }
.stat-card.high .stat-num { color: #67c23a; }
.stat-card.low .stat-num { color: #f56c6c; }

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.panel h4 { font-size: 14px; color: #303133; margin: 0 0 14px; }

/* 折线图 */
.line-chart-wrap {
  width: 100%;
}
.line-chart {
  width: 100%;
  height: auto;
  display: block;
}
.line-chart text {
  font-family: inherit;
  user-select: none;
}
.line-chart line[stroke="transparent"] {
  cursor: pointer;
}

/* 排行榜 */
.rank-panel { display: flex; gap: 24px; }
.rank-half { flex: 1; min-width: 0; }
.rank-half ol {
  list-style: none;
  padding: 0;
  margin: 0;
  counter-reset: rank;
}
.rank-half li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid #f5f7fa;
  counter-increment: rank;
  font-size: 13px;
}
.rank-half li::before {
  content: counter(rank);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
  flex-shrink: 0;
}
.rank-half li:nth-child(1)::before { background: #f56c6c; color: #fff; }
.rank-half li:nth-child(2)::before { background: #e6a23c; color: #fff; }
.rank-half li:nth-child(3)::before { background: #409EFF; color: #fff; }
.rank-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #606266; }
.rank-count { font-size: 12px; color: #909399; flex-shrink: 0; margin-left: 8px; }
</style>
