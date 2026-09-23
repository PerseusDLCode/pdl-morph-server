<script lang="ts">
  // Port of text_elements/bibl.html.jinja.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } =
    $props();
  const n = $derived(node.n as string | undefined);
</script>

{#if n}
  <a href="#{n}" data-ref={n} class="perseus-reference tei-bibl">
    {#each node.children as child}
      <ReadableTextContainer node={child} inheritedUrn={urn} />
    {/each}
  </a>
{:else}
  <span class="tei-bibl">
    {#each node.children as child}
      <ReadableTextContainer node={child} inheritedUrn={urn} />
    {/each}
  </span>
{/if}
