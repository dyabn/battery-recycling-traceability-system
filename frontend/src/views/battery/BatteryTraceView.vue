<template>
  <section class="page-panel" v-loading="loading">
    <div class="page-header">
      <h2>生命周期追溯</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-timeline>
      <el-timeline-item v-for="event in events" :key="event.occurredAt + event.eventName" :timestamp="event.occurredAt">
        <strong>{{ event.eventName }}</strong>
        <p>{{ event.objectCode }}｜{{ event.statusChange }}｜{{ event.result }}</p>
        <p class="muted">操作人：{{ event.operator }}</p>
        <div v-if="event.details?.acceptance" class="trace-detail">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="身份核对">{{ event.details.acceptance.identityCheckResult }}</el-descriptions-item>
            <el-descriptions-item label="外观情况">{{ event.details.acceptance.appearanceCheckResult }}</el-descriptions-item>
            <el-descriptions-item label="资料完整">{{ event.details.acceptance.documentCheckResult }}</el-descriptions-item>
            <el-descriptions-item label="验收说明">{{ event.details.acceptance.acceptanceNote || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div v-if="event.details?.supplement" class="trace-detail">
          <p>补充说明：{{ event.details.supplement.supplementNote || '-' }}</p>
          <div v-if="event.details.supplement.attachments.length" class="attachment-list">
            <el-button
              v-for="attachment in event.details.supplement.attachments"
              :key="attachment.id"
              link
              type="primary"
              @click="download(attachment.id, attachment.fileName)"
            >
              {{ attachment.fileName }}
            </el-button>
          </div>
        </div>
        <div v-if="event.details?.inbound" class="trace-detail">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="入库单号">{{ event.details.inbound.inboundNo }}</el-descriptions-item>
            <el-descriptions-item label="入库时间">{{ event.details.inbound.inboundAt }}</el-descriptions-item>
            <el-descriptions-item label="仓库">{{ event.details.inbound.warehouseCode }} {{ event.details.inbound.warehouseName }}</el-descriptions-item>
            <el-descriptions-item label="库位">{{ event.details.inbound.locationCode }}</el-descriptions-item>
            <el-descriptions-item label="库存记录">{{ event.details.inbound.inventoryId }}</el-descriptions-item>
            <el-descriptions-item label="入库人">{{ event.details.inbound.inboundBy }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-timeline-item>
    </el-timeline>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { downloadAttachment, getBatteryTrace, type TraceEvent } from '../../api/i2';

const route = useRoute();
const batteryId = computed(() => String(route.params.id));
const events = ref<TraceEvent[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    events.value = await getBatteryTrace(batteryId.value);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

async function download(attachmentId: string, fileName: string) {
  await downloadAttachment(attachmentId, fileName);
}
</script>

<style scoped>
.trace-detail {
  margin-top: 8px;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
