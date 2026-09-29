<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>重复编码核实</h2>
      <el-button @click="$router.push('/batteries/register')">返回登记</el-button>
    </div>

    <el-alert title="候选只允许处理一次；确认不同电池时必须填写原因。" type="info" show-icon />
    <el-form label-width="132px" class="narrow-form">
      <el-form-item label="候选ID">{{ candidateId }}</el-form-item>
      <el-form-item label="匹配电池ID">
        <el-select v-model="existingBatteryId" placeholder="请选择">
          <el-option v-for="id in matchedIds" :key="id" :label="id" :value="id" />
        </el-select>
      </el-form-item>
      <el-form-item label="处理结论">
        <el-radio-group v-model="reviewResult">
          <el-radio-button label="SAME_BATTERY">同一电池</el-radio-button>
          <el-radio-button label="DIFFERENT_BATTERY">不同电池</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="reviewResult === 'DIFFERENT_BATTERY'" label="重复原因">
        <el-input v-model="duplicateReason" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">确认核实</el-button>
      </el-form-item>
    </el-form>

    <el-result v-if="battery" icon="success" title="核实完成" :sub-title="battery.systemTraceCode">
      <template #extra>
        <el-button type="primary" @click="$router.push(`/batteries/${battery?.id}/trace`)">查看追溯</el-button>
      </template>
    </el-result>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';

import { resolveDuplicate, type Battery } from '../../api/i2';

const route = useRoute();
const candidateId = computed(() => Number(route.params.id));
const matchedIds = computed(() => String(route.query.matches || '').split(',').filter(Boolean).map(Number));
const existingBatteryId = ref<number | undefined>(matchedIds.value[0]);
const reviewResult = ref('SAME_BATTERY');
const duplicateReason = ref('');
const saving = ref(false);
const battery = ref<Battery | null>(null);

async function save() {
  if (reviewResult.value === 'SAME_BATTERY' && !existingBatteryId.value) {
    ElMessage.error('请选择已有电池');
    return;
  }
  if (reviewResult.value === 'DIFFERENT_BATTERY' && !duplicateReason.value.trim()) {
    ElMessage.error('请填写重复原因');
    return;
  }
  saving.value = true;
  try {
    battery.value = await resolveDuplicate(candidateId.value, {
      reviewResult: reviewResult.value,
      existingBatteryId: existingBatteryId.value,
      duplicateReason: duplicateReason.value,
    });
    ElMessage.success('重复编码核实完成');
  } finally {
    saving.value = false;
  }
}
</script>
