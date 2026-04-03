<script setup>
import ChatWorkspace from '../components/ChatWorkspace.vue';

// 旧版提示词存在乱码，已整体替换为 UTF-8 中文版本。
// #NEW CODE#
const quickPrompts = [
  '帮我规划一个 0 到 1 的 AI 智能体 MVP，先列出核心模块。',
  '围绕一个真实业务场景，设计 7 天内可以落地的智能体迭代方案。',
  '给我做一个 AI Agent 方案评审，指出技术风险、实现难点和优化方向。',
];
</script>

<template>
  <!-- 旧版模板存在乱码和 chatId 缺失问题，已整体替换为 UTF-8 正常版本。 -->
  <!-- #NEW CODE# -->
  <ChatWorkspace
    title="AI 超级智能体"
    subtitle="保持统一的聊天室体验，通过 Manus SSE 接口实时展示智能体的执行过程与最终结果。"
    badge="Manus"
    endpoint-path="/ai/manus/chat"
    placeholder="输入你的任务、问题，或继续追问上一轮上下文..."
    session-label="Chat ID"
    empty-title="开始一段支持上下文记忆的智能体对话"
    empty-description="你可以继续追问、补充限制条件，或者直接让智能体完成一项任务。当前页面会持续传递同一个 chatId，确保 follow-up 问题能够读取上一轮上下文。"
    :quick-prompts="quickPrompts"
    :requires-chat-id="true"
    theme="ocean"
  />
</template>
