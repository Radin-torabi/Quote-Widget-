# Quote Widget

A home-screen widget for Android (made for the Galaxy S26 Ultra / One UI 9) that rotates through **your own quote bank**.

## Install

1. On your phone, open this repo on GitHub and download [`release/QuoteWidget.apk`](release/QuoteWidget.apk)
   (or grab the `QuoteWidget-apk` artifact from the latest **Actions → Build APK** run).
2. Open the file. If asked, allow your browser / My Files to *install unknown apps*.
3. Open **Quote Widget**, paste your quotes, tap **Add to home screen**.
4. Recommended: **Settings › Apps › Quote Widget › Battery › Unrestricted** so One UI doesn't put it to sleep and stop the rotation.

Every build is signed with the same key (`app/quotewidget.keystore`), so newer APKs install over older ones without losing your quotes.

## Pasting your quote bank

Paste anything like this — the app figures out the author:

```
We suffer more often in imagination than in reality. — Seneca
"Stay hungry, stay foolish." - Steve Jobs
Simplicity is the ultimate sophistication ~ Leonardo da Vinci
The obstacle is the way.
— Marcus Aurelius
- bulleted quotes work
1. numbered ones too
```

For quotes that span several lines, switch **How are quotes separated?** to *Blank line between quotes*.
The app shows how many quotes (and authors) it found as you type.

## Settings

| | |
|---|---|
| **Change quote every** | 15 min → once a day, or only when tapped |
| **Shuffle** | Random order without repeats until the whole bank has been shown |
| **Tap action** | Next quote, or open settings |
| **Style** | Material You (follows your wallpaper colours), auto light/dark, dark, light, clear with white or black text |
| **Background opacity** | 0–100 % |
| **Font** | Serif italic, serif, sans, medium, bold, light, condensed, monospace |
| **Text size** | Auto-fit to the widget size, or fixed 10–39 sp |
| **Alignment, author, quotation marks** | On/off |

The widget is resizable (long-press › resize) and long-press › **Settings** reopens the settings.

## How it works

- Plain Android `AppWidgetProvider` in Kotlin, no third-party libraries (~75 KB APK).
- Rotation uses an inexact, non-wakeup `AlarmManager` alarm — no special permissions and almost no battery.
  If the phone is asleep when a change is due, it happens as soon as the screen turns on.
- Alarms are re-armed after reboot / app updates.

## Build it yourself

```
./gradlew assembleRelease     # → app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest   # quote-parser tests
```
