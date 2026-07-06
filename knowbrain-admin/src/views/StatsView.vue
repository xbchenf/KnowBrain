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
        <div class="bar-chart" v-if="trendBars.length > 0">
          <div class="bar-col" v-for="b in trendBars" :key="b.date" :title="`${b.date}: ${b.count} 次`">
            <div class="bar-fill" :style="{ height: b.pct + '%' }"></div>
            <div class="bar-label">{{ b.label }}</div>
          </div>
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

const trendBars = computed(() => {
  const arr = stats.value?.dailyTrend || []
  if (!arr.length) return []
  const max = Math.max(...arr.map((d: any) => d.count), 1)
  return arr.map((d: any) => ({
    date: d.date,
    count: d.count,
    label: d.date?.substring(5) || '',
    pct: Math.round((d.count / max) * 100)
  }))
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

/* 柱状图 */
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 120px;
  padding: 0 4px;
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
}
.bar-fill {
  width: 100%;
  max-width: 20px;
  background: linear-gradient(180deg, #409EFF, #79bbff);
  border-radius: 3px 3px 0 0;
  transition: height .3s;
  min-height: 2px;
}
.bar-label {
  font-size: 9px;
  color: #c0c4cc;
  margin-top: 4px;
  writing-mode: vertical-lr;
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
