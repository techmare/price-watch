#!/usr/bin/env python3
"""
Daily price check for Midox Shop and competitor websites.

Reads public product prices only, respects each site's robots.txt, and waits
between requests so no site is hammered. Standard library only.

Outputs (in data/):
  latest.json   every site's products with current price and stock
  history.json  price change points per product, kept for 180 days
  changes.json  what changed since the previous run
"""
from __future__ import annotations

import concurrent.futures as cf
import datetime as dt
import gzip
import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
import xml.etree.ElementTree as ET
import zlib
from html.parser import HTMLParser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")
UA = "MidoxPriceWatch/1.0 (daily price check; +https://www.midoxshop.ma)"
DELAY = float(os.environ.get("PW_DELAY", "1.2"))      # seconds between requests to one site
MAX_PAGES = int(os.environ.get("PW_MAX_PAGES", "1500"))  # product pages per sitemap site
HISTORY_DAYS = 180

# Words that mark camera / video gear. Used to skip unrelated products on
# general electronics stores (phones, TVs, appliances...).
GEAR_WORDS = [
    "canon", "nikon", "sony", "fujifilm", "fuji", "panasonic", "lumix", "olympus", "om-system", "leica", "hasselblad",
    "dji", "osmo", "gopro", "insta360", "ronin", "zhiyun", "feiyutech", "hohem",
    "hollyland", "rode", "røde", "saramonic", "boya", "deity", "sennheiser", "comica", "synco", "shure",
    "sigma", "tamron", "samyang", "viltrox", "tokina", "laowa", "ttartisan", "7artisans", "meike", "yongnuo",
    "godox", "aputure", "amaran", "nanlite", "smallrig", "manfrotto", "benro", "ulanzi", "neewer", "k&f", "peak design",
    "sandisk extreme", "prograde", "lexar professional",
    "appareil-photo", "appareil photo", "camera", "caméra", "camescope", "hybride", "reflex", "objectif", "lens",
    "eos", "alpha", "ilce", "gimbal", "stabilisateur", "trepied", "trépied", "micro-cravate", "microphone sans fil",
]


# ------------------------------------------------------------------ http

class Http:
    def __init__(self):
        self.last: dict[str, float] = {}
        self.robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}
        self.robots_text: dict[str, str] = {}

    def _wait(self, host: str):
        t = self.last.get(host, 0)
        now = time.time()
        if now - t < DELAY:
            time.sleep(DELAY - (now - t))
        self.last[host] = time.time()

    def raw(self, url: str, accept: str = "*/*", check_robots: bool = True, timeout: int = 30) -> tuple[int, bytes, str]:
        host = urllib.parse.urlsplit(url).netloc
        if check_robots and not self.allowed(url):
            return 999, b"", "robots"
        self._wait(host)
        req = urllib.request.Request(url, headers={
            "User-Agent": UA, "Accept": accept, "Accept-Encoding": "gzip, deflate", "Accept-Language": "fr-MA,fr;q=0.9,en;q=0.6",
        })
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=timeout) as r:
                    body = r.read()
                    enc = (r.headers.get("Content-Encoding") or "").lower()
                    if enc == "gzip":
                        body = gzip.decompress(body)
                    elif enc == "deflate":
                        body = zlib.decompress(body)
                    return r.status, body, r.headers.get("Content-Type", "")
            except urllib.error.HTTPError as e:
                if e.code in (429, 503) and attempt < 2:
                    time.sleep(5 * (attempt + 1))
                    continue
                return e.code, b"", ""
            except Exception as e:  # timeouts, resets
                if attempt < 2:
                    time.sleep(3)
                    continue
                return 0, b"", str(e)
        return 0, b"", "failed"

    def text(self, url: str, **kw) -> str | None:
        code, body, _ = self.raw(url, **kw)
        if code != 200:
            return None
        return body.decode("utf-8", errors="replace")

    def json(self, url: str, **kw):
        code, body, _ = self.raw(url, accept="application/json", **kw)
        if code != 200:
            return None
        try:
            return json.loads(body.decode("utf-8", errors="replace"))
        except Exception:
            return None

    def allowed(self, url: str) -> bool:
        parts = urllib.parse.urlsplit(url)
        base = f"{parts.scheme}://{parts.netloc}"
        if base not in self.robots:
            rp = urllib.robotparser.RobotFileParser()
            code, body, _ = self.raw(base + "/robots.txt", check_robots=False)
            if code == 200:
                txt = body.decode("utf-8", errors="replace")
                self.robots_text[base] = txt
                rp.parse(txt.splitlines())
                self.robots[base] = rp
            else:
                self.robots[base] = None  # no robots.txt: everything allowed
                self.robots_text[base] = ""
        rp = self.robots[base]
        return True if rp is None else rp.can_fetch(UA, url)

    def sitemaps_from_robots(self, base: str) -> list[str]:
        self.allowed(base + "/")
        txt = self.robots_text.get(base, "")
        return [l.split(":", 1)[1].strip() for l in txt.splitlines() if l.lower().startswith("sitemap:")]


