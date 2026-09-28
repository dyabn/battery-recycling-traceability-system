<template>
  <section class="page-panel">
    <header class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>
    <el-table :data="users" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="姓名" />
      <el-table-column prop="enterpriseId" label="企业" width="100" />
      <el-table-column prop="enabledStatus" label="状态" width="120" />
      <el-table-column label="角色">
        <template #default="{ row }">{{ row.roles.join(', ') }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { http } from '../../api/http';
import type { CurrentUser } from '../../stores/auth';

const users = ref<CurrentUser[]>([]);

async function load() {
  const response = await http.get<CurrentUser[]>('/users');
  users.value = response.data;
}

onMounted(load);
</script>

