<script setup lang="ts">
import type { UseQueryReturnType } from "@tanstack/vue-query";
import { NButton, NEmpty, NResult, NSpin } from "naive-ui";

import { getErrorMessage } from "@/shared/api/errors";

const props = defineProps<{
  query: Pick<
    UseQueryReturnType<unknown, unknown>,
    "data" | "error" | "isFetching" | "isLoading" | "isLoadingError" | "refetch"
  >;
  empty?: boolean;
}>();

async function retry() {
  await props.query.refetch();
}
</script>

<template>
  <NSpin v-if="query.isLoading.value" aria-label="加载中" />
  <NResult
    v-else-if="query.isLoadingError.value"
    status="error"
    title="加载失败"
    :description="getErrorMessage(query.error.value, '请稍后重试')"
  >
    <template #footer>
      <NButton :loading="query.isFetching.value" @click="retry">重试</NButton>
    </template>
  </NResult>
  <NEmpty
    v-else-if="empty && query.data.value !== undefined"
    description="暂无数据"
  />
  <slot v-else />
</template>
