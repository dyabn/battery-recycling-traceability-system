<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>{{ run?.runNo || '检查结果' }}</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-descriptions v-if="run" :column="3" border>
      <el-descriptions-item label="状态">{{ run.runStatus }}</el-descriptions-item>
      <el-descriptions-item label="范围">{{ run.runScope }}</el-descriptions-item>
      <el-descriptions-item label="规则数">{{ run.checkedRuleCount }}</el-descriptions-item>
      <el-descriptions-item label="对象数">{{ run.checkedObjectCount }}</el-descriptions-item>
      <el-descriptions-item label="问题数">{{ run.issueCount }}</el-descriptions-item>
      <el-descriptions-item label="新增/去重">{{ run.resultSummary?.newIssueCount || 0 }} / {{ run.resultSummary?.dedupHitCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ run.startedAt }}</el-descriptions-item>
      <el-descriptions-item label="完成时间">{{ run.completedAt || '-' }}</el-descriptions-item>
      <el-descriptions-item label="失败原因">{{ run.failureReason || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-table :data="results" v-loading="loading" stripe>
      <el-table-column prop="ruleCode" label="规则" width="100" />
      <el-table-column prop="checkStatus" label="结果" width="120" />
      <el-table-column prop="checkedObjectCount" label="对象数" width="100" />
      <el-table-column prop="violationCount" label="违规数" width="100" />
      <el-table-column prop="errorMessage" label="错误" min-width="220" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import {
  getDataQualityCheckRun,
  listDataQualityCheckResults,
  type DataQualityCheckResult,
  type DataQualityCheckRun,
} from '../../api/i2';

const route = useRoute();
const runId = computed(() => String(route.params.runId));
const run = ref<DataQualityCheckRun | null>(null);
const results = ref<DataQualityCheckResult[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const [nextRun, nextResults] = await Promise.all([
      getDataQualityCheckRun(runId.value),
      listDataQualityCheckResults(runId.value),
    ]);
    run.value = nextRun;
    results.value = nextResults;
  } finally {
    loading.value = false;
  }
}

onMounted(load);

defineExpose({ run, results, load });
</script>
