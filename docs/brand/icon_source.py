"""Approved concept B (Sangam monogram) -> SVG layers per app: foreground, monochrome, full (Play Store)."""
import json, sys

CRIMSON, SAFFRON, GREEN, CHARCOAL, WHITE = "#B83227", "#E09619", "#1F5F2C", "#141318", "#FFFFFF"
BG = {"customer": CRIMSON, "admin": CHARCOAL, "staff": GREEN}
S_TOP = "M66 38.5c-4-5.5-21-6-23.6 2.8-2.4 8.2 8 10.9 12.1 12.9"
S_BOT = "M54.5 54.2c4.1 2 14.3 5.4 11.7 13.4-2.8 8.6-19.7 8.2-23.7 2.6"
LEAF = "M69.5 30.5c4.5-1 7.5 1.5 7.8 5.8-4.6.9-7.6-1.3-7.8-5.8z"

def mark(top, bot, leaf):
    return (f'<path d="{S_TOP}" fill="none" stroke="{top}" stroke-width="9.5" stroke-linecap="round"/>'
            f'<path d="{S_BOT}" fill="none" stroke="{bot}" stroke-width="9.5" stroke-linecap="round"/>'
            f'<path d="{LEAF}" fill="{leaf}"/>')

def glyph(kind, ink):
    if kind == "admin":  # dashboard bars
        return (f'<rect x="64.3" y="69" width="2.4" height="4.5" rx=".6" fill="{ink}"/>'
                f'<rect x="67.9" y="66.5" width="2.4" height="7" rx=".6" fill="{ink}"/>'
                f'<rect x="71.5" y="64.5" width="2.4" height="9" rx=".6" fill="{ink}"/>')
    return (f'<path d="M63.6 72.4h11.2" stroke="{ink}" stroke-width="1.6" stroke-linecap="round"/>'
            f'<path d="M64.8 71.2a4.4 4.4 0 0 1 8.8 0z" fill="{ink}"/><circle cx="69.2" cy="65.6" r="1" fill="{ink}"/>')

def svg(body, defs=""):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>{defs}</defs>{body}</svg>'

def foreground(app):
    body = mark(WHITE, SAFFRON, "#7BC47F")
    if app != "customer":
        fill = SAFFRON if app == "admin" else WHITE
        body += (f'<circle cx="69" cy="69" r="9.6" fill="{BG[app]}"/><circle cx="69" cy="69" r="8" fill="{fill}"/>'
                 + glyph(app, CHARCOAL if app == "admin" else GREEN))
    return body

def mono(app):
    """Single colour: alpha is all that counts, so the badge is cut out with masks instead of coloured ink."""
    if app == "customer":
        return svg(mark(WHITE, WHITE, WHITE))
    defs = ('<mask id="gap"><rect width="108" height="108" fill="#fff"/><circle cx="69" cy="69" r="9.6" fill="#000"/></mask>'
            f'<mask id="ink"><rect width="108" height="108" fill="#fff"/>{glyph(app, "#000")}</mask>')
    return svg(f'<g mask="url(#gap)">{mark(WHITE, WHITE, WHITE)}</g>'
               f'<circle cx="69" cy="69" r="8" fill="{WHITE}" mask="url(#ink)"/>', defs)

out = {app: {"fg": svg(foreground(app)), "mono": mono(app),
             "full": svg(f'<rect width="108" height="108" fill="{BG[app]}"/>' + foreground(app)),
             "bg": BG[app]} for app in BG}
# notification small icon: the plain S silhouette, cropped tighter (status bar icons have no safe-zone margin)
out["notify"] = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="32 22 50 60">' + mark(WHITE, WHITE, WHITE) + '</svg>'
json.dump(out, open(sys.argv[1], "w"))
