import { VueQueryPlugin } from "@tanstack/vue-query";
import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./app/App.vue";
import { queryClient } from "./app/providers/query-client";
import { router } from "./app/router";
import "./app/styles/main.css";
import { setupAuth } from "./app/providers/setup-auth";
import { ROUTE_NAMES, ROUTE_PATHS } from "./shared/constants/routes.ts";

const app = createApp(App);

app.use(createPinia());
app.use(VueQueryPlugin, { queryClient });
app.use(router);
setupAuth({
  onAuthExpired: () => {
    if (router.currentRoute.value.name === ROUTE_NAMES.LOGIN) return;
    const currentPath = router.currentRoute.value.fullPath;
    void router.replace({
      name: ROUTE_NAMES.LOGIN,
      query:
        currentPath === ROUTE_PATHS.HOME
          ? undefined
          : {
              redirect: currentPath,
            },
    });
  },
});
app.mount("#app");
