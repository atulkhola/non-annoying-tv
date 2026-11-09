# Non-Annoying TV

A home screen for Google TV that knows what your household likes, and skips the annoying part.

Sakshi and I love watching films together, mostly mind-bending thrillers. The watching was never the problem. Getting to it was. First you hunt for something with a good IMDb rating. Then you check which app has it. Then it's on none of the apps you pay for, so you cast it from the laptop, and every time you want to pause, somebody has to get up. Again. And again.

So one weekend I gave up, redesigned our TV home in Figma and got Claude to build it. This is that home screen.

You tell it what you feel like watching, in your own messy words, the way you'd say it to the person next to you on the sofa. Claude works out what you meant and finds real titles, with the IMDb rating right there so nobody has to go and check. One press opens it in the app you already pay for. If you don't have it anywhere, it opens in Stremio. The rows on the home screen are planned around what you've told it about yourselves, and they get better the more you use it.

It sits on top of Google TV. It isn't a new operating system, and Google's own home is always one click away (for the days you miss being annoyed).

## What you'll need

Everything runs on your own accounts. There are no keys in here, no accounts, and nobody else's data. Including mine.

- A TV running Google TV or Android TV, with developer options switched on
- A Mac or Linux computer with [Claude Code](https://claude.com/claude-code)
- A [Cloudflare](https://cloudflare.com) account. Once you're using this every day, the paid Workers plan is worth it
- An [Anthropic API key](https://console.anthropic.com). Heads up: a Claude subscription doesn't include API access, and every search costs a little
- A [Movie of the Night](https://www.movieofthenight.com/about/api) key, which is how it knows what's streaming where
- The Stremio app on your TV, if you want somewhere for the titles you don't subscribe to. Totally optional

## Setting it up

I wanted this to be something a non-technical friend could install, so Claude does the setup with you. Clone the repo, open Claude Code inside it, and paste:

```
Read SETUP.md and set this up for me.
```

It goes one step at a time, explains each step before it does it, and at some point asks what the two of you (or four, or one) like to watch. That conversation becomes your taste file.

## Making it yours

| If you want to change | Go here |
|---|---|
| What it knows about you | `taste.md`, then run `scripts/push_taste.sh` |
| Colours, type, spacing, motion | `design/tokens.json`, then `python3 design/build_tokens.py` |
| The UI sounds | `design/build_sounds.py` |
| The little TV in the search chip | `design/build_mark.py` |
| Your country and your services | `.env`, then `scripts/setup_cloud.sh`. If you're outside India, your services also need their app added in `tv-app/app/src/main/java/tv/home/app/Title.java`. Ask Claude, it knows |

## Plugging in your own memory

By default it learns from a plain taste file. That works fine. But a taste file doesn't know that a friend told you about Dark at lunch last week and you both sounded keen.

I wear a note taker that hears my day, so I wanted the TV to hear about the films that come up in it. If you have anything like that (a note taker, a journal, a notes app you actually use), send it one line at a time:

```bash
scripts/add_note.sh "A friend recommended Dark and we both sounded keen."
```

Or call `POST /admin/note` from any automation, with your `APP_TOKEN` as a bearer token and `{"note": "..."}` as the body. Claude reads the newest 60 notes on every search and every time it plans your rows.

Two rules, please. Only send notes about films and shows. And if other people show up in them, ask them first.

## How it works

```
your words -> Google's speech on the TV -> your Cloudflare Worker -> Claude
                                                 |
                       Movie of the Night (where it streams)
                       Cinemeta (ratings, trailers, posters)
                                                 |
              rows on your TV -> Netflix, Prime, JioHotstar or Stremio
```

- `tv-app/` is the Android app that runs on the TV
- `worker/` is the engine: Cloudflare Workers, D1, Vectorize and Workers AI
- `design/` is the design system. `design/SPEC.md` has all of it, probably in more detail than anyone needs

## Before you get too excited

- **It has been tested on exactly one TV.** Ours, a Google TV on Android 14. The Home button takeover and the voice search both read Google TV's own screens through an accessibility service, so on other models they might need some fixing.
- **Your data stays on your accounts.** Your requests go to your own Worker and to Anthropic, and that's it. The accessibility service runs on the TV and only acts on Google's home and voice screens.
- **Your token is baked into the app you build.** So please don't share your APK.
- **It doesn't play video from add-ons.** Titles open in the apps on your TV, and what you run inside them is your call. Please respect the rules of the services you use.
- **Trailers come through [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor),** which reads YouTube directly. YouTube can break that whenever it feels like it.

## Credits

Streaming availability comes from the [Streaming Availability API](https://www.movieofthenight.com/about/api) by Movie of the Night. Their terms ask for this credit, so the app also shows it on its About screen. Ratings and artwork come through Cinemeta. The type is the [Geist](https://vercel.com/font) family, under the SIL Open Font License.

Not affiliated with Google, Netflix, Stremio or Anthropic. Just a guy who got tired of getting up.

## Licence

GPL-3.0, because the app includes NewPipe Extractor. See `LICENSE`.
