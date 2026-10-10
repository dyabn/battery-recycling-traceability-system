<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>当前库存</h2>
      <div class="toolbar">
        <el-input v-model="systemTraceCode" placeholder="系统追溯编码" clearable @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="systemTraceCode" label="系统追溯编码" min-width="190" />
      <el-table-column prop="currentResponsibleEnterpriseName" label="当前责任企业" min-width="160" />
      <el-table-column prop="lifecycleStatus" label="电池状态" width="130" />
      <el-table-column prop="warehouseName" label="仓库" min-width="160" />
      <el-table-column prop="warehouseCode" label="仓库编码" width="120" />
      <el-table-column prop="locationCode" label="库位" min-width="190" />
      <el-table-column prop="inboundAt" label="入库时间" min-width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/batteries/${row.batteryId}/trace`)">追溯</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { listInventory, type InventoryItem } from '../../api/i2';

const items = ref<InventoryItem[]>([]);
const systemTraceCode = ref('');
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    items.value = await listInventory(systemTraceCode.value.trim());
  } finally {
    loading.value = false;
  }
}

onMounted(load);

defineExpose({
  systemTraceCode,
  load,
});
</script>
