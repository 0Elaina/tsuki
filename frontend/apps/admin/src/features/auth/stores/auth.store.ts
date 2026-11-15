import { defineStore } from "pinia";
import { computed, shallowRef } from "vue";
import { adminAuthApi } from "../api/auth.api";
import { isSessionInvalidError } from "@tsuki/api-client";

export const useAuthStore = defineStore("admin-auth", () => {
  const accessToken = shallowRef<string | null>(null);
  const bootstrapped = shallowRef(false);
  const isAuthenticated = computed(() => accessToken.value !== null);
  // 并发调用 bootstrap 时复用同一个 Promise，避免重复刷新
  let bootstrapPromise: Promise<void> | null = null;

  // 直接写入令牌, 同时标记为已启动
  function setAccessToken(token: string | null): void {
    accessToken.value = token;
    bootstrapped.value = true;
  }

  // 清除本地会话状态
  function clearSession(): void {
    accessToken.value = null;
  }

  // 调用刷新接口换取新令牌
  async function refreshSession(): Promise<string | null> {
    try {
      const { accessToken: token } = await adminAuthApi.refresh();
      accessToken.value = token;
      return token;
    } catch (error) {
      if (isSessionInvalidError(error)) {
        accessToken.value = null;
        return null;
      }
      throw error;
    }
  }

  // 初始化会话：仅在首次调用时发起刷新，后续直接复用结果;
  // 即使刷新失败也标记 bootstrapped，避免重复请求
  function bootstrap(): Promise<void> {
    if (bootstrapped.value) return Promise.resolve();
    if (bootstrapPromise === null) {
      bootstrapPromise = refreshSession()
        .then(
          () => undefined,
          () => undefined,
        )
        .finally(() => {
          bootstrapped.value = true;
          bootstrapPromise = null;
        });
    }
    return bootstrapPromise;
  }

  return {
    accessToken,
    bootstrapped,
    isAuthenticated,
    setAccessToken,
    clearSession,
    refreshSession,
    bootstrap,
  };
});
