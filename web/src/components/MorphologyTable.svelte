<script lang="ts">
  // Port of the "Morphology" <details> block + morph_details macro in
  // morph.html.jinja.
  import type { ParseOut } from "../morph/types.js";

  const { parses }: { parses: ParseOut[] } = $props();

  const FEATURE_ORDER: Array<keyof ParseOut> = [
    "grammatical_case",
    "number",
    "gender",
    "tense",
    "mood",
    "voice",
    "person",
    "degree",
    "dialect",
    "other",
  ];

  function morphDetails(parse: ParseOut): string {
    return FEATURE_ORDER.map((key) => parse[key])
      .filter((value): value is string => Boolean(value))
      .join(" · ");
  }
</script>

<details class="border border-base-300 rounded bg-base-100" open>
  <summary class="bg-base-200 cursor-pointer font-medium px-3 py-1 text-neutral-700 text-sm">
    Morphology
    <span class="font-normal text-neutral-400"
      >({parses.length} parse{parses.length !== 1 ? "s" : ""})</span
    >
  </summary>
  <table class="min-w-full text-sm">
    <thead>
      <tr class="border-b border-base-300 text-neutral-500 text-xs uppercase">
        <th class="px-3 py-1 text-left">Form</th>
        <th class="px-3 py-1 text-left">POS</th>
        <th class="px-3 py-1 text-left">Details</th>
      </tr>
    </thead>
    <tbody>
      {#each parses as parse}
        <tr class="border-b border-base-200 {parse.is_winner ? 'bg-success/10' : ''}">
          <td class="px-3 py-1">
            <span>{parse.form}</span>
            {#if parse.is_winner}
              <span class="text-success text-xs" title="Most likely parse"> ✓</span>
            {/if}
          </td>
          <td class="px-3 py-1">{parse.part_of_speech}</td>
          <td class="px-3 py-1 text-neutral-600 text-sm">{morphDetails(parse)}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</details>
