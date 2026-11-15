import { createHttpClients } from "@tsuki/api-client";

export const { publicClient, apiClient, request } =
  createHttpClients("/api/v1");
