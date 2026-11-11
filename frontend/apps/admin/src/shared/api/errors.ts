import { RequestError } from "@tsuki/api-client";

export function isValidationError(error: unknown): error is RequestError {
  return error instanceof RequestError && error.code === "VALIDATION_FAILED";
}

export function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof RequestError ? error.message : fallback;
}
