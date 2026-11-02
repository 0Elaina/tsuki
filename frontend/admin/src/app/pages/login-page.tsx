import { Alert, Button, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { useLocation } from 'react-router'
import { BrandMark } from '@/app/components/brand-mark'
import { useLogin } from '@/features/auth/hooks/use-auth'
import type { LoginInput } from '@/features/auth/model/login-input'
import { loginFormRules } from '@/features/auth/model/login-rules'
import { ApiError } from '@/shared/api/api-error'
import type { ValidationIssue } from '@/shared/api/contracts'

const { Title, Text } = Typography

interface SessionExpiredState {
  reason?: string
}

const FIELD_NAMES = ['username', 'password'] as const
type FieldName = (typeof FIELD_NAMES)[number]

function isFieldName(value: unknown): value is FieldName {
  return value === 'username' || value === 'password'
}

interface SubmitFeedback {
  formError: string | null
  fieldErrors: Partial<Record<FieldName, string[]>>
}

const EMPTY_FEEDBACK: SubmitFeedback = { formError: null, fieldErrors: {} }

// 只把位置恰为 body.<字段> 的校验错误落到字段上，其余统一走表单级说明。
function mapValidationIssues(issues: readonly ValidationIssue[]): SubmitFeedback {
  const fieldErrors: Partial<Record<FieldName, string[]>> = {}
  const leftovers: string[] = []

  for (const issue of issues) {
    const [scope, field, ...rest] = issue.location

    if (scope === 'body' && rest.length === 0 && isFieldName(field)) {
      fieldErrors[field] = [...(fieldErrors[field] ?? []), issue.message]
    } else {
      leftovers.push(issue.message)
    }
  }

  return {
    formError: leftovers.length > 0 ? leftovers.join('；') : null,
    fieldErrors,
  }
}

function describeLoginError(error: unknown): SubmitFeedback {
  if (!(error instanceof ApiError)) {
    return { formError: '登录失败，请稍后重试', fieldErrors: {} }
  }

  if (error.code === 'INVALID_CREDENTIALS') {
    return { formError: '用户名或密码错误', fieldErrors: {} }
  }

  const issues = error.validationIssues
  if (issues.length > 0) {
    return mapValidationIssues(issues)
  }

  return { formError: error.message || '登录失败，请稍后重试', fieldErrors: {} }
}

export function LoginPage() {
  const [form] = Form.useForm<LoginInput>()
  const location = useLocation()
  const login = useLogin()
  const [feedback, setFeedback] = useState<SubmitFeedback>(EMPTY_FEEDBACK)

  const sessionExpired =
    (location.state as SessionExpiredState | null)?.reason === 'session-expired'
  const [dismissedExpired, setDismissedExpired] = useState(false)

  // 成功后不在此跳转：身份缓存更新会驱动 PublicOnly 回跳到安全目标地址。
  function handleFinish(values: LoginInput): void {
    setFeedback(EMPTY_FEEDBACK)
    login.mutate(values, {
      onError: (error) => {
        const next = describeLoginError(error)
        setFeedback(next)

        const entries = FIELD_NAMES.filter((name) => next.fieldErrors[name] !== undefined)
        if (entries.length > 0) {
          form.setFields(entries.map((name) => ({ name, errors: next.fieldErrors[name] ?? [] })))
        }
      },
    })
  }

  function handleValuesChange(changed: Partial<LoginInput>): void {
    const touched = Object.keys(changed).filter(isFieldName)
    if (touched.length === 0) {
      return
    }

    // 编辑后上次的服务端反馈均已过期，字段错误与表单说明一起清除。
    setFeedback(EMPTY_FEEDBACK)
    form.setFields(touched.map((name) => ({ name, errors: [] })))
  }

  return (
    <div className="relative flex min-h-dvh items-center justify-center overflow-hidden px-4 py-10">
      {/* 背景光晕：紫粉双团，缓慢漂移，构成登录页的空间感 */}
      <div
        aria-hidden
        className="pointer-events-none absolute -top-40 -left-32 h-136 w-136 animate-[glow-drift_18s_ease-in-out_infinite] rounded-full bg-primary/20 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -right-32 -bottom-40 h-120 w-120 animate-[glow-drift_22s_ease-in-out_infinite_reverse] rounded-full bg-accent/20 blur-3xl"
      />

      <div className="relative w-full max-w-105 animate-[fade-in_250ms_var(--ease-standard)_both]">
        <div className="mb-7 flex flex-col items-center gap-3">
          <BrandMark size={56} />
          <div className="text-center">
            <Title level={2} className="mb-0.5 font-semibold tracking-tight">
              Tsuki
            </Title>
            <Text type="secondary" className="text-[13px]">
              管理端
            </Text>
          </div>
        </div>

        <div className="rounded-card border border-border-glass bg-surface-glass p-7 shadow-raised backdrop-blur-xl backdrop-saturate-150">
          {sessionExpired && !dismissedExpired ? (
            <Alert
              className="mb-5"
              type="warning"
              showIcon
              closable={{ onClose: () => setDismissedExpired(true) }}
              title="登录状态已失效"
              description="请重新登录后继续操作。"
            />
          ) : null}

          <Form
            form={form}
            layout="vertical"
            requiredMark={false}
            onFinish={handleFinish}
            onValuesChange={handleValuesChange}
            disabled={login.isPending}
          >
            <Form.Item label="用户名" name="username" rules={loginFormRules.username}>
              <Input autoComplete="username" autoFocus placeholder="请输入管理员用户名" />
            </Form.Item>

            <Form.Item label="密码" name="password" rules={loginFormRules.password}>
              <Input.Password autoComplete="current-password" placeholder="请输入密码" />
            </Form.Item>

            {feedback.formError !== null ? (
              <div className="mb-4">
                <Alert type="error" showIcon title={feedback.formError} />
              </div>
            ) : null}

            <Button type="primary" htmlType="submit" block loading={login.isPending} size="large">
              登录
            </Button>
          </Form>
        </div>
      </div>
    </div>
  )
}
