<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>质量检查</h2>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="108px" class="compact-form">
      <el-form-item label="检查范围" prop="runScope">
        <el-select v-model="form.runScope" placeholder="请选择范围" style="width: 240px">
          <el-option label="全部" value="ALL" />
          <el-option label="电池档案" value="BATTERY" />
          <el-option label="入库记录" value="INBOUND" />
          <el-option label="当前库存" value="INVENTORY" />
          <el-option label="追溯事件" value="TRACE" />
        </el-select>
      </el-form-item>
      <el-form-item label="指定规则">
        <el-select v-model="form.requestedRuleCodes" multiple clearable collapse-tags placeholder="默认执行范围内全部启用规则" style="width: 420px">
          <el-option
            v-for="rule in scopeRules"
            :key="rule.ruleCode"
            :label="`${rule.ruleCode} ${rule.ruleName}`"
            :value="rule.ruleCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="submit">发起检查</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="recentRuns" stripe>
      <el-table-column prop="runNo" label="检查编号" min-width="190" />
      <el-table-column prop="runScope" label="范围" width="110" />
      <el-table-column prop="runStatus" label="状态" width="120" />
      <el-table-column prop="checkedRuleCount" label="规则数" width="90" />
      <el-table-column prop="checkedObjectCount" label="对象数" width="90" />
      <el-table-column prop="issueCount" label="问题数" width="90" />
      <el-table-column prop="startedAt" label="开始时间" min-width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/data-quality/check-runs/${row.id}`)">结果</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  createDataQualityCheckRun,
  listDataQualityRules,
  type DataQualityCheckRun,
  type DataQualityRule,
} from '../../api/i2';

const router = useRouter();
const formRef = ref<FormInstance>();
const allRules = ref<DataQualityRule[]>([]);
const recentRuns = ref<DataQualityCheckRun[]>([]);
const submitting = ref(false);
const form = reactive({
  runScope: 'ALL',
  requestedRuleCodes: [] as string[],
});

const scopeMap: Record<string, string[]> = {
  ALL: ['DQ-001', 'DQ-002', 'DQ-003', 'DQ-004', 'DQ-005', 'DQ-006', 'DQ-007'],
  BATTERY: ['DQ-001', 'DQ-002', 'DQ-003'],
  INBOUND: ['DQ-004', 'DQ-005'],
  INVENTORY: ['DQ-007'],
  TRACE: ['DQ-006'],
};

const rules: FormRules = {
  runScope: [{ required: true, message: '请选择检查范围', trigger: 'change' }],
};

const scopeRules = computed(() => allRules.value.filter((rule) => scopeMap[form.runScope]?.includes(rule.ruleCode)));

watch(() => form.runScope, () => {
  form.requestedRuleCodes = [];
});

async function loadRules() {
  allRules.value = await listDataQualityRules();
}

async function submit() {
  await formRef.value?.validate();
  submitting.value = true;
  try {
    const run = await createDataQualityCheckRun({
      runScope: form.runScope,
      requestedRuleCodes: form.requestedRuleCodes.length ? form.requestedRuleCodes : undefined,
    });
    recentRuns.value = [run, ...recentRuns.value.filter((item) => item.id !== run.id)];
    ElMessage.success('检查已完成');
    await router.push(`/data-quality/check-runs/${run.id}`);
  } finally {
    submitting.value = false;
  }
}

onMounted(loadRules);

defineExpose({ form, recentRuns, submit, loadRules });
</script>
