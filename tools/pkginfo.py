#!/usr/bin/env python3
"""Extract Activities/Services/Receivers for packages from a dumpsys package dump."""
import re, sys

DUMP = "/Users/spandan/Projects/tcl-debloat/backup/dumpsys_package.txt"

def sections(pkg):
    out = []
    cur = None
    for line in open(DUMP, encoding="utf-8", errors="replace"):
        m = re.match(r"^\s*Package \[(.+?)\]", line)
        if m:
            cur = m.group(1)
            continue
        if cur == pkg:
            out.append(line.rstrip("\n"))
    return "".join(out) if out else None

def usage(pkg):
    out = []
    grab = False
    for line in open(DUMP, encoding="utf-8", errors="replace"):
        if "User 0:" in line or re.match(r"\s+User [0-9]+:", line):
            grab = False
        if line.strip().startswith(pkg):
            grab = True
        if grab:
            out.append(line.rstrip())
    return out

def filter_list(txt, tag):
    idx = txt.find(tag)
    if idx == -1:
        return []
    block = txt[idx:]
    names = []
    for m in re.finditer(r"^ ([A-Za-z0-9_.$]+)", block, re.M):
        names.append(m.group(1))
    return names

if __name__ == "__main__":
    for pkg in sys.argv[1:]:
        s = sections(pkg)
        if s is None:
            print(f"== {pkg}: NOT FOUND (shared user?) ==")
            continue
        print(f"== {pkg} ==")
        for tag in ("Activities:", "Services:", "Receivers:", "Permissions (", "requested permissions:"):
            items = filter_list(s[:s.find(tag)] if False else s, tag)
        # simpler: print raw component areas
        m = re.search(r"Activities:.*?(?=Services:|Receivers:)", s, re.S)
        acts = re.findall(r"^\s{3}([A-Za-z0-9_./$]+)\b", m.group(0), re.M) if m else []
        m2 = re.search(r"Services:.*?(?=Receivers:|Permissions \(|\Z)", s, re.S)
        svcs = re.findall(r"^\s{3}([A-Za-z0-9_./$]+)\b", m2.group(0), re.M) if m2 else []
        m3 = re.search(r"Receivers:.*?(?=Services:|Permissions \(|\Z)", s, re.S)
        recs = re.findall(r"^\s{3}([A-Za-z0-9_./$]+)\b", m3.group(0), re.M) if m3 else []
        u = usage(pkg)
        inst = [x.strip() for x in u if "installed=true" in x or "flags=" in x or "enabled=" in x]
        print(f"  installed: {inst[0] if inst else '?'}")
        print(f"  activities ({len(acts)}): {', '.join(acts[:12])}")
        print(f"  services   ({len(svcs)}): {', '.join(svcs[:12])}")
        print(f"  receivers  ({len(recs)}): {', '.join(recs[:10])}")
