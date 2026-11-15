<script setup lang="ts">
import { NFlex, NSpin } from "naive-ui";

withDefaults(
  defineProps<{
    label?: string;
  }>(),
  {
    label: "加载中...",
  },
);
</script>

<template>
  <NFlex justify="center" align="center" class="min-h-40 py-8">
    <NSpin :description="label" />
  </NFlex>
</template>
