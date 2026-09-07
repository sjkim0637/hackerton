# ImageGen Prompt 기록

모든 이미지는 Codex 내장 `image_gen`으로 생성했다. 공통 Palette는 `#1660FF`, `#0B1736`,
`#38D6D0`, `#F2EDE4`, `#FFFFFF`이며 이미지 내부 Text, Logo, Watermark를 금지했다.

## `app-icon-master.png`

```text
Use case: logo-brand
Asset type: Android app icon master for an AR interior design app
Primary request: an original minimal symbol combining an open room corner frame, a small sofa silhouette, and one precise augmented-reality placement point
Style/medium: vector-friendly flat geometric logo rendered as a polished square raster asset
Composition/framing: single centered symbol, bold silhouette, generous safe padding, readable at 48 pixels
Color palette: primary electric blue #1660FF, deep navy #0B1736, spatial cyan #38D6D0, white
Constraints: no text, no letters, no gradients unless extremely subtle, no mockup device, no border, no watermark, no resemblance to existing brand logos
```

## 내부 AR 화면 Asset

아래 5종은 기존 `ar-interior-card.png`를 시각 Reference로 포함해 생성했다.

### `internal-scan-guide.png`

```text
Create a polished square mobile-app UI illustration asset for an AR interior app, matching the visual language of the provided reference: bright warm-white modern room, cobalt blue accents, subtle cyan AR graphics, premium soft 3D realism, clean and approachable. Depict the scanning state: a smartphone camera perspective facing a simple empty living-room corner, with elegant cyan plane-detection grid points flowing across the floor and one wall, plus a centered circular scan reticle. Keep the composition simple and readable at small size, with generous pale neutral negative space around the subject. No people, no hands, no phone frame, no logos, no letters, no numbers, no text, no watermark. Solid warm-white background, no transparency or checkerboard.
```

### `internal-object-selection-guide.png`

```text
Create a polished square mobile-app UI illustration asset for an AR interior app, matching the visual language of the provided reference: bright warm-white modern room, cobalt blue accents, subtle cyan AR graphics, premium soft 3D realism, clean and approachable. Depict object-region selection: a cobalt blue lounge chair against a light neutral wall, surrounded by a crisp cyan rectangular selection box with four clear corner handles and a subtle measurement line, as if the user dragged a bounding box around the chair. Keep the scene uncluttered and highly legible at small size. No people, no hands, no phone frame, no logos, no letters, no numbers, no text, no watermark. Solid warm-white background, no transparency or checkerboard.
```

### `internal-removal-result.png`

```text
Create a polished square mobile-app UI illustration asset for an AR interior app, matching the visual language of the provided reference: bright warm-white modern room, cobalt blue accents, subtle cyan AR graphics, premium soft 3D realism, clean and approachable. Depict an object-removal before-and-after result without any labels: one continuous room scene divided softly down the middle; on the left a small cobalt blue side cabinet stands against the wall, on the right the same wall and floor are seamlessly clean and empty. Add a restrained cyan transition sparkle at the center divider. Make the difference immediately understandable at small size. No people, no hands, no phone frame, no logos, no letters, no numbers, no text, no watermark. Solid warm-white background, no transparency or checkerboard.
```

### `internal-placement-guide.png`

```text
Create a polished square mobile-app UI illustration asset for an AR interior app, matching the visual language of the provided reference: bright warm-white modern room, cobalt blue accents, subtle cyan AR graphics, premium soft 3D realism, clean and approachable. Depict interactive furniture placement: a cobalt blue armchair hovering just above a cyan circular AR floor target in a bright empty room, with three minimal visual control cues around it—curved rotation arrow, diagonal scale handles, and a short drag-motion trail—drawn only as clean cyan symbols. Keep controls spacious and readable, with no interface panel. No people, no hands, no phone frame, no logos, no letters, no numbers, no text, no watermark. Solid warm-white background, no transparency or checkerboard.
```

### `internal-catalog-empty.png`

```text
Create a polished square mobile-app UI empty-state illustration for an AR interior furniture catalog, matching the visual language of the provided reference: bright warm-white, cobalt blue accents, subtle cyan AR graphics, premium soft 3D realism, clean and approachable. Depict a neat open catalog tray or shelving unit containing three simple furniture miniatures—a cobalt blue chair, a pale wood side table, and a small green plant—with a subtle cyan placement target beside them. Centered compact composition suitable for a bottom-sheet empty or loading state, generous negative space. No people, no hands, no phone frame, no shopping symbols, no logos, no letters, no numbers, no text, no watermark. Solid warm-white background, no transparency or checkerboard.
```

## `home-hero.png`

```text
Use case: stylized-concept
Asset type: portrait mobile home-screen hero background
Primary request: a calm contemporary living room being transformed by augmented reality, one side tangible warm interior and the other side expressed as precise translucent spatial planes and placement guides
Scene/backdrop: uncluttered modern living room with sofa, low table, shelf, and a clean wall
Style/medium: premium soft 3D editorial illustration, practical product UI asset rather than cinematic concept art
Composition/framing: portrait composition, focal room in middle-lower area, generous clean negative space in upper third and lower edge for native UI
Lighting/mood: warm daylight, confident and approachable
Color palette: warm neutral #F2EDE4 surfaces, deep navy shadows, primary blue #1660FF and cyan #38D6D0 AR accents
Constraints: no people, no phones, no text, no buttons, no logos, no watermark, no dense tiny details
```

## `ar-interior-card.png`

```text
Use case: stylized-concept
Asset type: feature card illustration for AR interior entry
Primary request: a sofa and side table anchored onto a detected floor plane inside a room, with one clear circular AR placement reticle and restrained spatial grid lines
Scene/backdrop: simplified corner of a contemporary living room
Style/medium: premium soft 3D product illustration with clean shapes and subtle depth
Composition/framing: compact centered cluster with generous outer padding, strong silhouette suitable for a rounded mobile card crop
Lighting/mood: bright, clean, capable
Color palette: primary blue #1660FF, spatial cyan #38D6D0, deep navy #0B1736, warm neutral #F2EDE4, white
Constraints: no text, no UI buttons, no device frame, no people, no logos, no watermark, only one reticle
```

## `guide-flow-hero.png`

```text
Use case: scientific-educational
Asset type: explanation-page hero illustration
Primary request: visually explain the app flow in three connected visual stages without words: scan a room plane, select a furniture object region, then place a redesigned furniture item in AR
Scene/backdrop: clean neutral canvas with three floating room vignettes connected by a subtle continuous path
Style/medium: polished soft 3D educational illustration, simple and immediately understandable
Composition/framing: horizontal sequence that can crop safely inside a portrait mobile page, balanced whitespace around all stages
Lighting/mood: clear, friendly, instructional
Color palette: primary blue #1660FF, spatial cyan #38D6D0, deep navy #0B1736, warm neutral #F2EDE4
Constraints: no text, no numbers, no arrows with labels, no phone mockup, no people, no logos, no watermark
```

## `settings-header.png`

```text
Use case: stylized-concept
Asset type: compact settings-page header illustration
Primary request: an abstract spatial room frame with three elegant control sliders representing server connection, AR tracking, and visual preferences
Scene/backdrop: clean airy neutral background
Style/medium: premium soft 3D product illustration with minimal geometric controls
Composition/framing: wide compact header, centered motif, large clean margins, suitable for cropping to a shallow mobile banner
Lighting/mood: precise, calm, trustworthy
Color palette: deep navy #0B1736, primary blue #1660FF, spatial cyan #38D6D0, warm neutral #F2EDE4, white
Constraints: no text, no gear icon cliché, no device mockup, no logos, no watermark
```
