<script lang="ts">
  let { children } = $props();
</script>

<!-- These point at the rest of the Perseus site, not at routes in this
     app, so they need a full page load rather than client-side routing. -->
<nav
  class="bg-neutral-500 text-neutral-200 font-bold flex justify-between mb-4 px-8 py-2 text-sm"
  data-sveltekit-reload
>
  <a href="/" class="hover:text-base-content">Home</a>
  <a href="/collections/" class="hover:text-base-content">Collections/Texts</a>
  <a href="https://catalog.perseus.org" class="hover:text-base-content">Perseus Catalog</a>
  <a href="/research/" class="hover:text-base-content">Research</a>
  <a href="/grants/" class="hover:text-base-content">Grants</a>
  <a href="/open-source/" class="hover:text-base-content">Open Source</a>
  <a href="/about/" class="hover:text-base-content">About</a>
  <a href="/help/" class="hover:text-base-content">Help</a>
</nav>

{@render children()}

<footer
  class="footer footer-center sticky bottom-0 bg-base-300 text-base-content p-4"
>
  <p>
    Short definitions courtesy of
    <a
      href="https://logeion.uchicago.edu"
      class="link"
      target="_blank"
      rel="noopener">Logeion</a
    >
  </p>
</footer>
