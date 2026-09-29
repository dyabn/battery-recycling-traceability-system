<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>回收批次</h2>
      <el-button v-if="canCreate" type="primary" @click="openCreate">新建批次</el-button>
    </div>

    <el-form :inline="true" class="filter-row">
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部" style="width: 180px" @change="load">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待验收" value="PENDING_ACCEPTANCE" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table :data="batches" v-loading="loading" stripe>
      <el-table-column prop="batchNo" label="批次编号" min-width="180" />
      <el-table-column prop="sourceType" label="来源类型" width="120" />
      <el-table-column prop="sourceSubjectName" label="来源主体" min-width="180" />
      <el-table-column prop="handoverDate" label="交接日期" width="130" />
      <el-table-column prop="batchStatus" label="状态" width="150" />
      <el-table-column label="电池数" width="90">
        <template #default="{ row }">{{ row.batteries.length }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/batches/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingBatch ? '编辑草稿批次' : '新建回收批次'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-form-item label="来源类型" prop="sourceType">
          <el-select v-model="form.sourceType" placeholder="请选择">
            <el-option label="个人" value="PERSON" />
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源主体" prop="sourceSubjectName">
          <el-input v-model="form.sourceSubjectName" />
        </el-form-item>
        <el-form-item label="交接日期" prop="handoverDate">
          <el-date-picker v-model="form.handoverDate" value-format="YYYY-MM-DD" type="date" />
        </el-form-item>
        <el-form-item label="交接地点">
          <el-input v-model="form.handoverLocation" />
        </el-form-item>
        <el-form-item label="关联单据号">
          <el-input v-model="form.relatedDocumentNo" />
        </el-form-item>
        <el-form-item label="交接人员">
          <el-input v-model="form.handoverPerson" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';

import { createBatch, listBatches, updateBatch, type BatchPayload, type RecycleBatch } from '../../api/i2';
import { useAuthStore } from '../../stores/auth';

const authStore = useAuthStore();
const batches = ref<RecycleBatch[]>([]);
const loading = ref(false);
const saving = ref(false);
const status = ref('');
const dialogVisible = ref(false);
const editingBatch = ref<RecycleBatch | null>(null);
const formRef = ref<FormInstance>();
const form = reactive<BatchPayload>({
  sourceType: '',
  sourceSubjectName: '',
  handoverDate: '',
  handoverLocation: '',
  relatedDocumentNo: '',
  handoverPerson: '',
  remark: '',
});

const rules: FormRules = {
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  sourceSubjectName: [{ required: true, message: '请输入来源主体名称', trigger: 'blur' }],
  handoverDate: [{ required: true, message: '请选择交接日期', trigger: 'change' }],
};

const canCreate = authStore.hasPermission('batch:create');

function resetForm(batch?: RecycleBatch) {
  editingBatch.value = batch || null;
  Object.assign(form, {
    sourceType: batch?.sourceType || '',
    sourceSubjectName: batch?.sourceSubjectName || '',
    handoverDate: batch?.handoverDate || '',
    handoverLocation: batch?.handoverLocation || '',
    relatedDocumentNo: batch?.relatedDocumentNo || '',
    handoverPerson: batch?.handoverPerson || '',
    remark: batch?.remark || '',
  });
}

function openCreate() {
  resetForm();
  dialogVisible.value = true;
}

async function load() {
  loading.value = true;
  try {
    batches.value = await listBatches(status.value);
  } finally {
    loading.value = false;
  }
}

async function save() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    if (editingBatch.value) {
      await updateBatch(editingBatch.value.id, form);
    } else {
      await createBatch(form);
    }
    ElMessage.success('批次已保存');
    dialogVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
