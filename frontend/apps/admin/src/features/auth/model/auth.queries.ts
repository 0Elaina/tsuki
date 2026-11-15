import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationOptions,
} from "@tanstack/vue-query";
import { adminAuthApi } from "../api/auth.api";
import { useAuthStore } from "./auth.store";
import type { LoginPayload, LoginResponse } from "@tsuki/api-client";
import { ERROR_FEEDBACK } from "@/shared/api/query-meta";

export const authKeys = {
  all: ["admin", "auth"] as const,
  me: () => [...authKeys.all, "me"] as const,
} as const;

// 获取当前管理员信息的查询配置
export function adminMeQueryOptions() {
  return queryOptions({
    queryKey: authKeys.me(),
    queryFn: ({ signal }) => adminAuthApi.fetchMe(signal), // signal 用于取消请求，避免竞态
    staleTime: 5 * 60_000, // 5 分钟内视为新鲜，不自动 refetch
  });
}

// 组合式：仅在已登录时才发起请求
export function useMeQuery() {
  const authStore = useAuthStore();
  return useQuery({
    ...adminMeQueryOptions(),
    enabled: () => authStore.isAuthenticated,
  });
}

/** 管理员登录：存 token 并清理旧用户缓存；可传自定义 mutation 选项，但 mutationFn 固定不可覆盖。 */
export function useLoginMutation(
  options?: Omit<
    UseMutationOptions<LoginResponse, unknown, LoginPayload>,
    "mutationFn"
  >,
) {
  const authStore = useAuthStore();
  const queryClient = useQueryClient();
  return useMutation({
    ...options,
    mutationFn: async (payload: LoginPayload): Promise<LoginResponse> => {
      const result = await adminAuthApi.login(payload);
      authStore.setAccessToken(result.accessToken);
      queryClient.removeQueries({
        queryKey: authKeys.me(),
      });
      return result;
    },
  });
}

/** 管理员登出：清空 token 和缓存 */
export function useLogoutMutation() {
  const authStore = useAuthStore();
  const queryClient = useQueryClient();
  return useMutation({
    meta: { errorFeedback: ERROR_FEEDBACK.SILENT },
    mutationFn: async (): Promise<void> => {
      try {
        await adminAuthApi.logout();
      } finally {
        authStore.clearSession();
        queryClient.clear();
      }
    },
  });
}
