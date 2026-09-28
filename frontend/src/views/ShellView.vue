<template>
  <el-container class="layout">
    <el-aside width="240px" class="sidebar">
      <div class="brand">
        <strong>电池追溯</strong>
        <span>I1 权限基础</span>
      </div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item v-if="can('permission:manage')" index="/system/users">用户管理</el-menu-item>
        <el-menu-item v-if="can('permission:manage')" index="/system/roles">角色管理</el-menu-item>
        <el-menu-item v-if="can('permission:manage')" index="/system/permissions">权限清单</el-menu-item>
        <el-menu-item v-if="can('audit:read')" index="/system/audit">审计日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div>
          <strong>{{ authStore.currentUser?.displayName }}</strong>
          <span>企业 {{ authStore.currentUser?.enterpriseId }}｜{{ authStore.roleNames }}</span>
        </div>
        <el-button @click="logout">退出</el-button>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const router = useRouter();

function can(permission: string) {
  return authStore.hasPermission(permission);
}

async function logout() {
  authStore.clearSession();
  await router.push('/login');
}
</script>

