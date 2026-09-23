<script lang="ts">
  // Port of text_elements/sense.html.jinja.
  //
  // Deliberately different from every other text-element component:
  // Jinja's original renders each child via `{% with text_container=element %}`
  // directly, NOT the `dict(child, urn=child_urn)` merge every other
  // partial uses -- so a <sense>'s children get no urn inherited from
  // it (or from anything above it); only a child's own non-null .urn
  // survives. Passing inheritedUrn={null} here reproduces that exactly.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node }: { node: TeiElement } = $props();
  const level = $derived(Number(node.level ?? 0));
  const n = $derived(node.n as string | undefined);
</script>

<div style="padding-left: {level * 1.25}rem">
  {#if n}
    <span class="font-medium text-neutral-700">{n}</span>
  {/if}
  <span class={n ? "ml-1" : undefined}>
    {#each node.children as child}
      <ReadableTextContainer node={child} inheritedUrn={null} />
    {/each}
  </span>
</div>
