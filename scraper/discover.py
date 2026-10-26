#!/usr/bin/env python3
"""One-off: look at how midoxshop.ma loads its catalogue, so the scraper can read it.

Saves the raw HTML of the catalogue and one product page, plus every URL and
data-looking string found in the site's JavaScript, into data/debug/.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scrape import Http, DATA  # noqa: E402

BASE = "https://www.midoxshop.ma"
OUT = os.path.join(DATA, "debug")
os.makedirs(OUT, exist_ok=True)
http = Http()
report = []


def save(name, text):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write(text or "")


for path in ["/", "/products", "/products/hollyland-lark-m2s-combo", "/sitemap.xml", "/robots.txt"]:
    code, body, ctype = http.raw(BASE + path, check_robots=False)
    text = body.decode("utf-8", errors="replace")
    report.append(f"{path}: HTTP {code} {ctype} {len(body)} bytes")
    save(re.sub(r"[^a-z0-9]+", "_", path.strip("/")) or "home" + ".html", text)

catalogue = open(os.path.join(OUT, "products"), encoding="utf-8").read() if os.path.exists(os.path.join(OUT, "products")) else ""
scripts = sorted(set(re.findall(r'src="(/_next/static/[^"]+\.js)"', catalogue)))
report.append(f"\n{len(scripts)} scripts on /products")

found = set()
for s in scripts:
    code, body, _ = http.raw(BASE + s, check_robots=False)
    js = body.decode("utf-8", errors="replace")
    for m in re.findall(r'https?://[a-zA-Z0-9._\-/:%?=&]+', js):
        if not re.search(r"(w3\.org|reactjs|nextjs\.org|github|schema\.org|googleapis\.com/css)", m):
            found.add("URL " + m)
    for m in re.findall(r'["\'](/api/[a-zA-Z0-9_\-/]+)["\']', js):
        found.add("API " + m)
    for kw in ("supabase", "firebase", "sanity", "contentful", "strapi", "prisma", "graphql", "mongodb", "airtable", "cloudinary", "products"):
        if kw in js.lower():
            found.add(f"KEYWORD {kw} in {s}")

report.append("\n".join(sorted(found)))

# Next.js data embedded in the page
for pat, label in [(r"__NEXT_DATA__", "pages-router __NEXT_DATA__"), (r"self\.__next_f\.push", "app-router RSC payload")]:
    report.append(f"{label}: {'yes' if re.search(pat, catalogue) else 'no'}")
prices = re.findall(r'\\?"(?:price|prix|salePrice|regularPrice)\\?"\s*:\s*\\?"?(\d[\d.]*)', catalogue)
report.append(f"price-like values in /products HTML: {len(prices)} e.g. {prices[:10]}")
names = re.findall(r'\\?"(?:name|title)\\?"\s*:\s*\\?"([^"\\]{3,80})', catalogue)
report.append(f"name-like values: {len(names)} e.g. {names[:10]}")
links = sorted(set(re.findall(r'/products/[a-z0-9\-]+', catalogue)))
report.append(f"product links in /products HTML: {len(links)} e.g. {links[:10]}")

save("report.txt", "\n".join(report))
print("\n".join(report))
