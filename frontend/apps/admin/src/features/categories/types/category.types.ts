import type { PageQuery } from "@tsuki/api-client";

export const CATEGORY_STATUS = {
  ACTIVE: "ACTIVE",
  DISABLED: "DISABLED",
} as const;

export type CategoryStatus =
  (typeof CATEGORY_STATUS)[keyof typeof CATEGORY_STATUS];

export const CATEGORY_LIMITS = {
  NAME_MAX_LENGTH: 64,
  SORT_ORDER_MIN: 0,
  BATCH_MAX_IDS: 100,
};

export interface CategoryItem {
  id: string;
  name: string;
  sortOrder: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryListQuery extends PageQuery {
  keyword?: string;
  status?: CategoryStatus;
}

export interface CreateCategoryPayload {
  name: string;
  sortOrder?: number;
}

export interface UpdateCategoryPayload {
  name: string;
  sortOrder: number;
}

export interface UpdateCategoryStatusPayload {
  status: CategoryStatus;
}

export interface BatchCategoryStatusPayload {
  ids: string[];
  status: CategoryStatus;
}

export interface BatchCategoryIdsPayload {
  ids: string[];
}
