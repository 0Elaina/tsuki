import axios from "axios";
import type {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";
import type z from "zod";

import { apiEnvelopeSchema, isRequestCanceled, RequestError } from "./errors";

export interface PageQuery {
  page?: number;
  size?: number;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface RequestConfig<
  T = unknown,
> extends AxiosRequestConfig<unknown> {
  schema?: z.ZodType<T>;
}

export interface AuthInterceptorHooks {
  getAccessToken: () => string | null | undefined;
  refreshAccessToken: () => Promise<string | null>;
  onAuthExpired?: () => void;
}

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

export async function requestWithClient<T>(
  config: RequestConfig<T>,
  client: AxiosInstance,
): Promise<T> {
  const { schema, ...axiosConfig } = config;
  let response: AxiosResponse<unknown>;

  try {
    response = await client.request<unknown>(axiosConfig);
  } catch (error) {
    if (isRequestCanceled(error) || !axios.isAxiosError<unknown>(error)) {
      throw error;
    }
    if (!error.response) {
      throw new RequestError("网络请求失败，请稍后重试", {
        code: "NETWORK_ERROR",
      });
    }
    response = error.response;
  }

  const { status } = response;
  const requestIdHeader: unknown = response.headers["x-request-id"];
  const requestId =
    typeof requestIdHeader === "string" ? requestIdHeader : undefined;
  const httpSuccess = status >= 200 && status < 300;
  const parsed = apiEnvelopeSchema.safeParse(response.data);

  if (!parsed.success) {
    throw new RequestError(
      httpSuccess ? "服务器响应格式不正确" : "请求处理失败",
      {
        code: httpSuccess ? "INVALID_RESPONSE" : "REQUEST_FAILED",
        status,
        requestId,
      },
    );
  }

  const { code, message, errors, data } = parsed.data;
  if (!httpSuccess || code !== "SUCCESS") {
    throw new RequestError(message, { code, errors, status, requestId });
  }
  if (!schema) {
    return data as T;
  }

  const payload = await schema.safeParseAsync(data);
  if (!payload.success) {
    throw new RequestError("服务器响应格式不正确", {
      code: "INVALID_RESPONSE",
      status,
      requestId,
    });
  }
  return payload.data;
}

export function createHttpClients(baseURL: string, timeout = 10_000) {
  const publicClient = axios.create({
    baseURL,
    timeout,
    withCredentials: true,
  });
  const apiClient = axios.create({
    baseURL,
    timeout,
    withCredentials: true,
  });

  function request<T>(
    config: RequestConfig<T>,
    client: AxiosInstance = apiClient,
  ): Promise<T> {
    return requestWithClient(config, client);
  }

  return { publicClient, apiClient, request };
}
