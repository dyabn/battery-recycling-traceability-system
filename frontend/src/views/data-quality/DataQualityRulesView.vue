<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>质量规则</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rules" v-loading="loading" stripe>
      <el-table-column prop="ruleCode" label="规则" width="100" />
      <el-table-column prop="ruleName" label="名称" min-width="180" />
      <el-table-column prop="dimension" label="维度" width="110" />
      <el-table-column prop="objectType" label="对象" width="110" />
      <el-table-column prop="severity" label="级别" width="90" />
      <el-table-column prop="defaultOwnerRoleCode" label="责任角色" min-width="150" />
      <el-table-column prop="enabledStatus" label="状态" width="100" />
      <el-table-column label="数据标准" min-width="220">
        <template #default="{ row }">
          {{ formatMetadata(row.dataStandardMetadata) }}
        </template>
      </el-table-column>
      <el-table-column label="涉及字段" min-width="240">
        <template #default="{ row }">
          {{ row.involvedFields.join('、') }}
        </template>
      </el-table-column>
      <el-table-column prop="checkCondition" label="检查条件" min-width="260" />
      <el-table-column prop="remediationGuidance" label="整改指导" min-width="260" />
      <el-table-column label="允许豁免" width="100">
        <template #default="{ row }">
          {{ row.allowExemption ? '是' : '否' }}
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { listDataQualityRules, type DataQualityRule } from '../../api/i2';

const rules = ref<DataQualityRule[]>([]);
const loading = ref(false);

function formatMetadata(metadata: Record<string, unknown>) {
  return Object.entries(metadata || {})
    .map(([key, value]) => `${key}: ${String(value)}`)
    .join('；');
}

async function load() {
  loading.value = true;
  try {
    rules.value = await listDataQualityRules();
  } finally {
    loading.value = false;
  }
}

onMounted(load);

defineExpose({ rules, load, formatMetadata });
</script>
