# Catat Uang

An Android expense tracker that fills itself in from your transfer receipts.

- **Share a receipt from BRImo** (or DANA, GoPay, OVO, Livin', BCA, any QRIS receipt) → Catat Uang reads the
  amount, recipient, date, note and reference number with on-device OCR.
- **Jev picks the category.** The receipt details go to [Jev](https://openrouter.ai/docs/guides/community/jev)
  on OpenRouter as a `choice` question over your own categories. Paid this person before? The app reuses
  that category instead and skips the API call.
- **You always check before saving.** Low-confidence picks are flagged, and duplicates (same reference
  number) are caught.
- **Home screen widget** with this month's total, budget bar, today's spend, top categories and a quick "+" button.

Everything is stored on the phone (Room/SQLite). The only thing that leaves the device is the receipt summary
sent to OpenRouter for categorising, and only when you've added a key.

## Install

Every push to `main` builds an APK. On your phone, open
[Releases → Latest build](https://github.com/mfachri820/catat-uang-pake-llm/releases/tag/latest),
download `catat-uang.apk` and open it (allow "install unknown apps" for your browser once).

## Using it

1. **Settings** → paste an OpenRouter key from <https://openrouter.ai/keys>, optionally set a monthly budget.
2. In BRImo, after a transfer, tap **Bagikan / Share** on the receipt and pick **Catat pengeluaran**.
   You can also tap **Scan receipt** in the app and pick a saved screenshot.
3. Check the pre-filled form and tap **Save**.
4. Long-press your home screen → Widgets → **Catat Uang**.

Edit category descriptions under **Categories**: that text is exactly what Jev reads, so listing the merchants
you actually use (e.g. "Kopi Kenangan, warung, GoFood") makes it more accurate.

## How it works

```
BRImo share ─► MainActivity (ACTION_SEND image/*)
                 │
                 ▼
          ReceiptOcr (ML Kit, offline) ─► joinRows() puts two-column rows back together
                 │
                 ▼
          ReceiptParser ─► amount · recipient · date/time (WIB/WITA/WIT) · note · ref · source app
                 │
                 ▼
     recipient seen before? ── yes ─► reuse last category
                 │ no
                 ▼
          JevCategorizer ─► POST openrouter.ai/api/alpha/decisions  {choice over your categories}
                 │
                 ▼
          Review form ─► Room DB ─► widget refresh
```

| Path | What |
|---|---|
| `receipt/ReceiptParser.kt` | Pure-Kotlin parser for Indonesian receipts (unit tested) |
| `receipt/ReceiptOcr.kt` | ML Kit text recognition + row joining |
| `ai/JevCategorizer.kt` | OpenRouter Decisions API client |
| `widget/SpendingWidget.kt` | Glance home screen widget |
| `data/` | Room entities, DAOs, default categories, settings |
| `ui/` | Compose screens |

## Build locally

Open the project in Android Studio (JDK 17), or run `./gradlew assembleDebug`. Parser tests:
`./gradlew testDebugUnitTest`.

## Signing for CI

So that each new build installs over the previous one (keeping your data), CI signs with a fixed key from
repository secrets: `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`. Without them, CI still builds, but each build has a different signature and Android
will ask you to uninstall first.

## Ideas for later

- Auto-import from BRImo notifications (NotificationListenerService) so you don't even need to share.
- Income tracking, CSV export, charts per category over months.
