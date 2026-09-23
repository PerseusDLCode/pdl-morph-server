import { redirect } from "@sveltejs/kit";

// The lookup page lives at /morph, as in the old Python app; keep the
// bare root working too (including any ?word=... it was given).
export function load({ url }) {
  redirect(307, `/morph${url.search}`);
}
