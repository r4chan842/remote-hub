#!/usr/bin/env python3
"""Aggregate JUnit XML produced by Gradle and fail when tests are missing or red."""
import glob
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    files = glob.glob("composeApp/build/test-results/**/*.xml", recursive=True)
    total = failures = errors = skipped = 0
    names = []
    for path in files:
        root = ET.parse(path).getroot()
        total += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
        names.append(root.get("name", path))
    print(f"suites={len(files)} tests={total} failures={failures} errors={errors} skipped={skipped}")
    for name in sorted(names):
        print(f"  {name}")
    if total == 0:
        print("no tests were executed", file=sys.stderr)
        return 1
    if failures or errors:
        print("test failures present", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
