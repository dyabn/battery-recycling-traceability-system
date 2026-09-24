<template>
  <main class="app-shell">
    <section class="workspace">
      <div>
        <p class="eyebrow">Implementation V0.1</p>
        <h1>面向动力电池回收利用企业的流转协同与追溯管理系统</h1>
        <p class="summary">当前仅初始化正式工程骨架，业务功能将在后续提交按已确认设计逐步实现。</p>
      </div>

      <el-card class="status-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>后端连接</span>
            <el-button type="primary" size="small" @click="checkHealth">检查</el-button>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="API">
            {{ apiBaseUrl }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="healthTagType">{{ healthStatus }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import axios from 'axios';
import { computed } from 'vue';

import { useAppStore } from './stores/app';

const appStore = useAppStore();
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1';

const healthStatus = computed(() => appStore.backendStatus);
const healthTagType = computed(() => (appStore.backendStatus === 'UP' ? 'success' : 'info'));

async function checkHealth() {
  appStore.setBackendStatus('CHECKING');
  try {
    const response = await axios.get(`${apiBaseUrl}/health`);
    appStore.setBackendStatus(response.data.status || 'UP');
  } catch {
    appStore.setBackendStatus('UNAVAILABLE');
  }
}
</script>
