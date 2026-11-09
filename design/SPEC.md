# Non-Annoying TV: design system and build spec

A personal, tvOS-feel home screen for Google TV. This spec covers the grid, tokens, components, screens, edge cases, glass, motion and sound.

## How the system stays in sync

`design/tokens.json` is the single source of truth. `build_tokens.py` checks the grid maths, then writes the Android `Tokens.java`. The build fails if a value breaks the grid. Change a colour, size or spring there, run the script, and install.

## Grid

The canvas is 1920 by 1080.

| **Value** | **Size** | **Source** |
|---|---|---|
| Safe area | 60 top and bottom, 80 left and right | Apple HIG |
| Horizontal spacing | 40 | Apple HIG |
| Minimum vertical spacing | 100 | Apple HIG |
| Four-up card width | 410 | Apple HIG column widths |
| 12-column base | 110 column, 40 gutter, 1760 content | Derived from the Apple values, not published by Apple |
| Row step | 370, so 140 between rows | Ours, above Apple's minimum |

Four cards at 410 fill the content width exactly. When a row scrolls, 40 of the next and previous cards show on each side, so hidden content stays symmetrical as Apple asks.

Spacing steps are 10, 20, 30, 40, 60, 80 and 100. Every line height is a multiple of 10.

### Vertical positions

| **Element** | **Home** | **Browsing** |
|---|---|---|
| Info bottom edge | 690 | 340 |
| Row title top | 730 | 380 |
| Cards top | 790 | 440 |
| Logo limit | 600 by 200 | 440 by 120 |

## Type

The whole Geist family, one job each.

| **Family** | **Job** | **Used for** |
|---|---|---|
| Geist Sans | Structure | Row titles, pills, tokens, reasons, overviews |
| Geist Serif | Voice | Said words and titles without logos |
| Geist Mono | Facts | Meta, service and card labels, all uppercase |
| Geist Pixel | System | Prompt and handoff in Pixel Circle, clock in Pixel Line |

| **Role** | **Size / line** | **Tracking** | **Clamp** |
|---|---|---|---|
| Said | 90 / 100 | 0 | 4 lines |
| Title | 80 / 90 | 0 | 2 lines |
| Title small | 60 / 70 | 0 | 1 line |
| Reason | 40 / 50 | -2% | 2 lines |
| Reason small | 30 / 40 | 0 | 1 line |
| Overview | 30 / 40 | 0 | 4 lines |
| Handoff | 40 / 50 | 0 | None |
| Clock | 32 / 40 | 0 | None |
| Row title, pill, token | 30 / 40 | 0 | 1 line |
| Prompt | 28 / 40 | 0 | 1 line |
| Meta, service | 26 / 40 | 0 | 1 line |
| Card label | 26 / 30 | 0 | 1 line |

Nothing is smaller than 26 on the 1080 canvas.

## Colour

| **Token** | **Value** | **Use** |
|---|---|---|
| room | #07080b | The dark behind everything |
| ink | #f4f1ea | Primary text, focused pills |
| soft | ink at 70% | Secondary text |
| voice | #d9ffb9 | Only what was heard or understood |
| mark | #f4f1ea | The mark's body |
| markDark | #b9b5ab | The mark's scan bar |
| prompt | ink at 30% | The mic prompt in the top chip |
| well | #15171d | Empty art |

## Glass

Glass is for controls and navigation. Content stays clear. This follows Apple's own boundary for materials.

| **Surface** | **Tint** | **Notes** |
|---|---|---|
| Top chip | Smoke at 45% | Top right, always present |
| Resting pills | Smoke at 45% | Focused pills sit on the selection |
| Selection | Ink at 14% | Frosted pane with a 2px edge of ink at 40%. It extends 10 past the focused item |
| Details panel | Room at 70% | Darker, because it holds reading text |
| Handoff veil | Room at 55% | Full screen |

Every surface has a 1px edge lit from above, a 40 blur and 150% saturation. Cards, logos and text on the picture never get glass.

### How the TV does it

This TV has no system window blur, and a video surface can't be sampled live. So the picture behind the UI is shrunk to 240 by 135, saturated and blurred once on a background thread. Each glass surface paints its own slice of that small bitmap with one shader draw, so nothing blurs per frame.

During a trailer the bitmap refreshes twice a second from a low-resolution PixelCopy. Refraction isn't used, because the TV doesn't draw refraction.

## Motion

Everything that moves uses a spring, so a held key retargets smoothly from the current position and speed.

