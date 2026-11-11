import js from "@eslint/js";
import { withVueTs, vueTsConfigs } from "@vue/eslint-config-typescript";
import { globalIgnores } from "eslint/config";
import prettier from "eslint-config-prettier/flat";
import vue from "eslint-plugin-vue";
import globals from "globals";

export default withVueTs(
  { rootDir: import.meta.dirname },
  globalIgnores(["**/dist/**", "**/*.timestamp-*-*.mjs"]),
  {
    ...js.configs.recommended,
    files: ["**/*.{js,mjs,cjs}"],
  },
  vue.configs["flat/recommended"],
  vueTsConfigs.recommendedTypeChecked,
  {
    files: ["apps/*/src/**/*.{ts,vue,js}"],
    languageOptions: { globals: globals.browser },
  },
  {
    files: ["eslint.config.mjs", "apps/*/vite.config.ts"],
    languageOptions: { globals: globals.node },
  },
  {
    files: ["**/*.{ts,vue}"],
    rules: { "@typescript-eslint/consistent-type-imports": "error" },
  },
  prettier,
);
