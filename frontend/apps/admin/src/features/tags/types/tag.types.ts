import type { PageQuery } from "@tsuki/api-client";

export const TAG_STATUS = {
  ACTIVE: "ACTIVE",
  DISABLED: "DISABLED",
} as const;

export const TAG_STATUS_SET = new Set<string>(Object.values(TAG_STATUS));
export type TagStatus = (typeof TAG_STATUS)[keyof typeof TAG_STATUS];

export const TAG_LIMITS = {
  NAME_MAX_LENGTH: 64,
  SORT_ORDER_MIN: 0,
  BATCH_MAX_IDS: 100,
} as const;

export interface TagItem {
  id: string;
  name: string;
  sortOrder: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface TagListQuery extends PageQuery {
  keyword?: string;
  status?: TagStatus;
}

export interface CreateTagPayload {
  name: string;
  sortOrder?: number;
}

export interface UpdateTagPayload {
  name: string;
  sortOrder: number;
}

export interface UpdateTagStatusPayload {
  status: TagStatus;
}

export interface BatchTagStatusPayload {
  ids: string[];
  status: TagStatus;
}

export interface BatchTagIdsPayload {
  ids: string[];
}
