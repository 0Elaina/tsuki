import type { components } from '@/shared/api/generated/openapi'

export type LoginInput = components['schemas']['LoginRequest']

export function normalizeUsername(username: string): string {
  return username.trim()
}
