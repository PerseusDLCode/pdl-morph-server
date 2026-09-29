<script lang="ts">
  import { asset } from "$app/paths";

  let { children } = $props();

  // Mirrors MinimumViablePerseus's nav_links macro
  // (src/mvp/site/templates/components/macros.html.jinja).
  const navLinks = [
    { href: "/", label: "Home" },
    { href: "/collections/", label: "Collections/Texts" },
    { href: "https://catalog.perseus.org", label: "Perseus Catalog" },
    { href: "/research/", label: "Research" },
    { href: "/grants/", label: "Grants" },
    { href: "/open-source/", label: "Open Source" },
    { href: "/about/", label: "About" },
    { href: "/abbreviations/", label: "Abbreviations" },
    { href: "/help/", label: "Help" },
  ];
</script>

<!-- Header and navbar ported from MinimumViablePerseus's base.html.jinja.
     These links point at the rest of the Perseus site, not at routes in
     this app, so they need a full page load rather than client-side
     routing. -->
<div data-sveltekit-reload>
  <header class="p-4">
    <div class="text-base-content w-full flex items-center gap-4">
      <a href="/" title="home" class="link">
        <img
          class="w-24 h-24"
          src={asset("/images/perseus-running-man.png")}
          alt="Perseus Digital Library"
        />
      </a>
      <div>
        <h1 class="text-xl font-bold small-caps text-shadow-sm">Perseus Digital Library</h1>
        <h2 class="text-lg font-bold text-shadow-xs">Gregory R. Crane, Editor-in-Chief</h2>
        <h2 class="text-lg font-bold text-shadow-xs">Tufts University</h2>
      </div>
    </div>
  </header>

  <div class="navbar bg-primary text-primary-content font-bold">
    <div class="navbar-start">
      <div class="dropdown">
        <div tabindex="0" role="button" class="btn lg:hidden" aria-label="Menu">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            class="h-5 w-5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M4 6h16M4 12h8m-8 6h16"
            />
          </svg>
        </div>
        <ul
          tabindex="-1"
          class="menu menu-sm dropdown-content bg-primary z-1 mt-3 w-52 p-2 shadow"
        >
          {#each navLinks as { href, label }}
            <li><a {href} class="hover:text-primary-content">{label}</a></li>
          {/each}
        </ul>
      </div>
    </div>
    <div class="navbar-center hidden lg:flex">
      <ul class="menu menu-horizontal px-1">
        {#each navLinks as { href, label }}
          <li><a {href} class="hover:text-primary-content">{label}</a></li>
        {/each}
      </ul>
    </div>
    <div class="navbar-end"></div>
  </div>
</div>

<main class="p-4">
  {@render children()}
</main>

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
