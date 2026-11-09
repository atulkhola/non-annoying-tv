---
name: Non-Annoying TV
description: A full-screen trailer is the home screen, and speech reshapes it.
---

# Non-Annoying TV design

`design/SPEC.md` is the full system: grid, type, colour, glass, motion, sound, components, screens and edge cases. `design/tokens.json` holds every value. This file only records the point of view.

## Overview

The picture leads. A trailer or backdrop fills the screen, and the interface steps back so the room feels like a cinema with the lights down. Speech is the shortest way in: say what you feel like, in your own words, and the screen reshapes around it.

## Rules

- **Grid first.** Every position comes from the Apple TV safe area, 40 gutters and the 12-column base. If a value breaks the grid, the token build fails.
- **Whole Geist family, one job each.** Sans for structure, Serif for voice, Mono for facts, Pixel for system moments.
- **The voice colour means heard.** It is only for what was said or understood. Never for focus, selection or badges.
- **Glass for controls, not content.** The top chip, pills, details panel and handoff veil are glass. Cards, logos and text on the picture stay clear.
- **Focus is light, not a ring.** Focused cards grow and catch a sheen. Focused items sit on a pane of frosted glass that travels between them. No solid focus blocks.
- **Everything moves on a spring.** A held key retargets smoothly and never restarts.
- **Sound has a place.** Pan follows the row, pitch follows depth, and it stays quiet.
- **Original work.** The feel is close to tvOS, but no Apple assets or sounds are used.
- **Never smaller than 26** on the 1920 by 1080 canvas.
