import axios from "axios";
import z from "zod";

export const fieldErrorSchema = z.object({
  field: z.string().nullable(),
  message: z.string(),
});

export type FieldError = z.infer<typeof fieldErrorSchema>;

export class RequestError extends Error {
  readonly code: string;
  readonly errors: FieldError[];
  readonly status?: number;
  readonly requestId?: string;

  constructor(
    message: string,
    options: {
      code: string;
      errors?: FieldError[];
      status?: number;
      requestId?: string;
    },
  ) {
    super(message);
    this.name = "RequestError";
    this.code = options.code;
    this.errors = options.errors ?? [];
    this.status = options.status;
    this.requestId = options.requestId;
  }
}

export function isRequestCanceled(error: unknown): boolean {
  return axios.isCancel(error);
}

export const apiEnvelopeSchema = z
  .object({
    data: z.unknown(),
    code: z.string(),
    message: z.string(),
    errors: z.array(fieldErrorSchema),
  })
  .refine(({ data }) => data !== undefined);
