import type { AxiosRequestConfig } from "axios";
import type z from "zod";

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
