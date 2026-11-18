<script setup lang="ts">
import { NAlert, NButton, NFlex, NText } from "naive-ui";
import { CATEGORY_STATUS, type CategoryStatus } from "../types/category.types";

defineProps<{
  count: number;
  batchPending: boolean;
}>();

const emits = defineEmits<{
  batchStatus: [status: CategoryStatus];
  batchDelete: [];
  clear: [];
}>();
</script>

<template>
  <NAlert v-if="count > 0" type="info" :show-icon="false" class="shrink-0">
    <NFlex align="center" justify="space-between" :wrap="true" :size="8">
      <NText>已选择 {{ count }} 项</NText>
      <NFlex align="center" :size="8">
        <NButton
          size="small"
          type="success"
          secondary
          :disabled="batchPending"
          @click="emits('batchStatus', CATEGORY_STATUS.ACTIVE)"
        >
          批量启用
        </NButton>
        <NButton
          size="small"
          type="warning"
          secondary
          :disabled="batchPending"
          @click="emits('batchStatus', CATEGORY_STATUS.DISABLED)"
        >
          批量停用
        </NButton>
        <NButton
          size="small"
          type="error"
          secondary
          :disabled="batchPending"
          @click="emits('batchDelete')"
        >
          批量删除
        </NButton>
        <NButton size="small" quaternary @click="emits('clear')">
          取消选择
        </NButton>
      </NFlex>
    </NFlex>
  </NAlert>
</template>
