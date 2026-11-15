import { useAuthStore } from "@/features/auth/model/auth.store";
import { apiClient } from "@/shared/api/request";
import { attachAuthInterceptor } from "@tsuki/api-client";
import { queryClient } from "./query-client";

export interface SetupAuthOptions {
  onAuthExpired?: () => void;
}

export function setupAuth(options?: SetupAuthOptions): void {
  const authStore = useAuthStore();
  attachAuthInterceptor(apiClient, {
    getAccessToken: () => authStore.accessToken,
    refreshAccessToken: () => authStore.refreshSession(),
    onAuthExpired: () => {
      authStore.clearSession();
      queryClient.clear();
      options?.onAuthExpired?.();
    },
  });
}