# --------------------------------------------------------------- parsing

def to_price(v) -> float | None:
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v) if v > 0 else None
    s = str(v).strip().replace(" ", "").replace("\xa0", "").replace(" ", "")
    s = re.sub(r"[^\d.,]", "", s)
    if not s:
        return None
    if "," in s and "." in s:
        # 4.799,00 or 4,799.00
        if s.rfind(",") > s.rfind("."):
            s = s.replace(".", "").replace(",", ".")
        else:
            s = s.replace(",", "")
    elif "," in s:
        parts = s.split(",")
        s = s.replace(",", ".") if len(parts[-1]) in (1, 2) else s.replace(",", "")
    elif s.count(".") > 1 or ("." in s and len(s.split(".")[-1]) == 3):
        s = s.replace(".", "")
    try:
        p = float(s)
        return p if 0 < p < 10_000_000 else None
    except ValueError:
        return None


def clean(s: str | None) -> str:
    if not s:
        return ""
    s = html.unescape(re.sub(r"<[^>]+>", " ", s))
    return re.sub(r"\s+", " ", s).strip()


def is_gear(text: str) -> bool:
    t = text.lower()
    return any(w in t for w in GEAR_WORDS)


class MetaParser(HTMLParser):
    """Collects JSON-LD blocks, meta tags, itemprop prices, title and first h1."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ld: list[str] = []
        self.meta: dict[str, str] = {}
        self.itemprop: dict[str, str] = {}
        self.title = ""
        self.h1 = ""
        self._in = None
        self._buf: list[str] = []

    def handle_starttag(self, tag, attrs):
        a = {k.lower(): (v or "") for k, v in attrs}
        if tag == "script" and "ld+json" in a.get("type", ""):
            self._in, self._buf = "ld", []
        elif tag == "title" and not self.title:
            self._in, self._buf = "title", []
        elif tag == "h1" and not self.h1:
            self._in, self._buf = "h1", []
        elif tag == "meta":
            key = (a.get("property") or a.get("name") or a.get("itemprop") or "").lower()
            if key and "content" in a and key not in self.meta:
                self.meta[key] = a["content"]
        ip = a.get("itemprop", "").lower()
        if ip in ("price", "pricecurrency", "availability", "name") and ip not in self.itemprop:
            val = a.get("content") or a.get("href") or ""
            if val:
                self.itemprop[ip] = val

    def handle_endtag(self, tag):
        if self._in == "ld" and tag == "script":
            self.ld.append("".join(self._buf))
            self._in = None
        elif self._in == "title" and tag == "title":
            self.title = clean("".join(self._buf))
            self._in = None
        elif self._in == "h1" and tag == "h1":
            self.h1 = clean("".join(self._buf))
            self._in = None

    def handle_data(self, data):
        if self._in:
            self._buf.append(data)


def find_products_in_ld(obj) -> list[dict]:
    out = []
    if isinstance(obj, list):
        for o in obj:
            out += find_products_in_ld(o)
    elif isinstance(obj, dict):
        t = obj.get("@type")
        types = t if isinstance(t, list) else [t]
        if any(str(x).lower() in ("product", "productgroup") for x in types):
            out.append(obj)
        for k in ("@graph", "mainEntity", "itemListElement", "item"):
            if k in obj:
                out += find_products_in_ld(obj[k])
    return out


def parse_product_page(page: str, url: str) -> dict | None:
    p = MetaParser()
    try:
        p.feed(page)
    except Exception:
        pass
    name = price = regular = None
    in_stock = None
    image = None

    for block in p.ld:
        try:
            data = json.loads(block.strip().rstrip(";"))
        except Exception:
            continue
        for prod in find_products_in_ld(data):
            name = name or clean(prod.get("name"))
            img = prod.get("image")
            if isinstance(img, list) and img:
                img = img[0]
            if isinstance(img, dict):
                img = img.get("url")
            image = image or (img if isinstance(img, str) else None)
            offers = prod.get("offers")
            if not offers and isinstance(prod.get("hasVariant"), list) and prod["hasVariant"]:
                v0 = prod["hasVariant"][0]
                offers = v0.get("offers") if isinstance(v0, dict) else None
            if isinstance(offers, list):
                offers = offers[0] if offers else None
            if isinstance(offers, dict):
                price = price or to_price(offers.get("price") or offers.get("lowPrice"))
                spec = offers.get("priceSpecification")
                if isinstance(spec, list):
                    for sp in spec:
                        if isinstance(sp, dict) and "strikethrough" in str(sp.get("priceType", "")).lower():
                            regular = to_price(sp.get("price"))
                av = str(offers.get("availability", "")).lower()
                if av:
                    in_stock = ("instock" in av or "limitedavailability" in av or "preorder" in av)
            if price:
                break
        if price:
            break

    m = p.meta
    if not price:
        price = to_price(m.get("product:price:amount") or m.get("og:price:amount") or p.itemprop.get("price"))
    if in_stock is None:
        av = (m.get("product:availability") or m.get("og:availability") or p.itemprop.get("availability") or "").lower()
        if av:
            in_stock = "in" in av and "out" not in av
    if not name:
        name = clean(m.get("og:title")) or p.h1 or p.title
    image = image or m.get("og:image")
    if not price or not name:
        return None
    return {"url": url, "name": name, "price": round(price, 2), "regular": regular,
            "inStock": True if in_stock is None else in_stock, "image": image}


# -------------------------------------------------------------- adapters

def scrape_woo(http: Http, base: str, gear_only: bool) -> list[dict] | None:
    out, page = [], 1
    while page <= 60:
        data = http.json(f"{base}/wp-json/wc/store/v1/products?per_page=100&page={page}&orderby=id&order=asc")
        if data is None:
            return None if page == 1 else out
        if not isinstance(data, list):
            return None
        for it in data:
            pr = it.get("prices") or {}
            minor = int(pr.get("currency_minor_unit") or 0)
            def money(k):
                v = pr.get(k)
                return to_price(int(v) / (10 ** minor)) if v not in (None, "") and str(v).isdigit() else None
            price = money("price")
            name = clean(it.get("name"))
            if not price or not name:
                continue
            cats = " ".join(c.get("name", "") for c in it.get("categories") or [])
            if gear_only and not is_gear(name + " " + cats + " " + (it.get("permalink") or "")):
                continue
            imgs = it.get("images") or []
            regular = money("regular_price")
            out.append({
                "url": it.get("permalink"), "name": name, "price": price,
                "regular": regular if regular and regular > price else None,
                "inStock": bool(it.get("is_in_stock", True)),
                "image": (imgs[0].get("thumbnail") or imgs[0].get("src")) if imgs else None,
            })
        if len(data) < 100:
            break
        page += 1
    return out


def scrape_shopify(http: Http, base: str, gear_only: bool) -> list[dict] | None:
    out, page = [], 1
    while page <= 40:
        data = http.json(f"{base}/products.json?limit=250&page={page}")
        if not isinstance(data, dict) or "products" not in data:
            return None if page == 1 else out
        prods = data["products"]
        for it in prods:
            name = clean(it.get("title"))
            variants = it.get("variants") or []
            avail = [v for v in variants if v.get("available")]
            pool = avail or variants
            prices = [to_price(v.get("price")) for v in pool]
            prices = [p for p in prices if p]
            if not name or not prices:
                continue
            text = name + " " + (it.get("product_type") or "") + " " + " ".join(it.get("tags") or []) + " " + (it.get("vendor") or "")
            if gear_only and not is_gear(text):
                continue
            price = min(prices)
            compare = [to_price(v.get("compare_at_price")) for v in pool]
            compare = [c for c in compare if c and c > price]
            imgs = it.get("images") or []
            out.append({
                "url": f"{base}/products/{it.get('handle')}", "name": name, "price": price,
                "regular": min(compare) if compare else None, "inStock": bool(avail),
                "image": imgs[0].get("src") if imgs else None,
            })
        if len(prods) < 250:
            break
        page += 1
    return out


PRODUCT_URL = re.compile(r"(/produit/|/product/|/products/|/p/|/\d+-[^/]+\.html$|/item/\d+)", re.I)
SKIP_URL = re.compile(r"(/categor|/tag/|/brand|/marque|/blog|/page/|\.jpg|\.png|\.webp|/cart|/panier|/compte|/account)", re.I)


def sitemap_urls(http: Http, base: str) -> list[str]:
    roots = http.sitemaps_from_robots(base)
    if not roots:
        roots = [base + p for p in ("/sitemap_index.xml", "/sitemap.xml", "/1_index_sitemap.xml", "/product-sitemap.xml")]
    seen, urls, queue = set(), [], list(roots)
    ns = re.compile(r"\{.*?\}")
    while queue and len(seen) < 80:
        sm = queue.pop(0)
        if sm in seen:
            continue
        seen.add(sm)
        code, body, _ = http.raw(sm)
        if code != 200 or not body:
            continue
        if sm.endswith(".gz"):
            try:
                body = gzip.decompress(body)
            except Exception:
                pass
        try:
            root = ET.fromstring(body)
        except ET.ParseError:
            continue
        tag = ns.sub("", root.tag)
        locs = [ns.sub("", e.tag) == "loc" and (e.text or "").strip() for e in root.iter()]
        locs = [l for l in locs if l]
        if tag == "sitemapindex":
            # product sitemaps first, skip obvious non-product ones
            subs = sorted(locs, key=lambda u: (0 if "product" in u.lower() or "produit" in u.lower() else 1))
            subs = [u for u in subs if not re.search(r"(post|page|categor|tag|author|cms|manufacturer|supplier)", u.lower())
                    or "product" in u.lower()]
            queue += subs
        else:
            urls += locs
    # keep product-looking urls
    prods = [u for u in urls if PRODUCT_URL.search(urllib.parse.urlsplit(u).path) and not SKIP_URL.search(u)]
    if not prods:
        prods = [u for u in urls if not SKIP_URL.search(u) and urllib.parse.urlsplit(u).path.count("/") >= 1]
    # de-duplicate, prefer French versions when a site lists /en/ and /fr/
    out, keys = [], set()
    for u in prods:
        key = re.sub(r"/(en|ar|fr)/", "/", u)
        if key in keys:
            continue
        keys.add(key)
        out.append(u)
    return out


def scrape_sitemap(http: Http, base: str, gear_only: bool, log) -> list[dict] | None:
    urls = sitemap_urls(http, base)
    if not urls:
        return None
    if gear_only:
        urls = [u for u in urls if is_gear(urllib.parse.unquote(u).replace("-", " "))]
    log(f"{len(urls)} product pages to read")
    urls = urls[:MAX_PAGES]
    out = []
    for i, u in enumerate(urls):
        page = http.text(u, accept="text/html")
        if not page:
            continue
        p = parse_product_page(page, u)
        if p and (not gear_only or is_gear(p["name"] + " " + u)):
            out.append(p)
        if i and i % 200 == 0:
            log(f"  {i}/{len(urls)} pages, {len(out)} products")
    return out


def scrape_site(http: Http, site: dict, log) -> tuple[list[dict], str]:
    base = site["url"].rstrip("/")
    gear = bool(site.get("filter"))
    mode = site.get("mode", "auto")
    tried = []
    if mode in ("woo", "auto"):
        tried.append("woo")
        r = scrape_woo(http, base, gear)
        if r:
            return r, "woo"
    if mode in ("shopify", "auto"):
        tried.append("shopify")
        r = scrape_shopify(http, base, gear)
        if r:
            return r, "shopify"
    if mode in ("sitemap", "auto", "woo", "shopify"):
        tried.append("sitemap")
        r = scrape_sitemap(http, base, gear, log)
        if r:
            return r, "sitemap"
    raise RuntimeError("no readable products (tried " + ", ".join(tried) + ")")


# ---------------------------------------------------------------- output

def load(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def save(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


def main():
    only = set(sys.argv[1:])
    cfg = load(os.path.join(ROOT, "scraper", "sites.json"), {})
    sites = [s for s in cfg["sites"] if not only or s["id"] in only]
    today = dt.date.today().isoformat()
    now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")

    previous = load(os.path.join(DATA, "latest.json"), {"sites": {}})
    history = load(os.path.join(DATA, "history.json"), {})

    def run(site):
        http = Http()
        lines = []
        log = lambda msg: (lines.append(msg), print(f"[{site['id']}] {msg}", flush=True))
        t0 = time.time()
        try:
            products, method = scrape_site(http, site, log)
            log(f"OK {len(products)} products via {method} in {int(time.time() - t0)}s")
            return site, products, method, None
        except Exception as e:
            log(f"FAILED {e}")
            return site, None, None, str(e)

    results = {}
    with cf.ThreadPoolExecutor(max_workers=8) as pool:
        for site, products, method, err in pool.map(run, sites):
            prev = previous.get("sites", {}).get(site["id"], {})
            if products is None:
                # keep yesterday's data, marked stale, so the app still has something
                results[site["id"]] = {**prev, "id": site["id"], "name": site["name"], "url": site["url"],
                                       "own": bool(site.get("own")), "ok": False, "error": err,
                                       "checked": now, "stale": True}
                continue
            # dedupe by url
            seen, uniq = set(), []
            for p in products:
                if p["url"] and p["url"] not in seen:
                    seen.add(p["url"])
                    uniq.append(p)
            results[site["id"]] = {"id": site["id"], "name": site["name"], "url": site["url"],
                                   "own": bool(site.get("own")), "ok": True, "error": None, "method": method,
                                   "checked": now, "updated": now, "stale": False, "products": uniq}

    # sites not run this time keep their previous data
    for sid, s in previous.get("sites", {}).items():
        results.setdefault(sid, s)

    # changes + history
    changes = []
    cutoff = (dt.date.today() - dt.timedelta(days=HISTORY_DAYS)).isoformat()
    for sid, s in results.items():
        if not s.get("ok") or s.get("stale"):
            continue
        prev_products = {p["url"]: p for p in previous.get("sites", {}).get(sid, {}).get("products", [])}
        h = history.setdefault(sid, {})
        for p in s.get("products", []):
            pts = h.setdefault(p["url"], [])
            point = [today, p["price"], 1 if p["inStock"] else 0]
            if not pts or pts[-1][1:] != point[1:]:
                if pts and pts[-1][0] == today:
                    pts[-1] = point
                else:
                    pts.append(point)
            old = prev_products.get(p["url"])
            if old is None:
                if prev_products:
                    changes.append({"site": sid, "url": p["url"], "name": p["name"], "type": "new", "new": p["price"]})
                continue
            if abs(old["price"] - p["price"]) >= 1:
                changes.append({"site": sid, "url": p["url"], "name": p["name"],
                                "type": "drop" if p["price"] < old["price"] else "rise",
                                "old": old["price"], "new": p["price"]})
            if old.get("inStock") and not p["inStock"]:
                changes.append({"site": sid, "url": p["url"], "name": p["name"], "type": "out", "new": p["price"]})
            elif not old.get("inStock") and p["inStock"]:
                changes.append({"site": sid, "url": p["url"], "name": p["name"], "type": "back", "new": p["price"]})
        for url in list(h):
            h[url] = [pt for pt in h[url] if pt[0] >= cutoff] or h[url][-1:]

    save(os.path.join(DATA, "latest.json"), {"generated": now, "own": cfg.get("own", "midox"), "sites": results})
    save(os.path.join(DATA, "history.json"), history)
    save(os.path.join(DATA, "changes.json"), {"generated": now, "date": today, "changes": changes})
    print(f"Done. {sum(len(s.get('products', [])) for s in results.values())} products, {len(changes)} changes.")


if __name__ == "__main__":
    main()
