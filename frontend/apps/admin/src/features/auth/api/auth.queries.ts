import { queryOptions, useQuery } from "@tanstack/vue-query";
import { authKeys } from "./auth.keys";
import { useAuthStore } from "../stores/auth.store";
import { adminAuthApi } from "./auth.api";

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
