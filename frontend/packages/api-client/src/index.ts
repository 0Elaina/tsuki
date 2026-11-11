import axios from "axios";
import type { AxiosRequestConfig, AxiosResponse, Method } from "axios";
import { z } from "zod";

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

type RequestOptions<T> = Pick<
  AxiosRequestConfig<unknown, unknown>,
  "params" | "data" | "headers" | "timeout"
> & {
  method: Method;
  url: string;
  signal?: AbortSignal;
  schema?: z.ZodType<T>;
};

const responseSchema = z
  .object({
    data: z.unknown(),
    code: z.string(),
    message: z.string(),
    errors: z.record(z.string(), z.string()),
  })
  .refine(({ data }) => data !== undefined);

export class RequestError extends Error {
  readonly code: string;
  readonly errors: Record<string, string>;
  readonly status?: number;
  readonly requestId?: string;

  constructor(
    message: string,
    options: {
      code: string;
      errors?: Record<string, string>;
      status?: number;
      requestId?: string;
    },
  ) {
    super(message);
    this.name = "RequestError";
    this.code = options.code;
    this.errors = options.errors ?? {};
    this.status = options.status;
    this.requestId = options.requestId;
  }
}

export function isRequestCanceled(error: unknown): boolean {
  return axios.isCancel(error);
}

export function createRequest(baseURL: string) {
  const client = axios.create({ baseURL, timeout: 10_000 });

  return async function request<T>(options: RequestOptions<T>): Promise<T> {
    const { schema, ...config } = options;
    let response: AxiosResponse<unknown>;

    try {
      response = await client.request<unknown>(config);
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
    const requestIdHeader = response.headers["x-request-id"];
    const requestId =
      typeof requestIdHeader === "string" ? requestIdHeader : undefined;
    const httpSuccess = status >= 200 && status < 300;
    const parsed = responseSchema.safeParse(response.data);

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
  };
}
