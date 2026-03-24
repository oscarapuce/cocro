# CoCro Design Charter

Reference charter for agents and contributors working on the current CoCro frontend in `cocro-angular`.

This document describes the design system that is actually implemented today. It is prescriptive: new UI work should reinforce this system instead of introducing a parallel one.

## Intent

CoCro uses a hybrid visual language:

- editorial composition for structure and hierarchy
- school notebook cues for warmth and recognizability
- game-like tactility for interactions

The result should feel precise, playful, and legible, not glossy, minimal, or generic SaaS.

## Core Principles

1. Keep the notebook atmosphere visible.
The paper background, blue ruling, red margin line, dashed borders, and offset shadows are not decorative extras. They are the brand signature.

2. Prefer structured contrast over decorative density.
Most screens work because they contrast light paper panels against a single dark forest panel. Preserve that contrast before adding more ornaments.

3. Use typography as hierarchy, not color alone.
`Space Grotesk` carries headlines, `Lexend` carries reading text, `Plus Jakarta Sans` handles UI, and `Patrick Hand` is reserved for action emphasis.

4. Make interaction tactile.
Primary actions should feel like printed cards being pushed on paper: hard shadows, small lift on hover, crisp edges.

5. Stay inside the shared token system.
Prefer tokens from [`_tokens.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/styles/_tokens.scss) before introducing ad hoc colors, radii, spacing, or shadows.

6. Keep the design docs in sync.
When a component, several components, or the overall system changes visually, update this charter and [`cocro-design-book.html`](/Users/oscar_mallet/Documents/cocro/docs/design/cocro-design-book.html) whenever the guidance or the visual references need to change.

## Brand Vocabulary

- Background: warm paper with notebook ruling
- Accent: forest green
- Structure: black ink and dashed separators
- Tone: collaborative workshop, not arcade, not enterprise dashboard
- Motion: short and tactile, never floaty

## Tokens To Reuse

### Colors

- `--color-paper`: global background (#F0EDE5)
- `--color-surface`: primary card surface (#FFFFFF)
- `--color-surface-alt`: subtle hover/secondary surface (#F7F4ED)
- `--color-ink`: main text and hard outlines (#131313)
- `--color-ink-muted`: secondary text (#6B5E4E)
- `--color-forest`: primary accent and dark panel (#255736)
- `--color-croco-strong`: active state accent (#849E00)
- `--color-croco-tint`: active state background fill
- `--color-red`: errors and destructive actions (#B83225)
- `--color-margin-line`: notebook margin marker

### Typography

- `--font-title`: `Space Grotesk`
- `--font-body`: `Lexend`
- `--font-ui`: `Plus Jakarta Sans`
- `--font-hand`: `Patrick Hand`
- `--font-label`: `Spline Sans`
- `--font-grid`: `Comic Neue` (grid cell letters only)

### Spacing

Use the `--space-*` scale. Available values: `--space-2xs` (2px), `--space-xs` (4px), `--space-sm` (8px), `--space-md` (16px), `--space-lg` (24px), `--space-xl` (40px), `--space-2xl` (64px), `--space-3xl` (96px). Avoid magic values unless there is a hard layout constraint.

### Shadows and Borders

- `--border-ink`
- `--border-soft`
- `--border-dashed`
- `--shadow-card`
- `--shadow-sm`
- `--shadow-panel`

## Layout Rules

### Page Shell

- Preserve the full-page paper background from [`styles.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/styles.scss).
- Favor centered content columns with generous horizontal breathing room.
- Use horizontal rules to segment major blocks.
- Prefer two-panel compositions for landing and home.
- Landing and home share the same visual shell on the default route (`/`). The route adapts its actions based on authentication state rather than showing two separate screens.

### Panels

- Light panels use `--color-surface`
- Dark panels use `--color-forest`
- Dashed border is the default structural edge
- Offset shadows should stay short and restrained
- Rounded corners are part of the current system
- Adjacent panels should feel related but clearly separated

### Responsive Behavior

- On narrow screens, stacked panels are preferred to compressed side-by-side panels
- Avoid hiding notebook cues on mobile unless readability forces it
- Keep CTA buttons full-width inside forms and cards

## Component Rules

### Buttons

Use [`button.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/shared/components/button/button.component.scss) as the source of truth.

- `primary`: forest fill, handwritten emphasis, hard shadow
- `secondary`: light surface with dashed border and green shadow
- `ghost`: transparent utility action
- `danger`: red outlined destructive action
- `size="sm"`: compact variant (`min-height: 32px`) for use inside dense toolbars and params bars
- buttons should feel slightly rounded and playful, not rigidly rectangular

Do not create screen-specific button aesthetics when an existing variant can be extended.

### Inputs

Use [`input.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/shared/components/input/input.component.scss).

- Labels are uppercase and compact
- Input background stays light and paper-adjacent (`--color-input-bg`)
- Error state uses red border and italic helper text

Prefer the shared `cocro-input` over native `input` when the field is part of standard form UX.

### Cards

