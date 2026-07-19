# ORCA Audio Waveform Design Language Specification
**Version 1.0.0 — Official Platform Standard for Native Android**

This document codifies the official visual design guidelines, mathematical proportions, zoom responsiveness, and rendering mechanics for ORCA’s signature audio waveform visualization. Built directly on native Android Hardware-Accelerated Canvases, this specification ensures that the waveform behaves as a precision editing instrument rather than a decorative accessory.

---

## 1. Core Visual Philosophy

In ORCA, the audio waveform is an **editing tool first and foremost**. Visual beauty is a natural byproduct of structural clarity and functional precision.

*   **Honesty & Accuracy:** The waveform represents true acoustic energy. It is not stylized with arbitrary neon curves, simulated equalizer bars, or smoothed-out "aesthetic" waves that distort the location of drum beats, vocals, or silence.
*   **Minimalist Professionalism:** We avoid heavy decorative effects like background glows, volumetric shadows, glassmorphism reflection highlights, or gradients. Pro-grade editors require high-contrast, clean-cut, crisp vectors that keep eye strain to an absolute minimum during 10-hour editing sessions.
*   **Information at a Glance:** The visual system allows an editor to immediately recognize audio events:
    *   **Transients:** Sharp, instantaneous spikes representing drum kicks, snare hits, or video sound effects.
    *   **Phrasing/Envelopes:** Gentle rise-and-fall curves mapping spoken dialogue, breaths, and sentences.
    *   **Silence:** Completely flat horizontal guidelines indicating room tone or dead air, allowing frame-accurate audio trimming.

---

## 2. Geometry & Structural Layout

### Waveform Type: Continuous Filled Envelope
Unlike consumer-level editors that render simple vertical bar charts (which can cause severe screen-space aliasing and visual vibration during high-speed timeline scrolling), ORCA utilizes a **continuous solid filled vector envelope**.

```
   Mono Waveform Geometry:
   
   +-------------------------------------------------------------+  <- Clip Top Boundary
   |  (Generous Top Margin Padding: 10dp)                        |
   |           __/\_     /\_                                     |  <- Upper Envelope Boundary
   |    ______/     \___/   \___/\                               |
   |---|==========================|---------------------------|--|  <- Center Guideline (0dB)
   |    \______     ___     ___  /                               |
   |           \__//   \___/   \/                                |  <- Lower Envelope Boundary
   |  (Generous Bottom Margin Padding: 10dp)                     |
   +-------------------------------------------------------------+  <- Clip Bottom Boundary
```

### Proportions & Padding
*   **Vertical Proportions:** The active waveform peak must occupy **exactly 70% to 80%** of the total height of the clip's card. This ensures that the audio wave has ample room to express dynamic peaks without colliding with the clip boundaries or visual text labels at the top.
*   **Vertical Padding:** A minimum of **10dp (device-independent pixels)** of empty space is maintained at the top and bottom of the wave envelope.
*   **Horizontal Padding:** Waveform rendering is clipped symmetrically at the start and end of the audio card, ensuring that visual lines never spill over onto adjacent clips or tracks.

---

## 3. Stereo vs. Mono Layout Standard

ORCA supports both mono (single channel) and stereo (dual channel) visual representations dynamically.

```
       [ MONO REPRESENTATION ]                   [ STEREO REPRESENTATION ]
   +-----------------------------+           +-----------------------------+
   |                             |           |  L Channel (Upper Half)     |
   |          /\_/\              |           |          _/\_               |
   | --------/-----\------------ |           | --------/----\------------- |
   |         \_/ \_/             |           |         \_/  \_/            |
   |                             |           | =========================== | <- Center Divider
   |                             |           |  R Channel (Lower Half)     |
   |                             |           |           _/\_              |
   |                             |           | --------/-----\------------ |
   |                             |           |         \_/   \_/           |
   +-----------------------------+           +-----------------------------+
```

### Mono Channel Layout
*   A single audio stream is mirrored symmetrically around the horizontal center line of the clip.
*   This center line represents zero decibels (audio silence).

