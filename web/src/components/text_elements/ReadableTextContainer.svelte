<script lang="ts">
  // Port of components/ReadableTextContainer.html.jinja: picks the
  // text_elements/<tagname>.html.jinja partial for a node (subtype
  // attribute wins over tagname, matching the original), falling back to
  // span.html.jinja (Span.svelte) for any TEI tag with no dedicated
  // component -- LSJ/Lewis & Short/Middle Liddell entries use plenty of
  // tags (foreign, cit, trans, tr, usg, ref, orth, ...) that only ever
  // hit this fallback.
  import type { TeiNode } from "../../tei/types.js";
  import Bibl from "./Bibl.svelte";
  import Div from "./Div.svelte";
  import Elements from "./Elements.svelte";
  import Head from "./Head.svelte";
  import I from "./I.svelte";
  import L from "./L.svelte";
  import Lb from "./Lb.svelte";
  import Lg from "./Lg.svelte";
  import Milestone from "./Milestone.svelte";
  import Note from "./Note.svelte";
  import P from "./P.svelte";
  import Pb from "./Pb.svelte";
  import PlaceName from "./PlaceName.svelte";
  import Quote from "./Quote.svelte";
  import S from "./S.svelte";
  import Sense from "./Sense.svelte";
  import Sp from "./Sp.svelte";
  import Span from "./Span.svelte";
  import Speaker from "./Speaker.svelte";
  import Stage from "./Stage.svelte";
  import TextRun from "./TextRun.svelte";
  import Token from "./Token.svelte";

  const COMPONENTS_BY_TAG: Record<string, unknown> = {
    bibl: Bibl,
    div: Div,
    elements: Elements,
    head: Head,
    i: I,
    l: L,
    lb: Lb,
    lg: Lg,
    milestone: Milestone,
    note: Note,
    p: P,
    pb: Pb,
    placeName: PlaceName,
    quote: Quote,
    s: S,
    sense: Sense,
    sp: Sp,
    span: Span,
    speaker: Speaker,
    stage: Stage,
    text_run: TextRun,
    token: Token,
  };

  interface Props {
    node: TeiNode;
    // The nearest ancestor's resolved urn, used as a fallback when this
    // node's own .urn is null -- mirrors every text_elements/*.html.jinja
    // partial's `child_urn = child.urn if child.urn else text_container.urn`
    // before including a child (Sense.svelte is the one exception, and
    // deliberately passes null here -- see its own comment).
    inheritedUrn?: string | null;
  }

  const { node, inheritedUrn = null }: Props = $props();

  function resolveTagname(n: TeiNode): string {
    const subtype = "subtype" in n ? (n as Record<string, unknown>).subtype : undefined;
    return typeof subtype === "string" && subtype !== "" ? subtype : n.tagname;
  }

  // The dispatched-to component's exact prop type varies (TextRun takes
  // TeiTextRun, everything else takes TeiElement); which one is correct
  // is a runtime fact this lookup can't express statically, so the
  // component (and the node passed to it) are deliberately untyped here.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const Component = $derived((COMPONENTS_BY_TAG[resolveTagname(node)] ?? Span) as any);
  const urn = $derived(
    "urn" in node && (node as Record<string, unknown>).urn !== null
      ? ((node as Record<string, unknown>).urn as string)
      : inheritedUrn,
  );
</script>

<Component {node} {urn} />
