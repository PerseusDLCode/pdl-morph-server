<script lang="ts">
  // Port of morph.html.jinja's content block. As in the server-rendered
  // original, the lookup is driven by /morph's query params (word,
  // language, document_id, prior_word) and the form is a plain GET form
  // back to /morph -- SvelteKit intercepts its submission as a client-side
  // navigation, and the query runs against morph.db in-browser via the
  // httpvfs connection instead of on a server.
  import { page } from "$app/state";
  import LemmaResult from "../../components/LemmaResult.svelte";
  import { getDb } from "../../dbConnection.js";
  import { buildMorphResponse } from "../../morph/index.js";
  import { parseMorphResponseText } from "../../morph/renderText.js";
  import type { MorphResponse } from "../../morph/types.js";

  const params = $derived(page.url.searchParams);
  const submittedWord = $derived(params.get("word")?.trim() || null);
  const submittedLanguage = $derived(params.get("language") ?? "grc");
  const documentId = $derived(params.get("document_id") ?? undefined);
  const priorWord = $derived(params.get("prior_word") ?? undefined);

  // The form's own state, reset from the URL on every navigation (e.g.
  // back/forward) but otherwise free to be edited before submitting.
  let word = $state("");
  let language = $state("grc");
  $effect(() => {
    word = submittedWord ?? "";
    language = submittedLanguage;
  });

  let response = $state<MorphResponse | null>(null);
  let loading = $state(false);
  let error = $state<string | null>(null);

  $effect(() => {
    const query = submittedWord;
    const lang = submittedLanguage;
    const options = { documentId, priorWord };

    response = null;
    error = null;
    if (query === null) {
      loading = false;
      return;
    }

    // A newer navigation may start before this lookup finishes; only the
    // latest one gets to write its result.
    let stale = false;
    loading = true;
    (async () => {
      try {
        const db = await getDb();
        const raw = await buildMorphResponse(db, query, lang, options);
        if (!stale) {
          response = parseMorphResponseText(raw);
        }
      } catch (err) {
        if (!stale) {
          error = err instanceof Error ? err.message : String(err);
        }
      } finally {
        if (!stale) {
          loading = false;
        }
      }
    })();

    return () => {
      stale = true;
    };
  });
</script>

<svelte:head>
  <title>{submittedWord ? `${submittedWord} – ` : ""}Morpheus</title>
</svelte:head>

<div>
  <form action="/morph" method="get" class="flex gap-2 mb-4">
    <!-- svelte-ignore a11y_autofocus -->
    <input
      type="text"
      name="word"
      bind:value={word}
      placeholder="Enter an inflected form…"
      autofocus
      class="input input-bordered grow"
    />
    <select name="language" bind:value={language} class="select select-bordered">
      <option value="grc">Greek</option>
      <option value="lat">Latin</option>
    </select>
    <button
      type="submit"
      class="btn bg-red-800 hover:bg-red-700 text-neutral-50">Search</button
    >
  </form>

  {#if loading}
    <p class="text-neutral-500 italic">Searching…</p>
  {/if}
  {#if error}
    <p class="text-red-600">{error}</p>
  {/if}

  {#if submittedWord !== null && response !== null}
    <h1 class="text-xl font-bold mb-4">
      {submittedWord}
      <span class="font-normal text-neutral-500">({submittedLanguage})</span>
    </h1>

    {#if response.lemmas.length === 0}
      <p class="text-neutral-500 italic">No results found.</p>
    {/if}

    {#each response.lemmas as lemma}
      <LemmaResult {lemma} />
    {/each}
  {/if}
</div>
