// morph.db is queried in-browser (sql.js-httpvfs, via a Web Worker), so
// there's nothing to render server-side or at build time: every route is
// rendered client-side from adapter-static's SPA fallback page.
export const ssr = false;
export const prerender = false;
