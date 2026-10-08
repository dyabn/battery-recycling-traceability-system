<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>电池登记</h2>
      <el-button @click="$router.push('/batches')">批次列表</el-button>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="132px" class="narrow-form">
      <el-form-item label="原始编码">
        <el-input v-model="form.originalCode" clearable maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="电池类型" prop="batteryType">
        <el-select v-model="form.batteryType">
          <el-option label="电池包" value="PACK" />
        </el-select>
      </el-form-item>
      <el-form-item label="电池体系" prop="batteryChemistry">
        <el-select v-model="form.batteryChemistry" allow-create filterable>
          <el-option label="未知" value="UNKNOWN" />
          <el-option label="磷酸铁锂" value="LFP" />
          <el-option label="三元锂" value="NCM" />
        </el-select>
      </el-form-item>
      <el-form-item label="电池型号">
        <el-input v-model="form.batteryModel" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="生产企业">
        <el-input v-model="form.manufacturer" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="标称容量">
        <el-input-number v-model="form.nominalCapacity" :min="0" :precision="2" />
      </el-form-item>
      <el-form-item label="生产日期">
        <el-date-picker v-model="form.productionDate" value-format="YYYY-MM-DD" type="date" />
      </el-form-item>
      <el-form-item>
        <el-button @click="duplicateCheck" :disabled="!form.originalCode">检查重复</el-button>
        <el-button type="primary" :loading="saving" @click="save">登记电池</el-button>
      </el-form-item>
    </el-form>

    <el-alert v-if="duplicateMessage" :title="duplicateMessage" :type="duplicateType" show-icon class="result-alert" />

    <el-result v-if="createdBattery" icon="success" title="电池登记成功" :sub-title="createdBattery.systemTraceCode">
      <template #extra>
        <el-button type="primary" @click="$router.push(`/batteries/${createdBattery?.id}/trace`)">查看追溯</el-button>
      </template>
    </el-result>

    <el-result v-if="candidateId" icon="warning" title="需要人工核实" :sub-title="`候选ID：${candidateId}`">
      <template #extra>
        <el-button type="primary" @click="$router.push(`/duplicates/${candidateId}?matches=${matchedBatteryIds.join(',')}`)">处理重复编码</el-button>
      </template>
    </el-result>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { reactive, ref } from 'vue';

import { checkDuplicate, createBattery, type Battery, type BatteryPayload } from '../../api/i2';

const formRef = ref<FormInstance>();
const saving = ref(false);
const createdBattery = ref<Battery | null>(null);
const candidateId = ref<string | null>(null);
const matchedBatteryIds = ref<string[]>([]);
const duplicateMessage = ref('');
const duplicateType = ref<'success' | 'warning'>('success');
const form = reactive<BatteryPayload>({
  originalCode: '',
  batteryType: 'PACK',
  batteryChemistry: 'UNKNOWN',
  batteryModel: '',
  manufacturer: '',
  nominalCapacity: null,
  productionDate: '',
});

const rules: FormRules = {
  batteryType: [{ required: true, message: '请选择电池类型', trigger: 'change' }],
  batteryChemistry: [{ required: true, message: '请选择电池体系', trigger: 'change' }],
};

async function duplicateCheck() {
  const result = await checkDuplicate(form.originalCode || '');
  duplicateType.value = result.duplicated ? 'warning' : 'success';
  duplicateMessage.value = result.duplicated ? `发现疑似重复，匹配电池ID：${result.matchedBatteryIds.join(', ')}` : '未发现当前企业内重复原始编码';
}

async function save() {
  await formRef.value?.validate();
  saving.value = true;
  createdBattery.value = null;
  candidateId.value = null;
  try {
    const result = await createBattery(form);
    if (result.resultType === 'BATTERY_CREATED' && result.battery) {
      createdBattery.value = result.battery;
      ElMessage.success('电池已登记');
    } else {
      candidateId.value = result.candidateId || null;
      matchedBatteryIds.value = result.matchedBatteryIds || [];
      ElMessage.warning('原始编码疑似重复，请进行人工核实');
    }
  } finally {
    saving.value = false;
  }
}
</script>
