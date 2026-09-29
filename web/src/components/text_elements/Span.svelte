<script lang="ts">
  // Port of text_elements/span.html.jinja -- also the dispatcher's
  // fallback for any TEI tag without a dedicated component.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
</script>

<span class="prose tei-{node.tagname}">
  {#each node.children as child}
    <ReadableTextContainer node={child} inheritedUrn={urn} />
  {/each}
</span>
