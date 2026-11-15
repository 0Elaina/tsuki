<script setup lang="ts">
import { palette } from "@/app/theme/tokens";
import { useMeQuery } from "@/features/auth/api/auth.queries";
import ErrorState from "@/shared/ui/ErrorState.vue";
import LoadingState from "@/shared/ui/LoadingState.vue";
import { getErrorMessage } from "@tsuki/api-client";
import { Music } from "lucide-vue-next";
import {
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NFlex,
  NGi,
  NGrid,
  NH2,
  NIcon,
  NTag,
  NText,
} from "naive-ui";
import { computed, type Component } from "vue";

const meQuery = useMeQuery();

interface UpcomingModuleItem {
  key: string;
  title: string;
  description: string;
  tag: string;
  icon: Component;
}

const UPCOMING_MODULES: readonly UpcomingModuleItem[] = [
  {
    key: "test1",
    title: "test1",
    description: "测试站位1",
    tag: "temp",
    icon: Music,
  },
];

const greetingTitle = computed(() => {
  const me = meQuery.data.value;
  if (!me) return "欢迎使用 Tsuki 管理端";
  return `欢迎回来, ${me.nickname || me.username}`;
});
async function handleRetry(): Promise<void> {
  await meQuery.refetch();
}
</script>

<template>
  <NFlex vertical :size="16" class="mx-auto max-w-5xl">
    <!-- 欢迎卡片 -->
    <NCard>
      <NFlex vertical :size="4">
        <NH2 class="mb-0">{{ greetingTitle }}</NH2>
        <NText :depth="3">
          这里用于统筹管理音乐曲库、专辑编排与后台账号权限
        </NText>
      </NFlex>
    </NCard>

    <!-- 账号摘要卡片 -->
    <NCard title="当前登录账号">
      <LoadingState
        v-if="meQuery.isLoading.value"
        label="正在获取管理员信息..."
      />
      <ErrorState
        v-else-if="meQuery.isLoadingError.value"
        :description="
          getErrorMessage(meQuery.error.value, '获取管理员信息失败，请稍候重试')
        "
        :retrying="meQuery.isFetching.value"
        @retry="handleRetry"
      />
      <NDescriptions
        v-else-if="meQuery.data.value"
        bordered
        :column="2"
        label-placement="left"
      >
        <NDescriptionsItem label="账号 ID">
          <NText class="tabular">{{ meQuery.data.value.id }}</NText>
        </NDescriptionsItem>
        <NDescriptionsItem label="用户名">
          {{ meQuery.data.value.username }}
        </NDescriptionsItem>
        <NDescriptionsItem label="昵称">
          {{ meQuery.data.value.nickname }}
        </NDescriptionsItem>
        <NDescriptionsItem label="角色">
          <NTag size="small" :bordered="false" type="info">
            {{ meQuery.data.value.role }}
          </NTag>
        </NDescriptionsItem>
      </NDescriptions>
    </NCard>

    <!-- 模块概览卡片 -->
    <NCard title="功能模块概览">
      <NGrid cols="1 m:3" responsive="screen" :x-gap="16" :y-gap="16">
        <NGi v-for="item in UPCOMING_MODULES" :key="item.key">
          <NCard size="small" embedded class="h-full">
            <NFlex vertical :size="10">
              <!-- 卡片顶部 -->
              <NFlex align="center" justify="space-between">
                <NFlex align="center" :size="10">
                  <div
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
                    :style="{
                      backgroundColor: palette.moon,
                      color: palette.ink,
                    }"
                  >
                    <NIcon :size="18" :component="item.icon" />
                  </div>
                  <NText strong>
                    {{ item.title }}
                  </NText>
                </NFlex>
                <!-- 右侧标签 -->
                <NTag size="small" :bordered="false">
                  {{ item.tag }}
                </NTag>
              </NFlex>
              <!-- 底部说明 -->
              <NText :depth="3" class="text-sm">
                {{ item.description }}
              </NText>
            </NFlex>
          </NCard>
        </NGi>
      </NGrid>
    </NCard>
  </NFlex>
</template>
