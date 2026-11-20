# Price Watch

Every morning this checks the public prices on Moroccan camera shops' websites, compares them with midoxshop.ma, and shows the results in an Android app.

**Download the app:** [latest APK](../../releases/latest/download/PriceWatch.apk)

## How it works
- `scraper/scrape.py` runs on GitHub Actions once a day, overnight (several start times are queued because GitHub often starts scheduled jobs late; the first one does the check). Each shop gets 70 minutes at most. It reads each shop's products (WooCommerce and Shopify data feeds where allowed, otherwise the product pages listed in the shop's sitemap), respects robots.txt and waits between requests.
- Results are saved in `data/`: `latest.json` (current prices), `history.json` (price changes, 180 days) and `changes.json` (what changed since the last check).
- The app downloads those files, matches your products to competitors' by brand, model code, kit lens and variant, and sends a notification (daytime only) when new prices arrive and something changed.

## Shops
Edit `scraper/sites.json` to add or remove a shop. Run the check by hand from the Actions tab (Daily price check, Run workflow).

## App signing
The APK is signed with a private key kept in the repository's Actions secrets
(`SIGNING_KEYSTORE_BASE64`, `SIGNING_PASSWORD`). Never commit a keystore: if this key changes,
installed apps can't update and have to be uninstalled first.

## Screenshots of every screen
`./gradlew testDebugUnitTest -Pscreens --tests '*ScreenTour*'` opens the app on the JVM (Robolectric) with the
data in `data/`, walks through every screen and saves screenshots to `app/build/screens/`.
