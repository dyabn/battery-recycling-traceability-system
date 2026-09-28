<template>
  <main class="simple-page">
    <el-result icon="warning" title="403" sub-title="没有访问该功能的权限">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </main>
</template>

