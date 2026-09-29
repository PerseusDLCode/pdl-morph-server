<script lang="ts">
  // Port of text_elements/token.html.jinja. Token nodes are only ever
  // produced by kodon_py.tei_parser.inject_tokens, which nothing in this
  // app calls (see Phase 4's notes) -- this component exists for parity
  // with the Jinja template set, but is unreachable with current data.
  interface TokenNode {
    tagname: "token";
    urn?: string | null;
    text?: string;
    whitespace?: boolean;
  }

  const { node, urn = null }: { node: TokenNode; urn?: string | null } = $props();
  const tokenUrn = $derived(node.urn ?? urn);
</script>

<span data-token-urn={tokenUrn ?? undefined}>{node.text}</span>{#if node.whitespace} {/if}
