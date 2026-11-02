import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { login, logout } from '@/features/auth/api/auth-api'
import { currentAdminQueryOptions } from '@/features/auth/api/current-admin-query'
import {
  noteSessionResult,
  noteVoluntaryLogout,
  updateSessionCache,
} from '@/features/auth/api/session-cache'

export function useCurrentAdmin() {
  return useQuery(currentAdminQueryOptions)
}

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: login,
    onSuccess: (result) => {
      noteSessionResult(result.admin)
      return updateSessionCache(queryClient, result.admin)
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      noteVoluntaryLogout()
      return updateSessionCache(queryClient, null)
    },
  })
}
