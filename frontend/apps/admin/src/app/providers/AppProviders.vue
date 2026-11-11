<script setup lang="ts">
import {
  dateZhCN,
  NConfigProvider,
  NDialogProvider,
  NGlobalStyle,
  NMessageProvider,
  zhCN,
} from "naive-ui";

import { themeOverrides } from "../theme/theme";
import { messageProvider } from "./query-client";
</script>

<template>
  <NConfigProvider
    :locale="zhCN"
    :date-locale="dateZhCN"
    :theme-overrides="themeOverrides"
  >
    <NGlobalStyle />
    <NMessageProvider ref="messageProvider">
      <NDialogProvider>
        <slot />
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
