<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>质量问题</h2>
      <div class="toolbar">
        <el-select v-model="status" clearable placeholder="状态" style="width: 160px">
          <el-option label="OPEN" value="OPEN" />
          <el-option label="ASSIGNED" value="ASSIGNED" />
          <el-option label="PROCESSING" value="PROCESSING" />
          <el-option label="CLOSED" value="CLOSED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="issues" v-loading="loading" stripe>
      <el-table-column prop="issueNo" label="问题编号" min-width="190" />
      <el-table-column prop="ruleCode" label="规则" width="100" />
      <el-table-column prop="objectType" label="对象" width="110" />
      <el-table-column prop="objectIdentity" label="对象标识" min-width="150" />
      <el-table-column prop="severity" label="级别" width="90" />
      <el-table-column prop="ownerRoleCode" label="责任角色" min-width="150" />
      <el-table-column prop="issueStatus" label="状态" width="110" />
      <el-table-column prop="lastDetectedAt" label="最近发现" min-width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/data-quality/issues/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { listDataQualityIssues, type DataQualityIssue } from '../../api/i2';

const issues = ref<DataQualityIssue[]>([]);
const status = ref('OPEN');
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    issues.value = await listDataQualityIssues(status.value);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

defineExpose({ issues, status, load });
</script>
