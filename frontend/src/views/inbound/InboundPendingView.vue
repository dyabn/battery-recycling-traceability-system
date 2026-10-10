<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>待入库电池</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="batteries" v-loading="loading" stripe>
      <el-table-column prop="systemTraceCode" label="系统追溯编码" min-width="190" />
      <el-table-column prop="originalCode" label="原始编码" min-width="150" />
      <el-table-column prop="batteryChemistry" label="电池体系" width="120" />
      <el-table-column prop="lifecycleStatus" label="状态" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openInbound(row)">办理入库</el-button>
          <el-button link type="primary" @click="$router.push(`/batteries/${row.id}/trace`)">追溯</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="inboundDialog" title="办理入库" width="560px">
      <el-form ref="inboundFormRef" :model="inboundForm" :rules="rules" label-width="88px">
        <el-form-item label="电池">
          <span>{{ selectedBattery?.systemTraceCode }}</span>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="inboundForm.warehouseId" placeholder="请选择仓库" style="width: 100%" @change="onWarehouseChange">
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="`${warehouse.warehouseCode} ${warehouse.warehouseName}`"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库位" prop="locationId">
          <el-select v-model="inboundForm.locationId" placeholder="请选择库位" style="width: 100%" :disabled="!inboundForm.warehouseId">
            <el-option
              v-for="location in locations"
              :key="location.id"
              :label="location.locationCode"
              :value="location.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inboundDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveInbound">确认入库</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';

import {
  createInbound,
  listPendingInbounds,
  listWarehouseLocations,
  listWarehouses,
  type Battery,
  type Warehouse,
  type WarehouseLocation,
} from '../../api/i2';

const batteries = ref<Battery[]>([]);
const warehouses = ref<Warehouse[]>([]);
const locations = ref<WarehouseLocation[]>([]);
const selectedBattery = ref<Battery | null>(null);
const loading = ref(false);
const saving = ref(false);
const inboundDialog = ref(false);
const inboundFormRef = ref<FormInstance>();

const inboundForm = reactive({
  warehouseId: '',
  locationId: '',
});

const rules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  locationId: [{ required: true, message: '请选择库位', trigger: 'change' }],
};

async function load() {
  loading.value = true;
  try {
    const [pending, enabledWarehouses] = await Promise.all([
      listPendingInbounds(),
      listWarehouses(),
    ]);
    batteries.value = pending;
    warehouses.value = enabledWarehouses;
  } finally {
    loading.value = false;
  }
}

async function openInbound(battery: Battery) {
  selectedBattery.value = battery;
  inboundForm.warehouseId = '';
  inboundForm.locationId = '';
  locations.value = [];
  inboundDialog.value = true;
}

async function onWarehouseChange() {
  const selectedWarehouseId = inboundForm.warehouseId;
  inboundForm.locationId = '';
  locations.value = [];
  if (!selectedWarehouseId) {
    return;
  }
  const nextLocations = await listWarehouseLocations(selectedWarehouseId);
  if (inboundForm.warehouseId === selectedWarehouseId) {
    locations.value = nextLocations;
  }
}

async function saveInbound() {
  await inboundFormRef.value?.validate();
  if (!selectedBattery.value) {
    return;
  }
  saving.value = true;
  try {
    await createInbound(selectedBattery.value.id, {
      warehouseId: inboundForm.warehouseId,
      locationId: inboundForm.locationId,
    });
    ElMessage.success('入库已完成');
    inboundDialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);

defineExpose({
  inboundForm,
  locations,
  openInbound,
  onWarehouseChange,
  saveInbound,
});
</script>
