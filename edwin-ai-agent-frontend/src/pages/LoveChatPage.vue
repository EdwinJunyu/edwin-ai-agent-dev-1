<script setup>
import ChatWorkspace from '../components/ChatWorkspace.vue';

const quickPrompts = [
  '我想让你帮我分析一段关系现状，并给出三条具体建议。',
  '请根据聊天上下文，帮我整理一段更真诚的表达方式。',
  '模拟一次情感沟通对话，分别给出我方和对方可能的回应。',
];

// The page link is renamed to Edwin App, but the backend API path is kept for compatibility.
const edwinAppEndpointPath = '/ai/edwin_app/chat/sse/emitter';
</script>

<template>
  <ChatWorkspace
    title="Edwin App"
    subtitle="前端页面名称和链接已经切换到 Edwin App，但请求仍然走兼容的后端 SSE 接口，保证现在的前后端联调不受影响。"
    badge="Edwin App"
    :endpoint-path="edwinAppEndpointPath"
    placeholder="输入你想咨询的问题，例如关系分析、沟通建议或回复措辞优化。"
    session-label="会话 ID"
    empty-title="从 Edwin App 开始一段新的对话"
    empty-description="你可以直接输入问题，或者点击下方预设提示词。页面地址已经更新，但后端接口保持兼容，所以当前链接是正确可用的。"
    :quick-prompts="quickPrompts"
    :requires-chat-id="true"
    theme="sunrise"
  />
</template>
