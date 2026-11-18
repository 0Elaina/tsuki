import { useMutation, useQueryClient } from "@tanstack/vue-query";
import type {
  BatchTagIdsPayload,
  BatchTagStatusPayload,
  CreateTagPayload,
  TagStatus,
  UpdateTagPayload,
} from "../types/tag.types";
import { tagApi } from "./tag.api";
import { tagKeys } from "./tag.keys";

export function useCreateTagMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateTagPayload) => tagApi.create(data),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: tagKeys.lists() }),
  });
}

export function useUpdateTagMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateTagPayload }) =>
      tagApi.update(id, data),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: tagKeys.lists() }),
  });
}

export function useUpdateTagStatusMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: TagStatus }) =>
      tagApi.updateStatus(id, { status }),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: tagKeys.lists() }),
  });
}

export function useDeleteTagMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => tagApi.remove(id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: tagKeys.lists() }),
  });
}

export function useBatchTagStatusMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: BatchTagStatusPayload) => tagApi.batchStatus(data),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: tagKeys.lists() }),
  });
}

export function useBatchDeleteTagMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: BatchTagIdsPayload) => tagApi.batchDelete(data),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: tagKeys.lists() }),
  });
}
