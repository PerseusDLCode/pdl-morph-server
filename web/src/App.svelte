<script lang="ts">
  // Port of morph.html.jinja's content block. Unlike the server-rendered
  // original (a GET form to /morph, re-rendered per request), this keeps
  // word/language/results as client-side state and queries morph.db
  // in-browser via the Phase 2 httpvfs connection.
  import LemmaResult from "./components/LemmaResult.svelte";
  import { getDb } from "./dbConnection.js";
  import { buildMorphResponse } from "./morph/index.js";
  import { parseMorphResponseText } from "./morph/renderText.js";
  import type { MorphResponse } from "./morph/types.js";

  let word = $state("");
  let language = $state("grc");
  let submittedWord = $state<string | null>(null);
  let submittedLanguage = $state<string | null>(null);
  let response = $state<MorphResponse | null>(null);
  let loading = $state(false);
  let error = $state<string | null>(null);

  async function search(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const query = word.trim();
    if (query === "") {
      return;
    }

    loading = true;
    error = null;
    try {
      const db = await getDb();
      const raw = await buildMorphResponse(db, query, language);
      response = parseMorphResponseText(raw);
      submittedWord = query;
      submittedLanguage = language;
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      response = null;
    } finally {
      loading = false;
    }
  }
</script>

<div>
  <form onsubmit={search} class="flex gap-2 mb-4">
    <input
      type="text"
      bind:value={word}
      placeholder="Enter an inflected form…"
      class="input input-bordered grow"
    />
    <select bind:value={language} class="select select-bordered">
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
<footer
  class="footer footer-center sticky bottom-0 bg-base-300 text-base-content p-4"
>
  <p>
    Short definitions courtesy of
    <a
      href="https://logeion.uchicago.edu"
      class="link"
      target="_blank"
      rel="noopener">Logeion</a
    >
  </p>
</footer>
