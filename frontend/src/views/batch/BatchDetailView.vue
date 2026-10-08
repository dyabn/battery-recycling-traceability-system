<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <h2>{{ batch?.batchNo || '批次详情' }}</h2>
      <div class="actions">
        <el-button @click="$router.push('/batches')">返回</el-button>
        <el-button v-if="canAdd" type="primary" @click="addDialog = true">加入电池</el-button>
        <el-button v-if="canSubmit" type="success" :loading="submitting" @click="submit">提交待验收</el-button>
      </div>
    </div>

    <el-descriptions v-if="batch" :column="3" border>
      <el-descriptions-item label="状态">{{ batch.batchStatus }}</el-descriptions-item>
      <el-descriptions-item label="来源类型">{{ batch.sourceType }}</el-descriptions-item>
      <el-descriptions-item label="来源主体">{{ batch.sourceSubjectName }}</el-descriptions-item>
      <el-descriptions-item label="交接日期">{{ batch.handoverDate }}</el-descriptions-item>
      <el-descriptions-item label="交接地点">{{ batch.handoverLocation || '-' }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ batch.version }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="3">{{ batch.remark || '-' }}</el-descriptions-item>
    </el-descriptions>

    <h3>批次电池</h3>
    <div v-if="batch" class="status-summary">
      <el-tag>待验收 {{ progress.pendingAcceptance }}</el-tag>
      <el-tag type="warning">待补充 {{ progress.pendingSupplement }}</el-tag>
      <el-tag type="success">通过待入库 {{ progress.accepted }}</el-tag>
      <el-tag type="danger">不通过 {{ progress.rejected }}</el-tag>
    </div>
    <el-table :data="batch?.batteries || []" stripe>
      <el-table-column prop="systemTraceCode" label="系统追溯编码" min-width="190" />
      <el-table-column prop="originalCode" label="原始编码" min-width="150" />
      <el-table-column prop="batteryChemistry" label="电池体系" width="120" />
      <el-table-column prop="lifecycleStatus" label="状态" width="150" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/batteries/${row.id}/trace`)">追溯</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addDialog" title="加入电池" width="420px">
      <el-form label-width="100px">
        <el-form-item label="电池ID">
          <el-input v-model="batteryId" placeholder="请输入电池ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="addBattery">确认加入</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { addBatteryToBatch, getBatch, submitBatch, type RecycleBatch } from '../../api/i2';
import { useAuthStore } from '../../stores/auth';

const route = useRoute();
const authStore = useAuthStore();
const batch = ref<RecycleBatch | null>(null);
const loading = ref(false);
const submitting = ref(false);
const adding = ref(false);
const addDialog = ref(false);
const batteryId = ref('');
const batchId = computed(() => String(route.params.id));
const canAdd = computed(() => authStore.hasPermission('battery:create') && batch.value?.batchStatus === 'DRAFT');
const canSubmit = computed(() => authStore.hasPermission('batch:submit') && batch.value?.batchStatus === 'DRAFT');
const progress = computed(() => {
  const batteries = batch.value?.batteries || [];
  return {
    pendingAcceptance: batteries.filter((battery) => battery.lifecycleStatus === 'PENDING_ACCEPTANCE').length,
    pendingSupplement: batteries.filter((battery) => battery.lifecycleStatus === 'PENDING_SUPPLEMENT').length,
    accepted: batteries.filter((battery) => battery.lifecycleStatus === 'ACCEPTED_PENDING_INBOUND').length,
    rejected: batteries.filter((battery) => battery.lifecycleStatus === 'ACCEPTANCE_REJECTED').length,
  };
});

async function load() {
  loading.value = true;
  try {
    batch.value = await getBatch(batchId.value);
  } finally {
    loading.value = false;
  }
}

async function addBattery() {
  adding.value = true;
  try {
    batch.value = await addBatteryToBatch(batchId.value, batteryId.value.trim());
    ElMessage.success('电池已加入批次');
    addDialog.value = false;
  } finally {
    adding.value = false;
  }
}

async function submit() {
  submitting.value = true;
  try {
    batch.value = await submitBatch(batchId.value);
    ElMessage.success('批次已提交待验收');
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.status-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 12px;
}
</style>
