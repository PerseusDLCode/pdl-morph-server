<script lang="ts">
  // Port of text_elements/note.html.jinja, with one deliberate deviation:
  // the original renders a button whose onclick calls
  // `document.getElementById(note_id).showModal()`, but the matching
  // `<dialog id="note_id">` is only ever collected into a page-level
  // `notes.items` list that no template actually renders anywhere --
  // clicking a note in production today throws (the element doesn't
  // exist). Svelte's per-component encapsulation makes the working
  // version just as simple as porting the broken one, so this renders
  // its own <dialog> right alongside the trigger instead.
  import ReadableTextContainer from "./ReadableTextContainer.svelte";
  import type { TeiElement } from "../../tei/types.js";

  const { node, urn = null }: { node: TeiElement; urn?: string | null } = $props();
  let dialogEl: HTMLDialogElement | undefined;
</script>

<sup class="tei-note">
  <button
    class="tei-note-trigger cursor-pointer text-primary hover:text-primary/80"
    type="button"
    onclick={() => dialogEl?.showModal()}
    aria-label="Show note"
  ></button>
</sup>
<dialog bind:this={dialogEl} class="modal">
  <div class="modal-box">
    {#each node.children as child}
      <ReadableTextContainer node={child} inheritedUrn={urn} />
    {/each}
    <div class="modal-action">
      <form method="dialog">
        <button class="btn">Close</button>
      </form>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop">
    <button>close</button>
  </form>
</dialog>
