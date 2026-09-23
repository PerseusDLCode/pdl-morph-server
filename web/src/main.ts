import { mount } from "svelte";
import App from "./App.svelte";

const target = document.getElementById("app");
if (target === null) {
  throw new Error("#app element not found");
}

export default mount(App, { target });
