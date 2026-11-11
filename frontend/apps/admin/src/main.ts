import { VueQueryPlugin } from "@tanstack/vue-query";
import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./app/App.vue";
import { queryClient } from "./app/providers/query-client";
import { router } from "./app/router";
import "./app/styles/main.css";

const app = createApp(App);

app.use(createPinia());
app.use(VueQueryPlugin, { queryClient });
app.use(router);
app.mount("#app");
