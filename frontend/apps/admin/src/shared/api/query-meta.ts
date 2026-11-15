export const ERROR_FEEDBACK = {
  TOAST: "toast",
  FORM: "form",
  SILENT: "silent",
} as const;

export type ErrorFeedback =
  (typeof ERROR_FEEDBACK)[keyof typeof ERROR_FEEDBACK];

declare module "@tanstack/vue-query" {
  interface Register {
    mutationMeta: {
      errorFeedback?: ErrorFeedback;
    };
  }
}
