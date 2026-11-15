import { MutationCache, QueryClient } from "@tanstack/vue-query";
import {
  getErrorMessage,
  isRequestCanceled,
  RequestError,
} from "@tsuki/api-client";
import type { MessageProviderInst } from "naive-ui";
import { shallowRef } from "vue";

export const messageProvider = shallowRef<MessageProviderInst | null>(null);

export const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onError(error) {
      if (isRequestCanceled(error)) {
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
