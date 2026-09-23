<script lang="ts">
  // Port of the "Entries" <details> block in morph.html.jinja.
  import ReadableTextContainer from "./text_elements/ReadableTextContainer.svelte";
  import type { EntryOut } from "../morph/types.js";

  const { entries }: { entries: EntryOut[] } = $props();
</script>

<details class="border border-neutral-200 rounded">
  <summary class="bg-neutral-50 cursor-pointer font-medium px-3 py-1 text-neutral-700 text-sm">
    Entries
    <span class="font-normal text-neutral-400"
      >({entries.length} {entries.length !== 1 ? "lexica" : "lexicon"})</span
    >
  </summary>
  <div class="px-3 py-2 space-y-1">
    {#if entries.length === 0}
      <p class="text-neutral-400 text-sm italic">No lexicon entries available.</p>
    {:else}
      {#each entries as entry}
        <details>
          <summary class="cursor-pointer font-medium text-neutral-600 text-sm"
            >{entry.document_id}</summary
          >
          <div class="mt-1 overflow-auto p-2 text-sm">
            {#if typeof entry.text === "string"}
              {entry.text}
            {:else}
              {#each entry.text ?? [] as element}
                <ReadableTextContainer node={element} />
              {/each}
            {/if}
          </div>
        </details>
      {/each}
    {/if}
  </div>
</details>
