export const ROUTE_NAMES = {
  LOGIN: "login",
  HOME: "home",
  SHELL: "admin-shell",
  FORBIDDEN: "forbidden",
  NOT_FOUND: "not-found",
  TAXONOMY_GROUP: "taxonomy",
  CATEGORIES: "categories",
} as const;

export const ROUTE_PATHS = {
  LOGIN: "/login",
  HOME: "/",
  FORBIDDEN: "/403",
  NOT_FOUND: ":pathMatch(.*)*",
  CATEGORIES: "/taxonomy/categories",
} as const;

export type RouteName = (typeof ROUTE_NAMES)[keyof typeof ROUTE_NAMES];
export type RoutePath = (typeof ROUTE_PATHS)[keyof typeof ROUTE_PATHS];
