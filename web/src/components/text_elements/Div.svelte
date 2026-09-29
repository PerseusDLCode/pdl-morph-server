<script lang="ts">
  // Port of text_elements/div.html.jinja.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
  const ctsUrn = $derived(urn ?? "unknown");
  const type = $derived(node.type as string | undefined);
  const isStrophic = $derived(type === "antistrophe" || type === "epode" || type === "strophe");
  const label = $derived(type === "epode" ? "ep" : (type?.slice(0, 3) ?? ""));
  const n = $derived(node.n as string | undefined);
</script>

{#if isStrophic}
  <div class="relative">
    <div class="absolute right-0 select-none text-neutral-500 text-sm">
      {label}. {n}
    </div>
  </div>
{/if}
<div class="prose tei-div mb-4" data-urn={ctsUrn} role="presentation">
  <div>
    {#each node.children as child}
      <ReadableTextContainer node={child} inheritedUrn={urn} />
    {/each}
  </div>
</div>
