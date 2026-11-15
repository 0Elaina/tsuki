import { createAuthApi } from "@tsuki/api-client";
import { publicClient, apiClient } from "@/shared/api/request";

export const adminAuthApi = createAuthApi({
  publicClient,
  apiClient,
  authBasePath: "/admin/auth",
  mePath: "/admin/me",
});