### Stereo Channel Layout
*   The clip height is split into two equal horizontal halves.
*   **Left Channel:** Rendered in the top half, centered symmetrically around its own local center line (at 25% of the total clip height).
*   **Right Channel:** Rendered in the bottom half, centered symmetrically around its own local center line (at 75% of the total clip height).
*   **Center Divider:** A clean, empty horizontal gutter is preserved between the two channels to keep them visually distinct and easy to analyze independently.

---

## 4. Multi-Resolution Zoom (LOD Engine)

To deliver frame-accurate editing performance without exhausting the system battery or lagging when pinch-zooming, ORCA implements a **4-Tier Level of Detail (LOD)** standard.

```
   [LOD 0] Zoomed Out (0.0x - 0.3x)
   - Highly consolidated peak-preserving envelopes (150 points).
   - Emphasizes the macro-energy of the track (verse, chorus, intro, outro).
   
   [LOD 1] Medium Zoom (0.3x - 1.0x)
   - Balanced representation (450 points).
   - Reveals general vocal structures and musical phrases.

   [LOD 2] High Zoom (1.0x - 3.0x)
   - High detail (1200 points).
   - Showcases sharp transients, drum hits, and syllables clearly.

   [LOD 3] Maximum Zoom (3.0x+)
   - High-fidelity, sample-accurate peak detail (3600 points).
   - Designed for precision splicing, audio lip-syncing, and fade-in/fade-out editing.
```

### Seamless Detail Morphing (Anti-Pop)
As the user pinch-zooms the timeline, the rendering engine must **linearly blend (interpolate)** between adjacent LOD levels (e.g., morphing from LOD 1 to LOD 2). This guarantees a completely fluid visual transition with absolutely zero sudden pops, jumps, or layout recalculations.

---

## 5. Official Color & State System

The visual system uses color to communicate the **functional purpose** of the audio track. The geometry remains strictly identical, maintaining visual consistency across the entire workspace.

### Core Accent Palette

| Track Type | Intended Use | Hex Color Value | Visual Vibe |
| :--- | :--- | :--- | :--- |
| **Music** | Background scores, soundtracks, songs | `#9061F9` (Purple) | Energetic, creative, structured |
| **Voice** | Dialogues, narrations, voiceovers, podcasts | `#06B6D4` (Cyan) | Clean, highly legible, speech-focused |
| **SFX** | Foley, sound effects, impacts, transition sweeps | `#F97316` (Orange) | Alert, punchy, transient-heavy |
| **Ambient** | Background room tone, wind, rain, outdoor noise | `#10B981` (Green) | Subtle, calm, low-amplitude |

### Interactive States

*   **Normal Clip:** The waveform envelope is rendered with soft, professional transparency (**53% opacity / `#88` alpha**) to sit elegantly behind text titles and playhead lines.
*   **Selected Clip:**
    *   Waveform opacity is increased to **90% (`#E6` alpha)**.
    *   A crisp, high-contrast, fully opaque (**100% / `#FF` alpha**) outline in a brighter version of the track color is rendered around the solid envelope.
    *   *The geometry and shape of the wave remain perfectly identical.*
*   **Muted Clip:** The waveform opacity is dropped to a very low level to signify silence, without altering the underlying shape or color of the waveform.

---

## 6. High-Performance Engineering Directives

To maintain a locked **120 frames per second (FPS)** refresh rate on modern high-end Android displays, the following technical constraints are strictly enforced in our native drawing modules:

1.  **Zero Memory Allocation in Draw Loop:** Allocating objects (such as `new Paint()`, `new Path()`, or even local helper coordinate floats) inside the `onDraw()` or rendering loop is strictly forbidden. All drawing tools must be pre-allocated once during initialization.
2.  **Viewport Virtualization:** Only the section of the audio clip that is actively visible inside the viewport bounds is calculated and rendered. Off-screen portions of long audio files are completely discarded from the graphics pipeline.
3.  **Hardware-Accelerated Path Caching:** The vector path structures are drawn using hardware-accelerated rendering. Re-triangulating paths is avoided; coordinate sweeps are performed using static, pre-allocated vertex arrays.
4.  **Static Global Alignment:** The sampling step is aligned to absolute global timeline coordinates rather than local clip offsets. This prevents "shimmering" or pixel jitter artifacts when dragging or scrolling.
