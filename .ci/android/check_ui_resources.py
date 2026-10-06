#!/usr/bin/env python3
"""Catch malformed resource XML and unsafe unthemed StateListDrawable aliases."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID_DRAWABLE = "{http://schemas.android.com/apk/res/android}drawable"


def validate(path: Path) -> None:
    root = ET.parse(path).getroot()
    if path.name == "moonwitch_palettes.xml":
        for style in root.findall("style"):
            for item in style.findall("item"):
                if item.get("name") in {"mwText", "mwMuted"}:
                    if not (item.text or "").startswith("@color/"):
                        raise ValueError(
                            f"{path}: {style.get('name')} text colors must use color resources; "
                            "Material navigation reads their resourceId"
                        )
    if path.parent.name.startswith("drawable") and root.tag == "selector":
        for item in root.findall("item"):
            if item.get(ANDROID_DRAWABLE, "").startswith("?"):
                raise ValueError(
                    f"{path}: selector item uses a theme-only drawable reference; "
                    "resolve it on the View instead (StateListDrawable may inflate without a theme)"
                )


def main() -> int:
    resource_dir = (Path(sys.argv[1]) if len(sys.argv) > 1 else
                    Path(__file__).resolve().parents[2] / "src/android/app/src/main/res")
    files = sorted(resource_dir.rglob("*.xml"))
    if not files:
        print(f"No resource XML found in {resource_dir}", file=sys.stderr)
        return 1
    for path in files:
        try:
            validate(path)
        except (ET.ParseError, ValueError) as error:
            print(error, file=sys.stderr)
            return 1
    print(f"Validated {len(files)} resource XML files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
