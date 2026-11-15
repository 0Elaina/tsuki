import type { AxiosInstance } from "axios";
import z from "zod";

import { requestWithClient } from "./http";

export const ACCOUNT_ROLES = ["ADMIN", "USER"] as const;

export const accountRoleSchema = z.enum(ACCOUNT_ROLES);
export type AccountRole = z.infer<typeof accountRoleSchema>;

const csrfTokenSchema = z.object({
  token: z.string().min(1),
  headerName: z.string().min(1),
});

const loginResponseSchema = z.object({
  accessToken: z.string().min(1),
  expiresIn: z.number(),
});

const accountIdentitySchema = z.object({
  id: z.string(),
  username: z.string(),
  nickname: z.string(),
  role: accountRoleSchema,
});

export interface LoginPayload {
  username: string;
  password: string;
}

export type LoginResponse = z.infer<typeof loginResponseSchema>;
export type AccountIdentity = z.infer<typeof accountIdentitySchema>;

export interface AuthApiOptions {
  publicClient: AxiosInstance;
  apiClient: AxiosInstance;
  authBasePath: string;
  mePath: string;
}

export function createAuthApi(options: AuthApiOptions) {
  async function postWithCsrf<T>(
    subPath: string,
    extra: { data?: unknown; schema?: z.ZodType<T> } = {},
  ): Promise<T> {
    const csrf = await requestWithClient(
      {
        method: "GET",
        url: `${options.authBasePath}/csrf`,
        schema: csrfTokenSchema,
      },
      options.publicClient,
    );
    return requestWithClient(
      {
        method: "POST",
        url: `${options.authBasePath}${subPath}`,
        headers: { [csrf.headerName]: csrf.token },
        ...extra,
      },
      options.publicClient,
    );
  }

  return {
    login: (payload: LoginPayload) =>
      postWithCsrf("/login", { data: payload, schema: loginResponseSchema }),
    refresh: () => postWithCsrf("/refresh", { schema: loginResponseSchema }),
    logout: async (): Promise<void> => {
      await postWithCsrf<null>("/logout");
    },
    fetchMe: (signal?: AbortSignal) =>
      requestWithClient(
        {
          method: "GET",
          url: options.mePath,
          signal,
          schema: accountIdentitySchema,
        },
        options.apiClient,
      ),
  };
}