Use [`card.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/shared/components/card/card.component.scss).

- Dashed border
- Short offset shadow
- Lift on hover only when the card is interactive or benefits from tactility
- `title` input: renders a small forest-green badge on the top dashed border (fieldset-legend style). Use for all named tool panels in the editor.
- `dark` input: forest-green background, white text. The global `styles.scss` override propagates this to ng-content children.

### Toggle Buttons

The `.toggle-btn` class is a **global utility** defined in `styles.scss`. Use it directly in templates without duplicating the definition in component SCSS files.

- Base: dashed border, surface-alt background, ink text, smooth transitions
- `.active`: croco-tint fill, croco-strong border, ink text
- `:disabled`: opacity preserved, cursor changed — always used together with `.active` on the currently selected item
- Component SCSS should only add **local overrides**: `flex: 1` for row layouts, `min-height` adjustments, or nested element rules

### Navigation

Navigation should look editorial and lightweight:

- brand on the left
- compact actions on the right
- horizontal divider below

Avoid introducing app bars with unrelated visual treatment.

### Tool Navigation

Protected tool routes such as grid editing, lobby creation, or game-oriented workspaces use a collapsible left sidebar instead of the top header. The default route (`/`) also uses this sidebar.

- keep the sidebar narrow and structural
- include a burger to collapse or expand it
- when collapsed, leave the burger visible but hide the sidebar itself
- the sidebar does not auto-collapse on navigation — the user controls collapse explicitly
- keep the work surface as the visual priority
- avoid creating a second right-heavy navigation layer that competes with the editor tools

## Screen-Specific Guidance

### Landing (default route `/`)

This screen is the visual reference for the product and handles both anonymous and authenticated states:

- large editorial hero
- join flow in a light panel
- create/editor flow in a dark forest panel
- restrained copy, strong CTA contrast
- authenticated users see editor and session actions instead of auth links

### Auth

Login and register are simpler, centered compositions.

- preserve the same paper context
- keep a strong brand mark at the top
- use a single focused card
- keep form actions full-width

### Grid Editor

The grid editor uses a three-zone layout:

- **Params bar** (top): horizontal `cocro-card` with title badge, containing grid name, row/column controls, and the submit button
- **Side panel** (left): stacked `cocro-card` panels with title badges — cell type selector, then contextual letter or clue content editor
- **Grid workspace** (right): the crossword grid, taking the remaining space

All tool panels use `cocro-card` with the `title` input. All toggle controls use the global `.toggle-btn` utility. Component SCSS files only contain local layout overrides.

### Grid Player (Play View)

The play view mirrors the editor's three-zone layout but replaces all editing controls with read-only information:

- **Params bar** (top): `cocro-card` with title badge "Session de jeu", split into two zones:
  - Left: grid title, author, difficulty badge (forest green), reference
  - Right: share code, participant count, revision, connection indicator, leave button
- **Side panel** (left, 18rem): `cocro-card` with title badge "Informations" containing:
  - Optional description (italic, muted)
  - Global clue section (forest green accent, word lengths)
  - Selected clue cell content (when a clue cell is focused)
- **Grid workspace** (right): `cocro-global-clue-preview` (if global clue exists) then `cocro-grid`

The play view reuses `GridSelectorService` (singleton), `GridComponent`, and `GlobalCluePreviewComponent` from the editor. Letter coloring uses `cellColorClassFn` callback: `letter--mine` (forest green) and `letter--other` (sepia/brown) for multi-player awareness.

Route: `/play/:shareCode` — accessible via `authGuard`.

## Anti-Patterns

Do not introduce:

- glossy gradients unrelated to the notebook/editorial theme
- soft blurred shadows as the primary depth system
- default browser blue focus treatments without token styling
- new fonts
- arbitrary radii or pill shapes
- separate one-off color palettes per feature
- inline visual styles in templates when a component class can carry them
- duplicated `.toggle-btn` definitions in component SCSS files (use the global utility)

## Current Weak Points To Keep In Mind

1. The shared header component is visually older than the landing navigation and should not be used as the target style for new screens.
2. The lobby and auth forms contain some duplicated structural patterns (card container, form layout, error message) that could be consolidated into shared utility classes.

## Definition Of Done For New UI

A frontend change is visually acceptable when:

- it uses existing tokens and component variants first
- it preserves the notebook/editorial identity
- it remains readable on mobile
- it does not create a second design language inside the app
- its interactive states are explicit
- its spacing and typography feel consistent with landing and auth
- the charter and book are updated if the design system changed

## Reference Files

- [`styles.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/styles.scss)
- [`_tokens.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/styles/_tokens.scss)
- [`_typography.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/styles/_typography.scss)
- [`landing.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/features/landing/landing.component.scss)
- [`button.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/shared/components/button/button.component.scss)
- [`input.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/shared/components/input/input.component.scss)
- [`card.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/shared/components/card/card.component.scss)
- [`grid-editor.component.scss`](/Users/oscar_mallet/Documents/cocro/cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.scss)
