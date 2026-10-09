// Aspect ratio of the TV picture (Graphics menu, WWHD_ASPECT): see aspect.cpp.
#pragma once
#include <cstdint>

namespace aspect {
enum Mode : int { kOriginal = 0, kWindow, k16x10, k21x9, k32x9, kCustom, kModes };
int mode();
void set_mode(int m);
const char* mode_name(int m);
float mode_value(int m);           // fixed aspect of a mode (0 for kWindow)
void set_window_aspect(float a);   // shape of the TV window's drawable (render thread, every present)
float requested();                 // what the setting asks for now
// main thread, GX2SwapScanBuffers: latch the aspect for the next game frame, update the game's
// projections; returns the aspect the render thread should switch to at this swap
float on_swap();
float game();
uint64_t game_frame();                // swaps so far                      // aspect the game draws with this frame
// HD layouts: the game thread marks uploads of layout projection matrices and the drawing of layout
// roots; the render thread, which knows the target, narrows the projection for TV-shaped targets
// and reports where each root went
bool tagged_projection();                          // gx2: the vertex uniform upload in progress is one
void layout_root_target(uint32_t root, bool tv);   // render thread (OP_LAYOUT_ROOT)
// the player's HUD positions: offsets in 1280x720 layout pixels (x right, y down) per part
enum HudPart : int { kHudHearts = 0, kHudRupees, kHudButtons, kHudKeys, kHudCompass, kHudParts };
void set_hud_offset(int part, int dx, int dy);
int hud_offset(int part, int axis);
void set_hud_scale(int part, int pct, bool hidden);  // size in percent, or not drawn
void refresh_hud();  // every layout recomputes its matrices (the editor wants fresh bounds)
// per part {left, bottom, right, top} in layout units (centre 0, y up; NaN: not seen yet), then kx, ky
void hud_bounds(float out[kHudParts * 4 + 2]);
bool skippable_scene();  // a scene the game lets + skip is playing (its Skip prompt's layout runs)
void ss_reset();  // save state loaded: every layout recomputes its matrices once
}  // namespace aspect
