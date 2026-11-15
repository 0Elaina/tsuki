import axios from "axios";
import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";

import { apiEnvelopeSchema, isRequestCanceled } from "./errors";
import type { AuthInterceptorHooks } from "./types";

const RETRIED = Symbol("retried");

type RetryableConfig = InternalAxiosRequestConfig & {
  [RETRIED]?: boolean;
};

export function attachAuthInterceptor(
  client: AxiosInstance,
  hooks: AuthInterceptorHooks,
): void {
  let refreshPromise: Promise<string | null> | null = null;

  function runSingleFlightRefresh(): Promise<string | null> {
    if (refreshPromise === null) {
      refreshPromise = hooks
        .refreshAccessToken()
        .then((token) => {
          if (!token) {
            hooks.onAuthExpired?.();
          }
          return token;
        })
        .finally(() => {
          refreshPromise = null;
        });
    }
    return refreshPromise;
  }

  client.interceptors.request.use((config) => {
    const token = hooks.getAccessToken();
    if (token && !config.headers.has("Authorization")) {
      config.headers.set("Authorization", `Bearer ${token}`);
    }
    return config;
  });

  client.interceptors.response.use(undefined, async (error: unknown) => {
    if (
      isRequestCanceled(error) ||
      !axios.isAxiosError<unknown>(error) ||
      !error.response ||
      !error.config
    ) {
      throw error;
    }

    const originalConfig: RetryableConfig = error.config;
    if (error.response.status !== 401 || originalConfig[RETRIED] === true) {
      throw error;
    }

    const envelope = apiEnvelopeSchema.safeParse(error.response.data);
    if (envelope.success && envelope.data.code !== "UNAUTHENTICATED") {
      throw error;
    }

    const newToken = await runSingleFlightRefresh();
    if (!newToken) {
      throw error;
    }

    originalConfig[RETRIED] = true;
    originalConfig.headers.set("Authorization", `Bearer ${newToken}`);
    return client.request(originalConfig);
  });
}
