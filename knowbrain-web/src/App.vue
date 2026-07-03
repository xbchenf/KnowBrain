<template>
  <div class="app-container">
    <!-- 顶部导航 -->
    <el-header class="app-header">
      <div class="header-left">
        <el-icon :size="24"><Reading /></el-icon>
        <span class="app-title">KnowBrain</span>
        <el-tag size="small" type="info">企业知识大脑</el-tag>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="uploadVisible = true">
          <el-icon><Upload /></el-icon>
          上传文档
        </el-button>
      </div>
    </el-header>

    <!-- 主内容区 -->
    <el-main class="app-main">
      <ChatView ref="chatViewRef" />
    </el-main>

    <!-- 文档上传弹窗 -->
    <UploadDialog v-model:visible="uploadVisible" @uploaded="onUploaded" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Reading, Upload } from '@element-plus/icons-vue'
import ChatView from './views/ChatView.vue'
import UploadDialog from './components/UploadDialog.vue'

const uploadVisible = ref(false)
const chatViewRef = ref()

function onUploaded(doc: any) {
  uploadVisible.value = false
  // 上传成功后添加系统提示
  if (chatViewRef.value) {
    chatViewRef.value.addSystemMessage(`文档「${doc.data.title || doc.data.fileName}」已上传并解析完成，可以开始提问了`)
  }
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: #f5f7fa;
}

.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
  height: 56px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-title {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-main {
  flex: 1;
  overflow: hidden;
  padding: 0;
}
</style>
