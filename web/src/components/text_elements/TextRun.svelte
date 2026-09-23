<script lang="ts">
  // Port of text_elements/text_run.html.jinja.
  import type { TeiTextRun } from "../../tei/types.js";

  const { node }: { node: TeiTextRun } = $props();
</script>

{node.content}
