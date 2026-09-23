<script lang="ts">
  // Port of text_elements/lb.html.jinja.
  import type { TeiElement } from "../../tei/types.js";

  const { node }: { node: TeiElement } = $props();
  const n = $derived((node.n as string | undefined) ?? "");
</script>

<span class="tei-lb" data-n={n}></span><br />
