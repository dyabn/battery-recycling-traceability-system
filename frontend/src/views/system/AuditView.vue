<template>
  <section class="page-panel">
    <header class="page-header">
      <h2>审计日志</h2>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>
    <el-table :data="logs" border>
      <el-table-column prop="actionCode" label="动作" width="240" />
      <el-table-column prop="objectType" label="对象" width="140" />
      <el-table-column prop="objectId" label="对象ID" width="100" />
      <el-table-column prop="result" label="结果" width="120" />
      <el-table-column prop="rejectReason" label="原因/变更" />
      <el-table-column prop="operatedAt" label="时间" width="200" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { http } from '../../api/http';

interface AuditLog {
  id: string;
  actionCode: string;
  objectType: string;
  objectId: string;
  result: string;
  rejectReason: string;
  operatedAt: string;
}

interface ApiEnvelope<T> {
  data: T;
}

const logs = ref<AuditLog[]>([]);

async function load() {
  const response = await http.get<ApiEnvelope<AuditLog[]>>('/audit-logs');
  logs.value = response.data.data;
}

onMounted(load);
</script>
