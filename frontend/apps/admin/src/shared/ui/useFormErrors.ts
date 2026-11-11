import type { FieldError } from "@tsuki/api-client";
import type { FormItemProps } from "naive-ui";
import { computed, shallowRef } from "vue";

import { isValidationError } from "@/shared/api/errors";

export function useFormErrors() {
  const errors = shallowRef<FieldError[]>([]);
  const formError = computed(
    () => errors.value.find((error) => error.field === null)?.message,
  );

  function fieldFeedback(
    path: string,
  ): Pick<FormItemProps, "feedback" | "validationStatus"> {
    const error = errors.value.find((entry) => entry.field === path);
    return error ? { feedback: error.message, validationStatus: "error" } : {};
  }

  const mutationOptions = {
    meta: { errorFeedback: "form" },
    onMutate() {
      errors.value = [];
    },
    onError(error: unknown) {
      if (!isValidationError(error)) {
        return;
      }

      errors.value =
        error.errors.length > 0
          ? error.errors
          : [{ field: null, message: error.message }];
    },
  };

  return { mutationOptions, fieldFeedback, formError };
}
