import { useMutation, useQueryClient } from "@tanstack/vue-query";
import type {
  BatchCategoryIdsPayload,
  BatchCategoryStatusPayload,
  CreateCategoryPayload,
  UpdateCategoryPayload,
} from "../types/category.types";
import { categoryApi } from "./category.api";
import { categoryKeys } from "./category.keys";
import { type CategoryStatus } from "../types/category.types";

export function useCreateCategoryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateCategoryPayload) => categoryApi.create(data),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: categoryKeys.lists() }),
  });
}

export function useUpdateCategoryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateCategoryPayload }) =>
      categoryApi.update(id, data),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: categoryKeys.lists() }),
  });
}

export function useUpdateCategoryStatusMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: CategoryStatus }) =>
      categoryApi.updateStatus(id, { status }),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: categoryKeys.lists() }),
  });
}

export function useDeleteCategoryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => categoryApi.remove(id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: categoryKeys.lists() }),
  });
}

export function useBatchCategoryStatusMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: BatchCategoryStatusPayload) =>
      categoryApi.batchStatus(data),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: categoryKeys.lists() }),
  });
}

export function useBatchDeleteCategoryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: BatchCategoryIdsPayload) =>
      categoryApi.batchDelete(data),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: categoryKeys.lists() }),
  });
}
