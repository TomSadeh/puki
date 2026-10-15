#!/usr/bin/env python3
"""Generates the companion parrot (פוקי) vector drawables.

Writes ic_companion_parrot.xml (beak closed) and ic_companion_parrot_talk.xml
(beak open) to app/src/main/res/drawable. The parrot faces left, toward the
middle of the screen when it sits in the bottom-start corner of a Hebrew (RTL) layout.
"""

import os

DRAWABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:g},{cy:g} A{rx:g},{ry:g} 0 1,1 {cx + rx:g},{cy:g} A{rx:g},{ry:g} 0 1,1 {cx - rx:g},{cy:g}Z"


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def path(d, fill=None, stroke=None, width=None, alpha=None):
    attrs = [f'android:pathData="{d}"']
    if fill:
        attrs.append(f'android:fillColor="{fill}"')
    if alpha is not None:
        attrs.append(f'android:fillAlpha="{alpha}"')
    if stroke:
        attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{width}"',
                  'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    return "    <path " + " ".join(attrs) + "/>"


def parrot(talking):
    p = []
    # Tail feathers (behind the body)
    p.append(path("M58,70 Q66,86 60,97 Q54,88 52,74Z", "#E53935"))
    p.append(path("M53,72 Q56,90 48,97 Q45,86 47,73Z", "#1E88E5"))
    p.append(path("M62,68 Q74,82 72,94 Q64,86 57,72Z", "#FDD835"))
    # Body
    p.append(path(ellipse(52, 60, 22, 25), "#43A047"))
    # Belly
    p.append(path(ellipse(48, 66, 13, 16), "#C5E1A5"))
    # Wing, folded at the side
    p.append(path("M60,44 Q78,52 72,74 Q64,80 58,70 Q56,56 60,44Z", "#2E7D32"))
    p.append(path("M64,58 Q72,64 68,74", None, "#1B5E20", 2))
    # Feet gripping a perch
    p.append(path("M24,88 L80,88", None, "#8D6E63", 6))
    p.append(path(ellipse(44, 86, 4.5, 3.5), "#FB8C00"))
    p.append(path(ellipse(56, 86, 4.5, 3.5), "#FB8C00"))
    # Head
    p.append(path(circle(44, 30, 19), "#4CAF50"))
    # Crest feathers
    p.append(path("M44,12 Q42,2 50,4 Q48,9 49,13Z", "#E53935"))
    p.append(path("M48,13 Q52,4 58,8 Q53,11 52,15Z", "#FB8C00"))
    p.append(path("M40,13 Q34,5 40,3 Q41,8 44,12Z", "#FDD835"))
    # Highlight on the head
    p.append(path(ellipse(38, 20, 7, 4), "#FFFFFF", alpha=0.3))
    # Cheek patch
    p.append(path(circle(50, 38, 5), "#FF8A80", alpha=0.8))
    # Eye: white, big dark pupil, two highlights
    p.append(path(circle(40, 28, 8.5), "#FFFFFF"))
    p.append(path(circle(38.5, 28.5, 5.5), "#3E2723"))
    p.append(path(circle(36.8, 26.5, 2), "#FFFFFF"))
    p.append(path(circle(40.5, 30.5, 1), "#FFFFFF"))
    # Hooked beak, pointing left
    if talking:
        p.append(path("M30,36 Q22,42 26,48 Q30,46 34,42Z", "#E65100"))      # lower beak, dropped open
        p.append(path(ellipse(30, 40, 3, 2.2), "#C62828"))                   # mouth
        p.append(path("M34,26 Q18,26 18,36 Q22,34 26,36 Q30,33 35,36Z", "#FFB300"))
    else:
        p.append(path("M30,36 Q24,40 27,44 Q31,43 35,40Z", "#E65100"))
        p.append(path("M34,26 Q18,26 18,36 Q22,38 26,38 Q30,37 35,38Z", "#FFB300"))
    p.append(path(ellipse(27, 29, 3, 1.5), "#FFFFFF", alpha=0.45))
    return "\n".join(p)


def write(name, talking):
    xml = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="100dp"\n    android:height="100dp"\n'
        '    android:viewportWidth="100"\n    android:viewportHeight="100">\n'
        f"{parrot(talking)}\n</vector>\n"
    )
    with open(os.path.join(DRAWABLE, f"{name}.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(xml)


if __name__ == "__main__":
    write("ic_companion_parrot", talking=False)
    write("ic_companion_parrot_talk", talking=True)
    print("wrote ic_companion_parrot.xml and ic_companion_parrot_talk.xml")
