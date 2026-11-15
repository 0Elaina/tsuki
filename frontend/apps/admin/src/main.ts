import { VueQueryPlugin } from "@tanstack/vue-query";
import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./app/App.vue";
import { queryClient } from "./app/providers/query-client";
import { router } from "./app/router";
import "./app/styles/main.css";
import { setupAuth } from "./app/providers/setup-auth";
import { ROUTE_NAMES } from "./shared/constants/routes";

const app = createApp(App);

app.use(createPinia());
app.use(VueQueryPlugin, { queryClient });
app.use(router);
setupAuth({
  onAuthExpired: () => {
    const currentRoute = router.currentRoute.value;
    if (currentRoute.name === ROUTE_NAMES.LOGIN) return;
    const isPlainHome =
      currentRoute.name === ROUTE_NAMES.HOME &&
      Object.keys(currentRoute.query).length === 0 &&
      !currentRoute.hash;
    void router.replace({
      name: ROUTE_NAMES.LOGIN,
      query: isPlainHome
        ? undefined
        : {
            redirect: currentRoute.fullPath,
          },
    });
  },
});
app.mount("#app");
