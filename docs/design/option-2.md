# Design Spec — "L'Atelier du Cruciverbiste" (Fusion B — Cahier de Notes)

Approved design direction for all CoCro frontends (Angular, Compose Multiplatform).

---

## Concept

**Fusion B — Cahier de Notes**: the editorial precision of a magazine fused with the tactile warmth of a Séyès school notebook.
Ruled lines in the background, a visible red margin line, cards with dashed borders and offset hard shadows, and a handwritten feel on action buttons.

---

## Palette

| Token | Hex | Usage |
|-------|-----|-------|
| `paper` | `#F5F0E8` | Global background |
| `paperDark` | `#EDE8D0` | Card backgrounds |
| `surface` | `#FFFFFF` | Editorial panels, overlays |
| `surfaceAlt` | `#F7F4ED` | Keyboard background, secondary panels |
| `forest` / Accent | `#2D5A3D` | Primary CTA, active borders, selection |
| `forestLight` | `#7AB89A` | Hover, grid cell selection tint |
| `forestDark` | `#1E3E2A` | Button shadow (primary) |
| `ink` / Text | `#1A1A1A` | Main text, borders, shadows |
| `inkMuted` | `#6B5E4E` | Secondary text, metadata |
| `inkBlue` | `#2C4A8A` | Participant slot 1 cursor |
| `border` | `#1A1A1A` | Magazine separators, card outlines |
| `borderSoft` | `#C8C0A8` | Secondary borders, key borders, input underline |
| `marginLine` | `#E8A090` | Séyès margin line (fixed left: 72px, opacity 0.7, width 2px) |
| `red` | `#B83225` | Errors, danger |
| `gold` | `#C4952A` | Score, premium accent |

---

## Background Treatment

```
body {
  background-color: #F5F0E8;
  background-image:
    repeating-linear-gradient(
      transparent 0px, transparent 23px,
      rgba(191,207,232,0.45) 23px, rgba(191,207,232,0.45) 24px
    ),
    noise-texture (SVG, 5% opacity);
  /* Ruling first, grain second */
}

body::before {
  position: fixed; left: 72px; top: 0; height: 100%;
  width: 2px; background: #E8A090; opacity: 0.7;
}
```

---

## Typography

| Role | Font | Weight | CSS var | CMP token |
|------|------|--------|---------|-----------|
| Section titles | Space Grotesk | 700 | `--font-title` | `FontTitle` |
| CTA Buttons (action) | **Patrick Hand** | 400 | `--font-hand` | `FontHand` |
| Menus / Ghost buttons | Plus Jakarta Sans | 500 | `--font-ui` | `FontUi` |
| Body text / Grid cells | Lexend | 400 | `--font-body` | `FontBody` |
| Small labels | Spline Sans | 400 | `--font-label` | `FontLabel` |

Label style: uppercase, 10–11sp, 2sp letter-spacing (Spline Sans).

**Patrick Hand** is used exclusively on CTA action buttons: primary, secondary, danger.
Ghost buttons remain Plus Jakarta Sans.

---

## Components

### Buttons

- **No `border-radius`** — rectangular only
- **Primary**: forest background, white text, `font-family: Patrick Hand`, `border: 2px solid ink`, `box-shadow: 3px 3px 0 #1a3d26`
  - Hover: `transform: translate(-2px,-2px)`, shadow enlarges to `5px 5px 0 #1a3d26` — no color inversion
- **Secondary**: white background, `border: 2px solid ink`, ink text, `Patrick Hand`, `box-shadow: 3px 3px 0 ink`
  - Hover: `transform: translate(-2px,-2px)`, shadow enlarges to `5px 5px 0 ink` — no color inversion
- **Ghost**: transparent, ink text, **Plus Jakarta Sans**, no shadow
  - Hover: `text-decoration: underline` only
- **Danger**: transparent, `border: 2px solid red`, red text, `Patrick Hand`, `box-shadow: 3px 3px 0 red`
  - Hover: red fill, white text, lift effect

### Cards / Panels

- Background: `paperDark` (`#EDE8D0`)
- Border: `2px dashed var(--color-border)` (no solid borders)
- Shadow: `4px 4px 0 var(--color-ink)` (hard offset)
- Hover: `transform: translate(-2px,-2px)`, shadow grows to `6px 6px 0`
- `::before` pseudo-element: pre-cut top edge (repeating dashed strip on card top)
- `::after` pseudo-element on panels: `content: '✂'` scissors icon, `opacity: 0.4`, rotated 90°, top-right corner

### Separators

1px solid `#1A1A1A` — full width or with horizontal padding.

### Text Fields

- Uppercase label (Spline Sans, 10sp, 2sp letter-spacing)
- **Underline style only**: `border-bottom: 2px solid borderSoft` — no box border
- Focus: `border-bottom-color → forest`
- Error: `border-bottom-color → red`
- Background: transparent

### Custom Keyboard — `CocroKeyboard` (CMP)

AZERTY layout, 3 rows. Wrapper: `border: 2px dashed borderSoft`, `box-shadow: 3px 3px 0 borderSoft`, background `surfaceAlt`.

