<script setup lang="ts">
import { useLogoutMutation } from "@/features/auth/api/auth.mutations";
import { ROUTE_NAMES } from "@/shared/constants/routes";
import { NButton, NCard, NFlex, NResult } from "naive-ui";
import { useRouter } from "vue-router";

const router = useRouter();
const logoutMutation = useLogoutMutation();

function handleGoHome(): void {
  void router.replace({
    name: ROUTE_NAMES.HOME,
  });
}

function handleSwitchAccount(): void {
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
  <div class="flex min-h-screen items-center justify-center bg-moon p-6">
    <NCard class="w-full max-w-lg">
      <NResult
        status="403"
        title="403 无访问权限"
        description="当前账号没有访问该页面的权限，请返回工作台或切换有权限的账号"
      >
        <template #footer>
          <NFlex justify="center" :size="12">
            <NButton type="primary" @click="handleGoHome">返回工作台</NButton>
            <NButton
              :loading="logoutMutation.isPending.value"
              @click="handleSwitchAccount"
            >
              切换账号
            </NButton>
          </NFlex>
        </template>
      </NResult>
    </NCard>
  </div>
</template>
