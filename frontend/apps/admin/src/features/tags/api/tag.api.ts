import { createPageResultSchema, type PageResult } from "@tsuki/api-client";
import z from "zod";
import type {
  BatchTagIdsPayload,
  BatchTagStatusPayload,
  CreateTagPayload,
  TagItem,
  TagListQuery,
  UpdateTagPayload,
  UpdateTagStatusPayload,
} from "../types/tag.types";
import { request } from "@/shared/api/request";

const TAGS_BASE_PATH = "/admin/tags";

const tagItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  sortOrder: z.number(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

const tagPageSchema = createPageResultSchema(tagItemSchema);

export const tagApi = {
  list(
    params: TagListQuery,
    signal?: AbortSignal,
  ): Promise<PageResult<TagItem>> {
    return request<PageResult<TagItem>>({
      method: "GET",
      url: TAGS_BASE_PATH,
      params,
      signal,
      schema: tagPageSchema,
    });
  },

  getById(id: string, signal?: AbortSignal): Promise<TagItem> {
    return request<TagItem>({
      method: "GET",
      url: `${TAGS_BASE_PATH}/${id}`,
      signal,
      schema: tagItemSchema,
    });
  },

  create(data: CreateTagPayload): Promise<TagItem> {
    return request<TagItem>({
      method: "POST",
      url: TAGS_BASE_PATH,
      data,
      schema: tagItemSchema,
    });
  },

  update(id: string, data: UpdateTagPayload): Promise<TagItem> {
    return request<TagItem>({
      method: "PATCH",
      url: `${TAGS_BASE_PATH}/${id}`,
      data,
      schema: tagItemSchema,
    });
  },

  updateStatus(id: string, data: UpdateTagStatusPayload): Promise<TagItem> {
    return request<TagItem>({
      method: "PUT",
      url: `${TAGS_BASE_PATH}/${id}/status`,
      data,
      schema: tagItemSchema,
    });
  },

  remove(id: string): Promise<null> {
    return request<null>({
      method: "DELETE",
      url: `${TAGS_BASE_PATH}/${id}`,
      schema: z.null(),
    });
  },

  batchStatus(data: BatchTagStatusPayload): Promise<number> {
    return request<number>({
      method: "POST",
      url: `${TAGS_BASE_PATH}/batch-status`,
      data,
      schema: z.number().int(),
    });
  },

  batchDelete(data: BatchTagIdsPayload): Promise<number> {
    return request<number>({
      method: "POST",
      url: `${TAGS_BASE_PATH}/batch-delete`,
      data,
      schema: z.number().int(),
    });
  },
};
