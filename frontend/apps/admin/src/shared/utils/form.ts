import type { FormInst } from "naive-ui";

export async function validateForm(form: FormInst | null): Promise<boolean> {
  if (!form) return false;
  try {
    await form.validate();
    return true;
  } catch (error: unknown) {
    if (Array.isArray(error)) return false;
    throw error;
  }
}
