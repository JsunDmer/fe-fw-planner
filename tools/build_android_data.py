#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate android/app/src/main/assets/fe_data.json (same derived fields as the
web bundle, but portraits/icons as Android asset paths) and copy the images.
Run:  python3 tools/build_android_data.py
"""
import json, os, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
SRC_ASSETS = os.path.join(ROOT, "assets")
APP = os.path.join(ROOT, "android", "app", "src", "main", "assets")

sys.path.insert(0, TOOLS)
import build_bundle as bb


def asset_ref(relpath):
    # "img/chr/1.webp" -> "chr/1.webp" (Android asset path); fall back to filename
    if not relpath:
        return ""
    parts = relpath.split("/")
    return (parts[1] + "/" + parts[-1]) if len(parts) > 2 else parts[-1]


def main():
    bundle = bb.build(asset_ref)
    os.makedirs(APP, exist_ok=True)
    with open(os.path.join(APP, "fe_data.json"), "w", encoding="utf-8") as f:
        json.dump(bundle, f, ensure_ascii=False, separators=(",", ":"))

    for sub in ("chr", "cls"):
        src = os.path.join(SRC_ASSETS, sub)
        dst = os.path.join(APP, sub)
        if os.path.isdir(src):
            os.makedirs(dst, exist_ok=True)
            for fn in sorted(os.listdir(src)):
                if not fn.startswith("."):
                    shutil.copy2(os.path.join(src, fn), os.path.join(dst, fn))

    n_chr = len(os.listdir(os.path.join(APP, "chr")))
    n_cls = len(os.listdir(os.path.join(APP, "cls")))
    size = os.path.getsize(os.path.join(APP, "fe_data.json")) / 1024
    print(f"wrote {APP}/fe_data.json ({size:.0f} KB), {n_chr} chr imgs, {n_cls} cls imgs")


if __name__ == "__main__":
    main()