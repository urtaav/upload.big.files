# Design

<!-- impeccable:design-schema 1 -->

## Direction contract

**THESIS** — A multipart upload is a signal routed through parallel channels, so the
console is a broadcast machine-room rack, not a dashboard. It refuses the arrangement this
category always ships: rounded cards floating on a dark field with one accent colour.

**OWN-WORLD** — A near-black rack interior with vertical 19" rails running the full page.
Equipment bolts into them: dark anodized faceplates, one brushed-aluminium panel for the
transport so the eye lands on it by material rather than by colour. Square corners, metal
bevels, real rack screws, silkscreened condensed caps. Colour exists only as lit signal:
tally red for transmitting, fault amber, ready green, playback blue. Meters, lamps, jack
sockets, patch cords, seven-segment counters.

**STORY** — He glances over from the IDE, reads the machine's state from the lamps alone,
sees the thick patch cord carrying bytes bypass the API module entirely, and watches the
signal path run out of wire after COMPLETED.

**FIRST VIEWPORT** — Rails left and right. Top: the brushed-aluminium TRANSPORT panel with
the file engraved on it, an oversized seven-segment byte counter, timecode, and chunky
transport keys. Below it the METER BRIDGE, one channel per part. Right column: the SIGNAL
PATH lamp strip and the FAULT LOG printout.

**FORM** — Broadcast video engineering rack, candidate 3 of the grounded list, assigned by
seed key `d823389e`. Staging: machine room at desk height, panels lit from above.

## The one rule

Every physical element corresponds to a real measured value. A screw is structure; a lamp,
a meter, a counter and a patch cord must each be driven by actual state. Skeuomorphism that
displays nothing is costume, and costume is how this direction fails.

## Colour

Strategy: restrained ground, committed material. Anodized panel greys own roughly half the
surface. Saturation appears only where the equipment would actually emit light.

| Token | Value | Role |
|---|---|---|
| `--rack-void` | `#0A0B0B` | rack interior behind the panels |
| `--rail` | `#171A19` | the 19" mounting rails |
| `--panel-top` / `--panel-bottom` | `#343836` / `#262A28` | anodized faceplate gradient |
| `--panel-recess` | `#151817` | inset wells: meters, logs, displays |
| `--alu-top` / `--alu-bottom` | `#C6C3BA` / `#A5A299` | brushed aluminium, transport panel only |
| `--bevel-light` / `--bevel-dark` | `rgba(255,255,255,.14)` / `rgba(0,0,0,.55)` | 1px metal edges |
| `--silkscreen` | `#CFD3CE` | lettering on dark panels |
| `--engraved` | `#2A2C2A` | lettering on aluminium |
| `--tally` | `#FF3B22` | transmitting |
| `--fault` | `#FFA51F` | retry, warning, paused |
| `--ready` | `#2FD06A` | stored and acknowledged |
| `--playback` | `#59C7FF` | restored from the server |

Lamp colour follows machine-room convention, not web convention: a part **in flight** burns
tally red because it is transmitting, and a **fault** is amber and blinks. They differ in
both hue and behaviour, and every lamp is engraved with its own label, so the inversion
reads correctly.

## Type

| Family | Use |
|---|---|
| **Archivo Narrow** | silkscreen panel lettering, caps, tracked `.12em`, and all prose |
| **Martian Mono** | measurement: part numbers, counts, timecode, ETags, log columns |
| CSS-drawn seven segments | the byte counter and the elapsed clock only |

No display face above 6rem. The seven-segment counter is drawn, not set.

## Components

Panels replace cards. A panel is square-cornered (2px), carries a 1px light top edge and a
dark bottom edge, four screws in its ears, and a silkscreened title on the left of the
faceplate. Nested panels do not exist; a panel contains recessed wells instead.

- **Meter bridge** — one vertical channel per part, with a tally lamp at its head. Channels
  become hairlines as the part count grows, so five hundred parts stay one legible object.
- **Patch bay** — labelled jack sockets joined by patch cords with real catenary sag. The
  browser-to-storage cord is the heaviest gauge on the panel and never passes through the
  API jack. This is the surface's signature element.
- **Signal path** — the lifecycle as a vertical lamp strip. `PROCESSING` and `READY` are
  drawn with no wire running to them, because nothing drives those transitions.
- **Transport keys** — chunky rectangular keys with an integrated LED, silkscreen caps.

## Motion

One authored moment: a power-on self-test when a session is created. Lamps sweep the rack
in sequence and every meter slams to full scale and falls back, the way real equipment
proves its indicators at power-up.

Meters use PPM ballistics, not linear interpolation: fast attack, slow decay. The
difference is visible and it is the honest depiction of a transfer rate.

Everything else stays still. A machine room is quiet until something happens.
