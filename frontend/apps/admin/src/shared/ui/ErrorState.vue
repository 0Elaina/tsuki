<script setup lang="ts">
import { NButton, NFlex, NResult } from "naive-ui";

withDefaults(
  defineProps<{
    title?: string;
    description?: string;
    retrying?: boolean;
    retryText?: string;
    showRetry?: boolean;
  }>(),
  {
    title: "加载失败",
    description: "请稍候重试",
    retrying: false,
    retryText: "重试",
    showRetry: true,
  },
);

const emits = defineEmits<{
  retry: [];
}>();
</script>

<template>
  <NFlex justify="center" align="center" class="min-h-40 py-8">
    <NResult status="error" :title="title" :description="description">
      <template v-if="showRetry" #footer>
        <NButton :loading="retrying" @click="emits('retry')">{{
          retryText
        }}</NButton>
      </template>
    </NResult>
  </NFlex>
</template>
