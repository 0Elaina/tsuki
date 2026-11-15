import { useMutation, useQueryClient } from "@tanstack/vue-query";
import { useAuthStore } from "../stores/auth.store";
import { authKeys } from "./auth.keys";
import type { LoginPayload, LoginResponse } from "@tsuki/api-client";
import { adminAuthApi } from "./auth.api";

/** 管理员登录：存 token 并清理旧用户缓存 */
export function useLoginMutation() {
  const authStore = useAuthStore();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: LoginPayload) => adminAuthApi.login(payload),
    onSuccess: (result: LoginResponse) => {
      authStore.setAccessToken(result.accessToken);
      queryClient.removeQueries({ queryKey: authKeys.me() });
    },
  });
}

/** 管理员登出：清空 token 和缓存 */
export function useLogoutMutation() {
  const authStore = useAuthStore();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => adminAuthApi.logout(),
    onSettled: () => {
      authStore.clearSession();
      queryClient.clear();
    },
  });
}
