<script lang="ts">
  // Port of text_elements/head.html.jinja.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
</script>

<h1 class="prose tei-head">
  {#each node.children as child}
    <ReadableTextContainer node={child} inheritedUrn={urn} />
  {/each}
</h1>
