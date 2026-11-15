import { VueQueryPlugin } from "@tanstack/vue-query";
import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./app/App.vue";
import { queryClient } from "./app/providers/query-client";
import { router } from "./app/router";
import "./app/styles/main.css";
import { setupAuth } from "./app/providers/setup-auth";

const app = createApp(App);

app.use(createPinia());
app.use(VueQueryPlugin, { queryClient });
app.use(router);
setupAuth();
app.mount("#app");
