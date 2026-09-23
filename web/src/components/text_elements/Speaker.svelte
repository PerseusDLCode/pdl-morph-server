<script lang="ts">
  // Port of text_elements/speaker.html.jinja.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
</script>

<div class="tei-speaker font-bold mt-4 mx-auto">
  {#each node.children as child}
    <ReadableTextContainer node={child} inheritedUrn={urn} />
  {/each}
</div>
