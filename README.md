# Non-Annoying TV

A home screen for Google TV that knows what your household likes, and skips the annoying part.

Sakshi and I love watching films together, mostly mind-bending thrillers. The watching was never the problem. Getting to it was. First you hunt for something with a good IMDb rating. Then you check which app has it. Then it's on none of the apps you pay for, so you cast it from the laptop, and every time you want to pause, somebody has to get up. Again. And again.

So one weekend I gave up, redesigned our TV home in Figma and got Claude to build it. This is that home screen.

Say what you feel like watching, in your own messy words. Claude works out what you meant, finds real titles, shows IMDb ratings up front, and one press opens the title in the app you pay for. Anything you don't have opens in Stremio. Your rows are planned for you from what you've told it, and they get better as you use it.

It sits on top of Google TV. It isn't a new operating system, and Google's own home is always one click away (for the days you miss being annoyed).

## What you bring

Everything runs on your own accounts. This repo contains no keys, no accounts and nobody else's data.

- A TV running Google TV or Android TV, with developer options on
- A Mac or Linux computer with [Claude Code](https://claude.com/claude-code)
- A [Cloudflare](https://cloudflare.com) account. The paid Workers plan is worth having once you use this daily
- An [Anthropic API key](https://console.anthropic.com). A Claude subscription doesn't include API access, and every search costs a little
- A [Movie of the Night](https://www.movieofthenight.com/about/api) key, for what's streaming where
- The Stremio app on your TV, if you want a home for titles you don't subscribe to. It's optional

## Install

Clone this repo, open Claude Code inside it, and paste:

```
Read SETUP.md and set this up for me.
```

Claude walks you through it, asks what you like to watch, and writes your taste file.

## Make it yours

| What | Where |
|---|---|
| What it knows about you | `taste.md`, then `scripts/push_taste.sh` |
| Colours, type, spacing, motion | `design/tokens.json`, then `python3 design/build_tokens.py` |
| UI sounds | `design/build_sounds.py` |
| The little TV in the search chip | `design/build_mark.py` |
| Your country and services | `.env`, then `scripts/setup_cloud.sh`. Services outside India also need their app added in `tv-app/app/src/main/java/tv/home/app/Title.java`. Ask Claude |

## Connect your own memory

The default is a plain taste file. If you have a note taker, a journal or anything else that knows what you've been talking about, send it one line at a time:

```bash
scripts/add_note.sh "A friend recommended Dark and we both sounded keen."
```

Or call `POST /admin/note` from any automation, with your `APP_TOKEN` as a bearer token and `{"note": "..."}` as the body. Claude reads the newest 60 notes on every search and every time it plans your rows. Send film and TV notes only, and get the agreement of anyone who shows up in them.

## How it works

```
your words -> Google's speech on the TV -> your Cloudflare Worker -> Claude
                                                 |
                       Movie of the Night (where it streams)
                       Cinemeta (ratings, trailers, posters)
                                                 |
              rows on your TV -> Netflix, Prime, JioHotstar or Stremio
```

- `tv-app/` is the Android app
- `worker/` is the engine: Cloudflare Workers, D1, Vectorize and Workers AI
- `design/` is the design system. Read `design/SPEC.md` for all of it

## Good to know

- **It's been tested on one TV.** It's a Google TV on Android 14. The Home button takeover and voice both read Google TV's own screens through an accessibility service, so they may need changes on other models.
- **Your data stays on your accounts.** Your requests go to your own Worker and to Anthropic. The accessibility service runs on the TV and only acts on Google's home and voice screens.
- **Your token sits inside the app you build.** Don't share your APK.
- **This doesn't include any way to play video from add-ons.** Titles open in the apps on your TV, and what you run inside them is up to you. Please respect the rules of the services you use.
- **Trailers** come through [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), which reads YouTube directly. YouTube can break this at any time.

## Credits

Streaming availability by the [Streaming Availability API](https://www.movieofthenight.com/about/api) from Movie of the Night. Their terms require this credit, so the app shows it on its About screen. Ratings and artwork through Cinemeta. Type is the [Geist](https://vercel.com/font) family under the SIL Open Font License. Not affiliated with Google, Netflix, Stremio or Anthropic.

## Licence

GPL-3.0, because the app includes NewPipe Extractor. See `LICENSE`.
