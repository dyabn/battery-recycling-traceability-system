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
      </el-timeline-item>
    </el-timeline>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { getBatteryTrace, type TraceEvent } from '../../api/i2';

const route = useRoute();
const batteryId = computed(() => Number(route.params.id));
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
</script>
