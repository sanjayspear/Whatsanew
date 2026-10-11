#!/usr/bin/env python3
"""
Build an offline Pulse region pack from an OpenStreetMap extract.

    pip install osmium
    curl -LO https://download.geofabrik.de/asia/india/southern-zone-latest.osm.pbf
    python tools/osm-pack/build_region_pack.py southern-zone-latest.osm.pbf \
        --bbox 12.75,77.35,13.20,77.85 --region IN-KA-BLR --out blr.json

Output is the same JSON shape as an Overpass API response ({"elements": [...]}), so the app parses
it with exactly the code it uses for live data (OverpassClient.parse) and classifies places with
OsmCategories.kt: one source of truth for categories. Only *named* places with a relevant tag are
kept. Data (c) OpenStreetMap contributors, ODbL: the pack carries that attribution.
"""
import argparse
import json
import sys
import tempfile

import osmium

# Tag values the app understands. Must stay a superset of OsmCategories.kt; anything the app can't
# classify is dropped on load, so a mismatch only loses places, it never mislabels them.
RELEVANT = {
    "amenity": {"cafe", "restaurant", "fast_food", "food_court", "ice_cream", "bar", "pub", "biergarten", "marketplace",
                "theatre", "cinema", "arts_centre", "music_venue", "nightclub", "events_venue", "community_centre",
                "conference_centre", "exhibition_centre", "pharmacy", "hospital", "clinic", "atm", "bank", "fuel",
                "charging_station"},
    "shop": {"bakery", "confectionery", "pastry", "clothes", "boutique", "fashion", "fashion_accessories", "shoes",
             "tailor", "jewelry", "bag", "fabric", "mall", "department_store", "books", "stationery", "electronics",
             "mobile_phone", "computer", "supermarket", "gift", "variety_store", "sports", "toys"},
    "tourism": {"hotel", "guest_house", "hostel", "motel", "apartment", "camp_site", "attraction", "viewpoint",
                "artwork", "museum", "gallery"},
    "leisure": {"fitness_centre", "sports_centre", "fitness_station", "park", "garden", "swimming_pool"},
    "sport": {"yoga"},
    "water": {"lake", "reservoir", "pond"},
}
KEEP_TAGS = set(RELEVANT) | {"name", "name:en", "name:kn", "name:hi", "cuisine", "phone", "contact:phone", "website",
                             "contact:website", "addr:street", "addr:suburb", "addr:city", "addr:state", "stars",
                             "clothes", "description"}


def relevant(tags) -> bool:
    if "name" not in tags:
        return False
    return any(tags.get(k) in vals for k, vals in RELEVANT.items())


class Collector(osmium.SimpleHandler):
    def __init__(self, bbox):
        super().__init__()
        self.s, self.w, self.n, self.e = bbox
        self.out = []

    def inside(self, lat, lon):
        return self.s <= lat <= self.n and self.w <= lon <= self.e

    def emit(self, kind, obj, lat, lon):
        tags = {t.k: t.v for t in obj.tags if t.k in KEEP_TAGS}
        self.out.append({
            "type": kind, "id": obj.id, "lat": round(lat, 6), "lon": round(lon, 6),
            "version": obj.version, "timestamp": obj.timestamp.strftime("%Y-%m-%dT%H:%M:%SZ"), "tags": tags,
        })

    def node(self, n):
        if n.location.valid() and relevant(n.tags) and self.inside(n.location.lat, n.location.lon):
            self.emit("node", n, n.location.lat, n.location.lon)

    def way(self, w):
        if not relevant(w.tags):
            return
        pts = [(nd.lat, nd.lon) for nd in w.nodes if nd.location.valid()]
        if not pts:
            return
        lat = sum(p[0] for p in pts) / len(pts)
        lon = sum(p[1] for p in pts) / len(pts)
        if self.inside(lat, lon):
            self.emit("way", w, lat, lon)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pbf")
    ap.add_argument("--bbox", required=True, help="south,west,north,east")
    ap.add_argument("--region", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    bbox = tuple(float(x) for x in a.bbox.split(","))
    c = Collector(bbox)
    with tempfile.TemporaryDirectory() as tmp:
        # Node locations on disk, so a 500+ MB extract processes in modest RAM.
        c.apply_file(a.pbf, locations=True, idx=f"sparse_file_array,{tmp}/nodes.idx")
    pack = {
        "region": a.region,
        "bbox": list(bbox),
        "attribution": "Data (c) OpenStreetMap contributors, ODbL. https://www.openstreetmap.org/copyright",
        "elements": c.out,
    }
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(pack, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(c.out)} places -> {a.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
