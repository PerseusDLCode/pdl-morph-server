<script lang="ts">
  // Port of text_elements/i.html.jinja.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
</script>

<span class="tei-i italic">
  {#each node.children as child}
    <ReadableTextContainer node={child} inheritedUrn={urn} />
  {/each}
</span>
