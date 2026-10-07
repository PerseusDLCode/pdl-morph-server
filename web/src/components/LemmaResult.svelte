<script lang="ts">
  // Port of the per-lemma <details> block in morph.html.jinja.
  import EntriesList from "./EntriesList.svelte";
  import MorphologyTable from "./MorphologyTable.svelte";
  import ReadableTextContainer from "./text_elements/ReadableTextContainer.svelte";
  import type { LemmaResult as LemmaResultData } from "../morph/types.js";

  const { lemma, language }: { lemma: LemmaResultData; language: string } = $props();

  // MinimumViablePerseus's corpus search, on the same host: every occurrence
  // of this lemma's forms across the Greek and Latin texts. It identifies the
  // lemma by headword + sequence number (lemmas.id isn't stable across
  // morph.db rebuilds) and reads its forms from this app's morph.db.
  const corpusSearchHref = $derived(
    `/search/?${new URLSearchParams({
      lemma: lemma.headword,
      seq: String(lemma.sequence_number),
      lang: language,
    })}`,
  );
</script>

<details class="border border-base-300 rounded mb-2 overflow-hidden" open>
  <summary class="bg-base-300 cursor-pointer font-semibold px-4 py-2 text-base-content">
    <span>{lemma.headword}</span>
    {#if lemma.sequence_number > 1}
      <span class="text-neutral-400">({lemma.sequence_number})</span>
    {/if}
    {#if lemma.document_frequency !== null}
      <span class="font-normal text-neutral-400 text-sm"
        >— {lemma.document_frequency.toFixed(0)}%</span
      >
    {/if}
    {#if lemma.short_definition}
      <span class="font-normal text-neutral-600 text-sm">
        {#if typeof lemma.short_definition === "string"}
          {lemma.short_definition}
        {:else}
          {#each lemma.short_definition as element}
            <ReadableTextContainer node={element} />
          {/each}
        {/if}
      </span>
    {/if}
  </summary>

  <div class="px-4 py-2 space-y-2">
    <a href={corpusSearchHref} class="link text-sm">Find in corpus</a>
    {#if lemma.parses.length > 0}
      <MorphologyTable parses={lemma.parses} />
    {/if}
    <EntriesList entries={lemma.entries} />
  </div>
</details>
