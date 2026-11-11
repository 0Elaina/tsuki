import { MutationCache, QueryClient } from "@tanstack/vue-query";
import { isRequestCanceled, RequestError } from "@tsuki/api-client";
import type { MessageProviderInst } from "naive-ui";
import { shallowRef } from "vue";

import { getErrorMessage, isValidationError } from "@/shared/api/errors";

export const messageProvider = shallowRef<MessageProviderInst | null>(null);

export const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onError(error, _variables, _onMutateResult, mutation) {
      const feedback = mutation.meta?.errorFeedback;
      if (
        isRequestCanceled(error) ||
        feedback === "local" ||
        (feedback === "form" && isValidationError(error))
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
