<template>
  <main class="simple-page">
    <el-result icon="info" title="正在进入系统" sub-title="正在根据当前用户权限跳转" />
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

import { defaultHomePath } from '../router/defaultHome';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const authStore = useAuthStore();

onMounted(async () => {
  if (!authStore.currentUser) {
    await authStore.loadCurrentUser();
  }

  await router.replace(defaultHomePath(authStore.currentUser?.permissions || []));
});
</script>
