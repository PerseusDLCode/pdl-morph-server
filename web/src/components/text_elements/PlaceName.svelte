<script lang="ts">
  // Port of text_elements/placeName.html.jinja.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
  // Mirrors `text_container.get("key", text_container.get("urn"))`:
  // Jinja stringifies a missing key/urn as the literal text "None"
  // (Python's default Jinja environment doesn't blank out None), not
  // an empty string -- kept as-is rather than "fixed" to "".
  const hrefTarget = $derived((node.key as string | undefined) ?? urn ?? "None");
</script>

<a href="#{hrefTarget}" class="tei-placeName hover:font-extrabold">
  {#each node.children as child}
    <ReadableTextContainer node={child} inheritedUrn={urn} />
  {/each}
</a>
