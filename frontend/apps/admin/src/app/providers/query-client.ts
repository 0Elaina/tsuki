import { ERROR_FEEDBACK } from "@/shared/api/query-meta";
import { MutationCache, QueryClient } from "@tanstack/vue-query";
import {
  getErrorMessage,
  isRequestCanceled,
  isValidationError,
  RequestError,
} from "@tsuki/api-client";
import type { MessageProviderInst } from "naive-ui";
import { shallowRef } from "vue";

export const messageProvider = shallowRef<MessageProviderInst | null>(null);

export const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onError(error, _variables, _onMutateResult, mutation) {
      const feedback = mutation.meta?.errorFeedback;
      if (
        isRequestCanceled(error) ||
        feedback === ERROR_FEEDBACK.SILENT ||
        (feedback === ERROR_FEEDBACK.FORM && isValidationError(error))
      ) {
        return;
      }

      messageProvider.value?.error(
        getErrorMessage(error, "操作失败，请稍后重试"),
      );
    },
  }),
  defaultOptions: {
    queries: {
      retry(failureCount, error) {
        if (failureCount > 0 || !(error instanceof RequestError)) {
          return false;
        }

        const { code, status } = error;
        return (
          code === "NETWORK_ERROR" ||
          (status !== undefined && status >= 500 && status < 600)
        );
      },
    },
    mutations: { retry: false },
  },
});
