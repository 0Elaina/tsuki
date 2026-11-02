export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
}

// 自定义错误尚未完整声明进后端 OpenAPI，按实际错误契约维护。
export interface ValidationIssue {
  location: (string | number)[]
  message: string
  type: string
}

export interface ValidationErrorData {
  errors: ValidationIssue[]
}

export function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof value.code === 'string' &&
    'message' in value &&
    typeof value.message === 'string' &&
    'data' in value
  )
}

function isValidationIssue(value: unknown): value is ValidationIssue {
  return (
    typeof value === 'object' &&
    value !== null &&
    'location' in value &&
    Array.isArray(value.location) &&
    value.location.every((part: unknown) => typeof part === 'string' || typeof part === 'number') &&
    'message' in value &&
    typeof value.message === 'string' &&
    'type' in value &&
    typeof value.type === 'string'
  )
}

export function isValidationErrorData(value: unknown): value is ValidationErrorData {
  return (
    typeof value === 'object' &&
    value !== null &&
    'errors' in value &&
    Array.isArray(value.errors) &&
    value.errors.every(isValidationIssue)
  )
}
