import { createPageResultSchema, type PageResult } from "@tsuki/api-client";
import z from "zod";
import type {
  BatchCategoryIdsPayload,
  BatchCategoryStatusPayload,
  CategoryItem,
  CategoryListQuery,
  CreateCategoryPayload,
  UpdateCategoryPayload,
  UpdateCategoryStatusPayload,
} from "../types/category.types";
import { request } from "@/shared/api/request";

const CATEGORIES_BASE_PATH = "/admin/categories";
const categoryItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  sortOrder: z.number(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
const categoryPageSchema = createPageResultSchema(categoryItemSchema);

export const categoryApi = {
  list(
    params: CategoryListQuery,
    signal?: AbortSignal,
  ): Promise<PageResult<CategoryItem>> {
    return request<PageResult<CategoryItem>>({
      method: "GET",
      url: CATEGORIES_BASE_PATH,
      params,
      signal,
      schema: categoryPageSchema,
    });
  },
  getById(id: string, signal?: AbortSignal): Promise<CategoryItem> {
    return request<CategoryItem>({
      method: "GET",
      url: `${CATEGORIES_BASE_PATH}/${id}`,
      signal,
      schema: categoryItemSchema,
    });
  },
  create(data: CreateCategoryPayload): Promise<CategoryItem> {
    return request<CategoryItem>({
      method: "POST",
      url: CATEGORIES_BASE_PATH,
      data,
      schema: categoryItemSchema,
    });
  },
  update(id: string, data: UpdateCategoryPayload): Promise<CategoryItem> {
    return request<CategoryItem>({
      method: "PATCH",
      url: `${CATEGORIES_BASE_PATH}/${id}`,
      data,
      schema: categoryItemSchema,
    });
  },
  updateStatus(
    id: string,
    data: UpdateCategoryStatusPayload,
  ): Promise<CategoryItem> {
    return request<CategoryItem>({
      method: "PUT",
      url: `${CATEGORIES_BASE_PATH}/${id}/status`,
      data,
      schema: categoryItemSchema,
    });
  },
  async remove(id: string): Promise<null> {
    return await request<null>({
      method: "DELETE",
      url: `${CATEGORIES_BASE_PATH}/${id}`,
    });
  },
  batchStatus(data: BatchCategoryStatusPayload): Promise<number> {
    return request<number>({
      method: "POST",
      url: `${CATEGORIES_BASE_PATH}/batch-status`,
      data,
    });
  },
  batchDelete(data: BatchCategoryIdsPayload): Promise<number> {
    return request<number>({
      method: "POST",
      url: `${CATEGORIES_BASE_PATH}/batch-delete`,
      data,
    });
  },
};
