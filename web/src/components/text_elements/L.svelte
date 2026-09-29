<script lang="ts">
  // Port of text_elements/l.html.jinja (a verse line).
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();

  // Mirrors Jinja's `int` filter: parses the "n" attribute (defaulting
  // to the string "1" when absent), falling back to 0 when it doesn't
  // parse as an integer rather than propagating NaN.
  const n = $derived.by(() => {
    const parsed = parseInt((node.n as string | undefined) ?? "1", 10);
    return Number.isNaN(parsed) ? 0 : parsed;
  });
  const showLineNumber = $derived((n % 5 === 0 && n !== 0) || n === 1);
</script>

<div class="prose tei-l flex justify-between w-full">
  <div>
    {#each node.children as child}
      <ReadableTextContainer node={child} inheritedUrn={urn} />
    {/each}
  </div>

  {#if showLineNumber}
    <span class="select-none">{n}</span>
  {/if}
</div>
