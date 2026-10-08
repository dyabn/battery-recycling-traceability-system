<template>
  <main class="simple-page">
    <el-result icon="warning" title="403" sub-title="没有访问该功能的权限">
      <template #extra>
        <el-button type="primary" @click="goHome">返回可访问首页</el-button>
        <el-button @click="logout">退出登录</el-button>
      </template>
    </el-result>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

import { defaultHomePath } from '../router/defaultHome';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const authStore = useAuthStore();

async function goHome() {
  await router.push(defaultHomePath(authStore.currentUser?.permissions || []));
}

async function logout() {
  authStore.clearSession();
  await router.push('/login');
}
</script>
