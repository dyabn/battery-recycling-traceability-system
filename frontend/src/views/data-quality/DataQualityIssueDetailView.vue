<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>{{ issue?.issueNo || '质量问题详情' }}</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-descriptions v-if="issue" :column="2" border>
      <el-descriptions-item label="规则">{{ issue.ruleCode }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ issue.issueStatus }}</el-descriptions-item>
      <el-descriptions-item label="级别">{{ issue.severity }}</el-descriptions-item>
      <el-descriptions-item label="责任角色">{{ issue.ownerRoleCode }}</el-descriptions-item>
      <el-descriptions-item label="对象类型">{{ issue.objectType }}</el-descriptions-item>
      <el-descriptions-item label="对象标识">{{ issue.objectIdentity }}</el-descriptions-item>
      <el-descriptions-item label="标题">{{ issue.title }}</el-descriptions-item>
      <el-descriptions-item label="最近发现">{{ issue.lastDetectedAt }}</el-descriptions-item>
      <el-descriptions-item label="描述" :span="2">{{ issue.description }}</el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { getDataQualityIssue, type DataQualityIssue } from '../../api/i2';

const route = useRoute();
const issueId = computed(() => String(route.params.issueId));
const issue = ref<DataQualityIssue | null>(null);

async function load() {
  issue.value = await getDataQualityIssue(issueId.value);
}

onMounted(load);

defineExpose({ issue, load });
</script>
