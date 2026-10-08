<template>
  <section class="page-panel">
    <header class="page-header">
      <h2>权限清单</h2>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>
    <el-table :data="permissions" border>
      <el-table-column prop="permissionCode" label="权限编码" width="240" />
      <el-table-column prop="permissionName" label="权限名称" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { http } from '../../api/http';

interface Permission {
  id: string;
  permissionCode: string;
  permissionName: string;
}

interface ApiEnvelope<T> {
  data: T;
}

const permissions = ref<Permission[]>([]);

async function load() {
  const response = await http.get<ApiEnvelope<Permission[]>>('/permissions');
  permissions.value = response.data.data;
}

onMounted(load);
</script>