| **Spring** | **Stiffness / damping** | **Use** |
|---|---|---|
| focus | 439 / 0.65 | Gaining focus, with a little overshoot |
| unfocus | 247 / 0.85 | Losing focus, slower and calmer |
| slide | 260 / 0.92 | Rows and info moving |
| reveal | 180 / 1.0 | Words, replies and picks arriving |
| lead | 620 / 0.72 | The edge of the selection moving toward its target |
| trail | 300 / 0.78 | The far edge of the selection, so it stretches in flight |
| morph | 210 / 0.8 | A card growing into the screen and returning to its place |

Focus runs faster than unfocus because Apple documents that pattern. Apple doesn't publish its spring or scale values, so these numbers are our own starting points.

### Transitions

- **Selection:** one pane of frosted glass travels from item to item. Its leading edge moves first and the far edge follows, so it stretches and settles with a little give. Going from a card to a pill, it takes the new shape on the way.
- **Focus:** the card scales 1.1 outward and a sheen sweeps across it once. Neighbours don't move.
- **Walking focus:** focus walks across the four visible columns. The row only moves once focus reaches the edge of them.
- **Open and back:** the focused card grows into the full screen. Back returns it to the exact slot it came from, with a small overshoot.
- **Edges:** with nowhere to go, the selection leans that way and comes back.
- **Rack focus:** the picture goes soft while browsing rows, then comes sharp when a trailer takes over.
- **Title change:** the backdrop and glass crossfade over 420 ms and the info fades in over 280 ms.
- **Trailer:** starts after focus rests for 900 ms. After 7 seconds idle, the rows drop away and the title sits at the bottom safe area.
- **Picks:** cards rise in sequence, 60 ms apart. Claude's reply becomes the row title.
- **Pill:** the pill takes its new size first, and text only appears once it has arrived, so nothing can overrun the edge. Text leaves before the pill changes.
- **Understanding:** the words that mattered warm to the voice colour inside the sentence.

## Sound

Thirteen short original sounds, synthesised from a build script at -12 dBFS peak. No Apple sound files are used.

| **Sound** | **Length** | **When** |
|---|---|---|
| focus | 40 ms | Focus moves along a row |
| row | 70 ms | Focus moves between rows |
| edge | 80 ms | Nothing further in that direction |
| select | 120 ms | OK on a card or pill |
| back | 120 ms | Back, or leaving a screen |
| home | 300 ms | Home key, or the app comes forward |
| listenStart | 240 ms | Mic pressed. Reference build only, because Google owns this moment on the TV |
| listenEnd | 200 ms | Speech finished, thinking begins |
| picks | 430 ms | Picks arrive |
| handoff | 380 ms | Opening a title in another app |
| error | 220 ms | No match or no connection |
| boot | 2400 ms | Power-on, the blueprint builds and resolves |
| build | 650 ms | A set of picks resolves |

Sound carries position. Stereo pan follows progress along a row, up to 40% left or right, so the start of a row sounds left and the end sounds right. Pitch drops 5% with each row down, so depth can be heard.

- Master level is 32%.
- Sounds are at least 45 ms apart, so a held key doesn't machine-gun.
- A trailer ducks to 55% for bigger moments, never for focus or row ticks.
- The TV's "Sound effects" setting switches all of this off.

## Components

All components are bound to variables.

| **Component** | **Variants** | **Rules** |
|---|---|---|
| Card | State: Rest, Focused, Dimmed. Art: Image, None | Label shows only on the focused row, and never on the first row |
| Pill | State: Rest, Focused | Used on the Details panel. Focus scales 1.06 at runtime |
| Token | One | Retired from the screens. Claude's reply replaced keyword chips |
| Top bar | Prompt: On, Off, Mark, Thinking | The ticker trades between the mark and the prompt. Thinking replaces the clock during a search |
| Mark | Pose: 8 poses | The little TV on its pixel grid |
| Info | Size: Home, Browse. Mark: Logo, Title | Anchored by its bottom edge |
| Row | Kind: Cards, Pills. State: Active, Inactive | Rows above the focused row fade out, rows below dim to 46% |
| Backdrop | Mode: Home, Browse, Listening, More, Details | Picture, scrim and dim |
| Details panel | Mark: Logo, Title | 8 columns wide, text held to 860 so three pills always fit |
| Veil | Kind: Glass, Solid | Solid when there's no feed behind it |


## Screens

