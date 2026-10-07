<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>待处理验收</h2>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="batteries" v-loading="loading" stripe>
      <el-table-column prop="systemTraceCode" label="系统追溯编码" min-width="190" />
      <el-table-column prop="originalCode" label="原始编码" min-width="150" />
      <el-table-column prop="batteryChemistry" label="电池体系" width="120" />
      <el-table-column prop="lifecycleStatus" label="状态" width="170" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.lifecycleStatus === 'PENDING_ACCEPTANCE'" link type="primary" @click="openAcceptance(row)">登记验收</el-button>
          <el-button v-if="row.lifecycleStatus === 'PENDING_SUPPLEMENT'" link type="primary" @click="openSupplement(row)">补充资料</el-button>
          <el-button link type="primary" @click="$router.push(`/batteries/${row.id}/trace`)">追溯</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="acceptanceDialog" title="登记验收" width="640px">
      <el-form ref="acceptanceFormRef" :model="acceptanceForm" :rules="acceptanceRules" label-width="124px">
        <el-form-item label="验收结论" prop="acceptanceResult">
          <el-segmented v-model="acceptanceForm.acceptanceResult" :options="resultOptions" />
        </el-form-item>
        <el-form-item label="身份核验" prop="identityCheckResult">
          <el-input v-model="acceptanceForm.identityCheckResult" maxlength="40" show-word-limit />
        </el-form-item>
        <el-form-item label="外观检查" prop="appearanceCheckResult">
          <el-input v-model="acceptanceForm.appearanceCheckResult" maxlength="40" show-word-limit />
        </el-form-item>
        <el-form-item label="资料检查" prop="documentCheckResult">
          <el-input v-model="acceptanceForm.documentCheckResult" maxlength="40" show-word-limit />
        </el-form-item>
        <el-form-item label="验收说明" prop="acceptanceNote">
          <el-input v-model="acceptanceForm.acceptanceNote" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptanceDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAcceptance">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="supplementDialog" title="补充验收资料" width="640px">
      <el-form ref="supplementFormRef" :model="supplementForm" :rules="supplementRules" label-width="112px">
        <el-form-item label="补充说明" prop="supplementNote">
          <el-input v-model="supplementForm.supplementNote" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="证据附件">
          <input type="file" @change="uploadSelectedFile" />
          <div v-if="uploadedAttachments.length" class="muted attachment-list">
            <span v-for="attachment in uploadedAttachments" :key="attachment.id">{{ attachment.fileName }}</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supplementDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSupplement">重新提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';

import {
  createAcceptance,
  listPendingAcceptances,
  supplementAcceptance,
  uploadAttachment,
  type AcceptancePayload,
  type Attachment,
  type Battery,
} from '../../api/i2';

const batteries = ref<Battery[]>([]);
const selectedBattery = ref<Battery | null>(null);
const loading = ref(false);
const saving = ref(false);
const acceptanceDialog = ref(false);
const supplementDialog = ref(false);
const acceptanceFormRef = ref<FormInstance>();
const supplementFormRef = ref<FormInstance>();
const uploadedAttachments = ref<Attachment[]>([]);

const resultOptions = [
  { label: '通过', value: 'PASS' },
  { label: '待补充', value: 'NEED_SUPPLEMENT' },
  { label: '不通过', value: 'REJECT' },
];

const acceptanceForm = reactive<AcceptancePayload>({
  acceptanceResult: 'PASS',
  identityCheckResult: '',
  appearanceCheckResult: '',
  documentCheckResult: '',
  acceptanceNote: '',
});

const supplementForm = reactive({
  supplementNote: '',
});

const noteRequired = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if ((acceptanceForm.acceptanceResult === 'NEED_SUPPLEMENT' || acceptanceForm.acceptanceResult === 'REJECT') && !value?.trim()) {
    callback(new Error('该结论必须填写说明'));
    return;
  }
  callback();
};

const supplementRequired = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value?.trim() && uploadedAttachments.value.length === 0) {
    callback(new Error('补充说明和附件至少提供一项'));
    return;
  }
  callback();
};

const acceptanceRules: FormRules = {
  acceptanceResult: [{ required: true, message: '请选择验收结论', trigger: 'change' }],
  identityCheckResult: [{ required: true, message: '请输入身份核验结果', trigger: 'blur' }],
  appearanceCheckResult: [{ required: true, message: '请输入外观检查结果', trigger: 'blur' }],
  documentCheckResult: [{ required: true, message: '请输入资料检查结果', trigger: 'blur' }],
  acceptanceNote: [{ validator: noteRequired, trigger: 'blur' }],
};

const supplementRules: FormRules = {
  supplementNote: [{ validator: supplementRequired, trigger: 'blur' }],
};

async function load() {
  loading.value = true;
  try {
    batteries.value = await listPendingAcceptances();
  } finally {
    loading.value = false;
  }
}

function openAcceptance(battery: Battery) {
  selectedBattery.value = battery;
  Object.assign(acceptanceForm, {
    acceptanceResult: 'PASS',
    identityCheckResult: '',
    appearanceCheckResult: '',
    documentCheckResult: '',
    acceptanceNote: '',
  });
  acceptanceDialog.value = true;
}

function openSupplement(battery: Battery) {
  selectedBattery.value = battery;
  supplementForm.supplementNote = '';
  uploadedAttachments.value = [];
  supplementDialog.value = true;
}

async function saveAcceptance() {
  await acceptanceFormRef.value?.validate();
  if (!selectedBattery.value) {
    return;
  }
  saving.value = true;
  try {
    await createAcceptance(selectedBattery.value.id, acceptanceForm);
    ElMessage.success('验收已保存');
    acceptanceDialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function saveSupplement() {
  await supplementFormRef.value?.validate();
  if (!selectedBattery.value) {
    return;
  }
  saving.value = true;
  try {
    await supplementAcceptance(selectedBattery.value.id, {
      supplementNote: supplementForm.supplementNote,
      attachmentIds: uploadedAttachments.value.map((attachment) => attachment.id),
    });
    ElMessage.success('资料已补充并重新提交');
    supplementDialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function uploadSelectedFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  const attachment = await uploadAttachment(file);
  uploadedAttachments.value.push(attachment);
  input.value = '';
}

onMounted(load);

defineExpose({
  acceptanceForm,
  supplementForm,
  uploadedAttachments,
  openAcceptance,
  openSupplement,
  saveAcceptance,
  saveSupplement,
  uploadSelectedFile,
});
</script>

<style scoped>
.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
</style>