```
[A][Z][E][R][T][Y][U][I][O][P]
[Q][S][D][F][G][H][J][K][L][M]
[→/↓][W][X][C][V][B][N][⌫]
```

- Direction key: full forest background, white text, `box-shadow: 2px 2px 0 #1a3d26`
- All other keys: white surface, `borderSoft` border, **38dp height**, Lexend 13sp
- Keys: `box-shadow: 2px 2px 0 borderSoft` (not ink-based)
- Hover: `transform: translate(-1px,-1px)`, shadow `3px 3px 0 borderSoft`
- Active: `background: ink`, white text, no shadow

---

## Page Layouts

### Landing (unauthenticated / anonymous)

```
┌──────────────────────────────────────────────────────────────┐
│ CoCro                              [Connexion] [Inscription]  │
│ ─────────────────────────────────────────────────────────── │
│                                                              │
│  L'Atelier du Cruciverbiste    ← Space Grotesk 40sp Bold     │
│  Mots fléchés collaboratifs…   ← Lexend 16sp, inkMuted       │
│                                                              │
│ ─────────────────────────────────────────────────────────── │
│                                                              │
│  [PAPER-DARK CARD, dashed border]  [FOREST PANEL]            │
│  REJOINDRE UNE PARTIE              CRÉER VOTRE ATELIER        │
│  Vous avez un code ?               Concevez, invitez, jouez. │
│  ┌──────────────────┐              [Créer un compte →]       │
│  │ Code ABC123       │             [Déjà un compte ? Login]  │
│  └──────────────────┘                                        │
│  [Rejoindre →]  ← Patrick Hand                               │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Home (authenticated)

Three stacked panels (dashed border, paperDark background, offset shadow):
1. **Card** — `REJOINDRE` — code input + "Rejoindre →"
2. **Forest card** — `LANCER UNE SESSION` — "Nouvelle session →"
3. **Card** — `ÉDITION` — "Éditeur de grille →"

Hero: `Bonjour, {username}` — Space Grotesk 38sp Bold.

### Game Board

```
┌──────────────────────────────────────────────────────────────┐
│ ABC123   ● ● ○ ○           Rév.12       [Quitter]            │
│ ─────────────────────────────────────────────────────────── │
│                                                              │
│  ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐                                     │
│  │ │A│ │ │ │ │ │ │ │ │  ← 44dp cells, no border-radius      │
│  ├─┼─┼─┼─┼─┼─┼─┼─┼─┼─┤  border: 1px #1A1A1A                │
│  │ │ │ │B│ │ │ │ │ │ │  selected: 2px forest + tint         │
│  └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┘                                     │
│                                                              │
│ ─────────────────────────────────────────────────────────── │
│ [→]  Col 3, Ligne 2                           [Effacer]      │
│ ─────────────────────────────────────────────────────────── │
│ [A][Z][E][R][T][Y][U][I][O][P]                               │
│  [Q][S][D][F][G][H][J][K][L][M]                              │
│ [→][W][X][C][V][B][N][⌫]                                     │
└──────────────────────────────────────────────────────────────┘
```

### Lobby (waiting room)

```
SALLE D'ATTENTE       ← Space Grotesk 700, 36sp

CODE DE PARTAGE
┌──────────────────────────────────┐  ← dashed border card
│      A  B  C  1  2  3            │
└──────────────────────────────────┘
[Copier le lien]

Participants (2/4)
──────────────────────────────────
● Alice  (créatrice)   ● connectée    ← nom en Patrick Hand
● Bob                  ● connecté
○ ...                  ○ en attente

Grille : "Weekend" — HARD — 12×12

┌──────────────────────────────────┐
│       DÉMARRER LA PARTIE         │   ← Patrick Hand, forest green, full width, shadow
└──────────────────────────────────┘
```

---

## Participant Cursor Palette (Game Board)

Slot-based color assignment (local player always slot 0):

| Slot | Color | Hex |
|------|-------|-----|
| 0 — local player | Forest green | `#2D5A3D` |
| 1 | Ink blue | `#2C4A8A` |
| 2 | Red | `#B83225` |
| 3 | Gold | `#C4952A` |

**Local player**: 2dp colored border + 10% tint background on selected cell.
Direction indicator: small `›` (H) or `v` (V) glyph, top-right corner of selected cell.
**Remote players**: 9dp colored square, bottom-right corner of their cursor cell.

**Participant names** in lobby/game: displayed in **Patrick Hand** font.

---

## Implementation Notes

- **No `border-radius`** anywhere — rectangular = editorial rigor
- Magazine-style separators: `1px solid #1A1A1A`
- Section labels always uppercase, Spline Sans, 2sp letter-spacing
- Cards: `2px dashed` border + `4px 4px 0 #1A1A1A` hard shadow + hover lift
- Buttons: Patrick Hand on primary/secondary/danger — ghost stays Plus Jakarta Sans
- Inputs: underline only — `border-bottom: 2px solid borderSoft`
- Background: Séyès ruling (24px repeat, rgba blue 45%) first, then grain texture — ruling layer on top
- Margin line: `#E8A090` solid color, `opacity: 0.7`, `width: 2px` at `left: 72px`
