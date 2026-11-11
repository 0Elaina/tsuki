import type { FormItemProps } from "naive-ui";
import { computed, shallowRef } from "vue";

import { isValidationError } from "@/shared/api/errors";

export function useFormErrors() {
  const errors = shallowRef<Record<string, string>>({});
  const formError = computed(() => errors.value._global);

  function fieldFeedback(
    path: string,
  ): Pick<FormItemProps, "feedback" | "validationStatus"> {
    const feedback = errors.value[path];
    return typeof feedback === "string"
      ? { feedback, validationStatus: "error" }
      : {};
  }

  const mutationOptions = {
    meta: { errorFeedback: "form" },
    onMutate() {
      errors.value = {};
    },
    onError(error: unknown) {
      if (!isValidationError(error)) {
        return;
      }

      errors.value =
        Object.keys(error.errors).length > 0
          ? error.errors
          : { _global: error.message };
    },
  };

  return { mutationOptions, fieldFeedback, formError };
}
