# Architecture sketch

```
Camera (CameraX) --frame--> Landmarks (MediaPipe Tasks, 15-30 Hz, smoothed)
        |                                   |
        +--------> GLSL shader chain <------+  (landmarks as uniforms)
                   filter/LUT -> skin smooth (mask) -> brighten -> face warp -> vignette
                        |                          |
                  Preview canvas             Offscreen full-res render
                                                   |
                                  Photo (JPEG) / video encoder -> Gallery -> Share
```

Rules
- One effect pipeline shared by preview, capture, editor, export. Parameters are plain data (`EffectStack`) so any surface can re-render it.
- Landmarks run on a downscaled frame; shaders run at display/export resolution.
- Filters are data (LUT png or param JSON) in `assets/filters/`.
- Editor stores a parameter stack, never baked pixels, until export.

Suggested layout
```
app/src/main/java/com/glowcam/
  camera/   CameraX setup, capture modes, timer, flash
  gl/       renderer, shaders (res/raw), filters, beauty, EffectStack
  face/     MediaPipe wrapper, smoothing, masks
  editor/   crop, rotate, adjust screens (Compose)
  share/    MediaStore export + share intents
  ui/       theme, navigation
```
