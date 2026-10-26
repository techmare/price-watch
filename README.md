# Price Watch

Every morning this checks the public prices on Moroccan camera shops' websites, compares them with midoxshop.ma, and shows the results in an Android app.

**Download the app:** [latest APK](../../releases/latest/download/PriceWatch.apk)

## How it works
- `scraper/scrape.py` runs on GitHub Actions at about 6:15 Morocco time. It reads each shop's products (WooCommerce and Shopify data feeds where allowed, otherwise the product pages listed in the shop's sitemap), respects robots.txt and waits between requests.
- Results are saved in `data/`: `latest.json` (current prices), `history.json` (price changes, 180 days) and `changes.json` (what changed since the last check).
- The app downloads those files, matches your products to competitors' by brand, model code, kit lens and variant, and sends a notification at 9:00 when something changed.

## Shops
Edit `scraper/sites.json` to add or remove a shop. Run the check by hand from the Actions tab (Daily price check, Run workflow).
