#!/usr/bin/env python3
"""Parse dumpsys package resolver tables: which actions/intents each package handles."""
import re, sys, collections

DUMP = "/Users/spandan/Projects/tcl-debloat/backup/dumpsys_package.txt"

handles = collections.defaultdict(set)
cur_action = None
skip_re = re.compile(r"^\s+(application|audio|video|text|image|vnd\.|android/|time/|http|https|ftp|rtsp|mailto|content|market|package|file|data)\b")
for line in open(DUMP, encoding="utf-8", errors="replace"):
    if re.match(r"^\s*Activity Resolver Table", line):
        cur_action = None
    m = re.match(r"^\s*([A-Za-z][A-Za-z0-9._]+):$", line)
    if m and not skip_re.match(line) and "." in m.group(1) and not m.group(1).endswith("."):
        cur_action = m.group(1)
        continue
    mt = re.match(r"^\s+([0-9a-f]{6,8})\s+([A-Za-z0-9._/]+(?:\s*\(\d+ filters?\))?)\s*$", line)
    if mt and cur_action:
        pkg = mt.group(2).split("/")[0]
        if "." in pkg:
            handles[pkg].add(cur_action)

def report(pkgs):
    for p in pkgs:
        acts = sorted(handles.get(p, []))
        if acts:
            print(f"{p}: {len(acts)} intents")
            for a in acts:
                print(f"    <- {a}")
        else:
            print(f"{p}: (no resolver entries)")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        report(sys.argv[1:])
    else:
        report(sorted(handles))
