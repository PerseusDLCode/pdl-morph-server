<script lang="ts">
  // Port of text_elements/stage.html.jinja.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
</script>

<span class="tei-stage italic text-neutral-500">
  {#each node.children as child}
    <ReadableTextContainer node={child} inheritedUrn={urn} />
  {/each}
</span>
