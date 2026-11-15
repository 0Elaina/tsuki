export const authKeys = {
  all: ["admin", "auth"] as const,
  me: () => [...authKeys.all, "me"] as const,
} as const;
