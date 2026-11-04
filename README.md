# Yayos Game 🧓🚌

**Yayos Game** is a loving retro pixel-art tribute that mixes the classic **Frogger** crossing tension with **Lemmings**-style character management.

You play as a well-meaning but slightly overwhelmed grandchild trying to get as many elderly people ("yayos") as possible from the bus stop safely across the busy streets of Barcelona to the **Sagrada Familia**.

## Level 1 — Barcelona: La Sagrada Familia

- The bus arrives at the bottom of the screen and unloads **6 unique yayos**, each with different speeds and personalities.
- The map has **3 horizontal roads** and **2 vertical streets** crossing them.
- Traffic is constant but regulated by **short-cycle traffic lights** (5–8 seconds) and **zebra crossings** at every intersection.
- Vehicles include cars, buses, motorcycles, and electric scooters (patinetes), all with distinct sizes and speeds.
- Yayos can get **tired** and stop for a few seconds — timing and commands are everything.
- Goal: Get the maximum number of yayos to the Sagrada Familia at the top of the screen.

## Controls (Touch)

1. **Tap a yayo** to select it (big highlight appears).
2. Use the three chunky retro buttons at the bottom:
   - **STOP** — Make the selected yayo wait safely (great at curbs).
   - **GO** — Force the yayo to attempt crossing the current road immediately.
   - **HURRY** — Temporary speed boost (risky but sometimes necessary).

## Difficulty Levels

- **Easy**: Fewer cars, longer green lights, yayos tire less often.
- **Medium**: Balanced traffic.
- **Hard**: Dense traffic, short safe windows, yayos get tired more frequently.

## Current Status (v0.1)

- ✅ Complete Level 1 (Barcelona)
- ✅ Full traffic system + lights + zebras
- ✅ 6 unique yayo types with speed + tiredness
- ✅ Bus arrival + unloading animation
- ✅ Selection + command system
- ✅ Win / Lose conditions + score
- ✅ Pure 70s/80s chunky pixel-art style (no external assets needed for first version)

## How to Build & Run

1. Open Android Studio (you already have it installed).
2. Choose **"Open"** → select the `yayos_game` folder.
3. Let Android Studio download Gradle + any missing SDK components (it will use your existing SDK at `C:\Users\Antonio\Android\Sdk`).
4. Create or select a device in the emulator (any Pixel with API 31+ works great).
5. Click **Run**.

The game is completely self-contained and runs beautifully even on modest hardware because it uses a classic `SurfaceView` + Canvas renderer with a fixed virtual resolution (360×640) scaled with chunky pixels.

## Future Plans (when you want more)

- Real hand-crafted 24×24 / 32×32 pixel-art sprites for yayos and vehicles (we can generate them together with AI or draw them).
- More levels (Park Güell, Barceloneta beach, etc.).
- More yayo abilities / personality quirks.
- Sound (retro chiptunes and honks).
- High score table + Barcelona-themed unlockables.

---

Made with ❤️ for all the yayos who still cross the street faster than you expect.

**"¡Cuidado con el patinete!"**

## License

MIT — do whatever you want, just don't run over the yayos in real life.

## Notice and Disclaimer

NOTICE AND DISCLAIMER: yayos_game does not retrieve external materials at run time; it only works with the local device. This software is provided "AS IS", without warranty of any kind. The author assumes no liability for any losses, damages, liabilities or legal consequences from your use or inability to use this software. Use this software at your own risk.
