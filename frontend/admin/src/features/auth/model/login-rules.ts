import type { FormRule } from 'antd'
import { normalizeUsername } from '@/features/auth/model/login-input'
import type { LoginInput } from '@/features/auth/model/login-input'

function normalizeUsernameForValidation(value: unknown): unknown {
  return typeof value === 'string' ? normalizeUsername(value) : value
}

export const loginFormRules = {
  username: [
    {
      required: true,
      type: 'string',
      transform: normalizeUsernameForValidation,
      message: '请输入用户名',
    },
    {
      type: 'string',
      min: 3,
      max: 32,
      pattern: /^[A-Za-z][A-Za-z0-9_]*$/,
      transform: normalizeUsernameForValidation,
      message: '用户名须为3–32位英文字母开头，且仅包含英文字母、数字和下划线',
    },
  ],
  password: [
    { required: true, type: 'string', message: '请输入密码' },
    {
      type: 'string',
      min: 6,
      max: 128,
      pattern: /^[\x21-\x7e]+$/,
      message: '密码须为6–128位ASCII可见字符，不可包含空格、换行或中文',
    },
  ],
} satisfies Record<keyof LoginInput, FormRule[]>
