# Setup, for Claude Code

You're setting up Non-Annoying TV for someone who may not be technical. Go one step at a time, and explain each step in a sentence before you do it. Never print their keys, and never commit `.env` or `taste.md`.

## 1. Check the tools

They need Node 20 or newer, Python 3, a JDK (17 or newer), and the Android SDK with platform tools. Check what's there and offer to install what's missing. Set `ANDROID_HOME` if the SDK isn't in the default place.

## 2. Collect the keys

Run `cp .env.example .env`. Ask them to open `.env` themselves and paste in `ANTHROPIC_API_KEY` and `MOTN_KEY`, so the keys never pass through the chat. Tell them where each key comes from. Then ask for their country and the services they pay for, and fill in `COUNTRY`, `SERVICES` and `CATALOGS`.

Check that their services exist in `serviceName()` and `pkg()` in `tv-app/app/src/main/java/tv/home/app/Title.java`, and in the row of apps in `MainActivity.java`. If one is missing, find its Android TV package name and add it, along with its name in `NICE` in `worker/src/worker.js`.

## 3. Learn their taste

Have a short conversation: who watches, when, what they keep coming back to, what they hate, what languages they use, and what they've already seen. Write the answers to `taste.md` in plain sentences, following `taste.example.md`. Read it back to them.

## 4. Set up the cloud

Run `scripts/setup_cloud.sh`. It opens a browser for Cloudflare login the first time. Then run `scripts/push_taste.sh`.

## 5. Fill the catalogue

Run `scripts/fill_catalog.sh`. Tell them first that it uses their Movie of the Night quota and takes a while. It's safe to run again if it stops.

## 6. Connect the TV

Walk them through it on the TV:

1. Settings, System, About, then press Android TV OS build seven times to turn on developer options
2. Settings, System, Developer options, then turn on USB debugging or network debugging
3. Find the TV's IP address in the network settings

Put it in `.env` as `TV_IP`, then run `scripts/tv_install.sh`. Tell them to accept the prompt on the TV the first time.

## 7. Try it

Ask them to press Home, then hold the remote's mic button and ask for something in their own words. If it fails, check `adb logcat -s TVHOME` and `npx wrangler tail` from `worker/`.

To undo everything on the TV, run `scripts/tv_remove.sh`.

## Later

To add a memory source, see "Connect your own memory" in `README.md`. To change the look, edit `design/tokens.json`, run `python3 design/build_tokens.py`, then `scripts/tv_install.sh`.
