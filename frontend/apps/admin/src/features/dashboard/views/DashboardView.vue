<script setup lang="ts">
import { useLogoutMutation } from "@/features/auth/api/auth.mutations";
import { useMeQuery } from "@/features/auth/api/auth.queries";
import { ROUTE_NAMES } from "@/shared/constants/routes";
import ErrorState from "@/shared/ui/ErrorState.vue";
import LoadingState from "@/shared/ui/LoadingState.vue";
import { getErrorMessage } from "@tsuki/api-client";
import {
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NTag,
  NText,
} from "naive-ui";
import { useRouter } from "vue-router";

const router = useRouter();
const meQuery = useMeQuery();
const logoutMutation = useLogoutMutation();

async function handleRetry(): Promise<void> {
  await meQuery.refetch();
}

function handleLogout(): void {
  logoutMutation.mutate(undefined, {
    onSettled: () => {
      void router.replace({
        name: ROUTE_NAMES.LOGIN,
      });
    },
  });
}
</script>

<template>
  <main class="mx-auto max-w-2xl px-4 py-10">
    <NCard title="Tsuki 管理端">
      <template #header-extra>
        <NButton
          secondary
          :loading="logoutMutation.isPending.value"
          @click="handleLogout"
          >退出登录</NButton
        >
      </template>

      <!-- 内容区 -->
      <LoadingState
        v-if="meQuery.isLoading.value"
        label="正在获取管理员信息..."
      />
      <ErrorState
        v-else-if="meQuery.isLoadingError.value"
        :description="
          getErrorMessage(meQuery.error.value, '获取管理员信息失败, 请稍候重试')
        "
        :retrying="meQuery.isFetching.value"
        @retry="handleRetry"
      />
      <NDescriptions
        v-else-if="meQuery.data.value"
        bordered
        :column="1"
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
          <NTag size="small" type="info">
            {{ meQuery.data.value.role }}
          </NTag>
        </NDescriptionsItem>
      </NDescriptions>
    </NCard>
  </main>
</template>