| **Screen** | **What it does** |
|---|---|
| Home | Hero with trailer, first row at 730 |
| Browse | Info rises, focused row at 380, next row shows below |
| Listening | The chip springs to the centre at 1.5 times its size. The mark works alone in it until Claude's first line arrives, and those lines replace the clock. The picture blurs and the spoken words sit centred on the 1760 content width, with the words that carry the meaning in the voice colour |
| Picks | A vertical grid, four across, rows 310 apart, always in the browse layout. Up and down keep the column. Claude's reply is the first row's title. Titles join the end as they arrive |
| Details | Glass panel over the picture. Opens on a long press of OK |
| More | App tiles showing each app's own TV banner, including the way back to Google TV home |

## Edge cases

| **Case** | **Behaviour** |
|---|---|
| No logo, or the logo fails to load | Typeset title in Serif |
| Long title | 2 lines on Home, 1 browsing, then an ellipsis |
| Long reason | 2 lines on Home, 1 browsing across all 12 columns |
| Long overview | 4 lines on the panel |
| No art | Plain card with the title on the well colour |
| No trailer, or trailer blocked | The backdrop stays and the screen never goes empty |
| Not on your services | "Not on your services · Opens in Stremio" |
| Leaving soon | Service line and card label show the date |
| First card | Sits in the first column |
| Row end | The track stops when the last card reaches the right margin |
| Last row or row edge | The edge sound plays and the selection leans and returns |
| One pick | The row title is still Claude's reply |
| Thinking | The sentence stays centred and the pill holds the mark alone until Claude's first line arrives |
| No match | The reply sits where the spoken words were, then returns home |
| No connection with a saved feed | The saved feed shows |
| No connection, no feed | Solid veil. OK tries again |
| Engine can't reach its database | The last good feed is served for up to 7 days |
| Profile picker in Prime or JioHotstar | Outside this UI. One OK press continues to the title |
| SonyLIV | Off until the TV is signed in to a paid plan |

## Ticker

The mark in the chip shows the TV is thinking. It works alone for a while, then the chip stretches on the morph spring and the mic prompt comes in, and the two keep trading places. During a search it scans in the centred chip while Claude writes three short lines for that exact request. Replace it by editing the poses in `design/build_mark.py`.

## Row forms

Different shapes keep the page from being one long list, and each still sits on the grid.

| **Form** | **Size** | **Across** | **Use** |
|---|---|---|---|
| Wide | 410 by 230 | 4 | The default, and always the first row |
| Tall | 260 by 390 | 6 | True posters |
| Spotlight | 860 by 484 selected, 410 by 230 others | 1 big, 2 small | The selected card is big and its neighbours trade size with it as you move |
| Apps | 260 by 146 | 6 | More row |

A section breaker sits above the first row of a group and says why the group is there: Close to home, Because of who you are, You should look at this, New out there. It is 170 tall and fades away once you reach its row.

## Feed and search

Claude keeps a pool of up to 14 personal rows, topped up every 3 hours from what the household said, watched and opened. Row names are written for them, such as "Because Coherence broke your brain". Every open lays the pool out differently: rows shown last time go to the back and the lead row prefers titles not just shown. Titles named in their taste notes or watch history are left out.

Search runs on the larger Claude model. Its reply and its own ideas are streamed, so they reach the screen as they are written. A second pass then adds to the list, up to 24 titles. If that model fails, search falls back to the smaller one and the Worker logs it.

## Splash and power-on

The screen draws itself, in black. This is an original take on the skeleton generation effect from Jakub Antalik's transitions.dev work.

| **Step** | **Time** | **What happens** |
|---|---|---|
| Ring | 500 ms, or longer | 36 dots turn slowly on black. The ring holds until the backdrop is ready, so this is also the loading state |
| Field | 500 ms | The ring opens and a 40-spaced dot field spreads from the centre |
| Blueprint | 800 ms | Tonight's real layout appears: dotted runs for text, dashed outlines for cards and the chip. A target bracket hops between parts and names them |
| Scan | 900 ms | A dotted line travels down. Above it is the real interface. Below it is still blueprint |

The parts are read from the live screen, so the blueprint is always the exact layout about to appear. Any key press skips it.

It plays every time the TV is switched on, on every wake from sleep or the screensaver, and when the app returns after more than 10 minutes away. After a boot, Google's launcher arrives late, so for 45 seconds the app takes the screen back each time it appears. Picks use a short 650 ms version over the lower screen, so a new set of titles builds the same way. Trailers wait until the scan has finished.

Key frames are on the Splash page. They are captures of a moving sequence, for reference.
