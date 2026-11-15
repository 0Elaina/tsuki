<script setup lang="ts">
import { ROUTE_NAMES } from "@/shared/constants/routes";
import { NButton, NResult } from "naive-ui";
import { useRouter } from "vue-router";

const router = useRouter();

function handleGoHome(): void {
  void router.replace({ name: ROUTE_NAMES.HOME });
}
</script>

<template>
  <div class="flex items-center justify-center py-16">
    <NResult
      status="404"
      title="404 页面不存在"
      description="你访问的页面可能已移动、删除，或链接地址有误"
    >
      <template #footer>
        <NButton type="primary" @click="handleGoHome">返回工作台</NButton>
      </template>
    </NResult>
  </div>
</template>
