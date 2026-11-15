export const ROUTE_NAMES = {
  LOGIN: "login",
  HOME: "home",
} as const;

export const ROUTE_PATHS = {
  LOGIN: "/login",
  HOME: "/",
} as const;

export type RouteName = (typeof ROUTE_NAMES)[keyof typeof ROUTE_NAMES];
export type RoutePath = (typeof ROUTE_PATHS)[keyof typeof ROUTE_PATHS];
