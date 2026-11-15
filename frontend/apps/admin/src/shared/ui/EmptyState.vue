<script setup lang="ts">
import { NEmpty, NFlex } from "naive-ui";

withDefaults(
  defineProps<{
    description?: string;
  }>(),
  {
    description: "暂无数据",
  },
);
</script>

<template>
  <NFlex justify="center" align="center" class="min-h-40 py-8">
    <NEmpty :description="description">
      <template v-if="$slots.default" #extra>
        <slot />
      </template>
    </NEmpty>
  </NFlex>
</template>
