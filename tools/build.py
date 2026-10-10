#!/usr/bin/env python3
# Inline src/bundle.js + src/style.css + src/app.js into a single offline planner.html
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src")

def read(n):
    with open(os.path.join(SRC, n), encoding="utf-8") as f:
        return f.read()

def inline(html, pattern, replacement):
    return re.sub(pattern, lambda _m: replacement, html)

def main():
    html = read("index.html")
    css = read("style.css")
    bundle = read("bundle.js")
    app = read("app.js")
    # lambda replacement: re.sub must NOT interpret \n \t \\ inside the injected code
    html = inline(html, r'<link[^>]+href="style\.css"[^>]*>', "<style>\n" + css + "\n</style>")
    html = inline(html, r'<script[^>]+src="bundle\.js"[^>]*></script>', "<script>\n" + bundle + "\n</script>")
    html = inline(html, r'<script[^>]+src="app\.js"[^>]*></script>', "<script>\n" + app + "\n</script>")
    out = os.path.join(ROOT, "planner.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)
    print("built", out, f"{os.path.getsize(out)/1024:.0f} KB")

if __name__ == "__main__":
    main()
