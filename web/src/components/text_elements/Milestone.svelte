<script lang="ts">
  // Port of text_elements/milestone.html.jinja.
  import type { TeiElement } from "../../tei/types.js";

  const { node }: { node: TeiElement } = $props();
  const unit = $derived(node.unit as string | undefined);
  const n = $derived((node.n as string | undefined) ?? "");
  const id = $derived(`${unit}-${n}`);
  const title = $derived(`${unit ?? ""} ${n}`);
</script>

<span {id} data-n={n} data-unit={unit} class="tei-milestone" {title}></span>
