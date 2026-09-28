<template>
  <section class="page-panel">
    <header class="page-header">
      <h2>角色管理</h2>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>
    <el-table :data="roles" border>
      <el-table-column prop="roleCode" label="角色编码" width="180" />
      <el-table-column prop="roleName" label="角色名称" width="160" />
      <el-table-column label="权限">
        <template #default="{ row }">{{ row.permissions.join(', ') }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { http } from '../../api/http';

interface Role {
  id: number;
  roleCode: string;
  roleName: string;
  permissions: string[];
}

interface ApiEnvelope<T> {
  data: T;
}

const roles = ref<Role[]>([]);

async function load() {
  const response = await http.get<ApiEnvelope<Role[]>>('/roles');
  roles.value = response.data.data;
}

onMounted(load);
</script>
