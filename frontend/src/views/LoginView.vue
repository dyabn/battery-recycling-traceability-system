<template>
  <main class="login-page">
    <section class="login-panel">
      <div>
        <p class="eyebrow">I1 Authentication</p>
        <h1>动力电池回收追溯系统</h1>
        <p class="summary">使用正式 JWT 认证登录，企业和权限由后端统一裁决。</p>
      </div>
      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '../stores/auth';

const username = ref('admin');
const password = ref('password');
const loading = ref(false);
const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();

async function submit() {
  loading.value = true;
  try {
    await authStore.login(username.value, password.value);
    ElMessage.success('登录成功');
    await router.push((route.query.redirect as string) || '/');
  } finally {
    loading.value = false;
  }
}
</script>

