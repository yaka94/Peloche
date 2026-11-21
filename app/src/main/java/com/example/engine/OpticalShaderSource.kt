package com.example.engine

import android.opengl.GLES20
import com.example.domain.model.OpticalChainParams
import kotlin.math.max

/**
 * Shared GLSL shaders executing the exact 3-stage physical optical chain:
 * Sensor/Texture -> 1. Lens (Objectif) -> 2. Camera Body (Boîtier) -> 3. Film + Expiration + Lab Dev.
 * Uses universally supported GLSL ES syntax compatible with OpenGL ES 3.0/3.2 contexts across all GPUs.
 */
object OpticalShaderSource {

    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vScreenUv;
        void main() {
            gl_Position = aPosition;
            vScreenUv = aTexCoord;
        }
    """

    private const val FRAGMENT_BODY = """
        precision highp float;
        varying vec2 vScreenUv;

        // Geometry & Timing
        uniform mat4 uTexMatrix;
        uniform sampler2D uOverlayTex;
        uniform float uHasOverlay;
        uniform vec2 uCropScale;
        uniform float uZoom;
        uniform float uTime;
        uniform vec2 uTexelSize;

        // Stage 1: Lens (Objectif)
        uniform float uDistortionK;
        uniform float uVignette;
        uniform float uChromaticAberration;
        uniform float uOpticalSoftness;
        uniform float uHalation;
        uniform float uFlareStrength;
        uniform float uTiltShift;
        uniform float uTiltShiftPos;
        uniform float uTiltShiftVertical;
        uniform float uPlasticScratches;
        uniform float uSwirlyBokeh;

        // Stage 2: Camera Body (Boîtier)
        uniform float uExposureEv;
        uniform float uBodyContrast;
        uniform float uBodyWarmth;
        uniform float uBodySaturation;
        uniform vec3 uBodyShadowTint;
        uniform vec3 uBodyHighlightTint;
        uniform float uLightLeakIntensity;
        uniform float uLightLeakSeed;
        uniform float uInvertedGroundGlass;

        // Stage 3: Film Stock (Pellicule)
        uniform float uIsMonochrome;
        uniform vec3 uFilmRgbGain;
        uniform float uFilmContrast;
        uniform float uFilmSaturation;
        uniform float uFilmToeLift;
        uniform float uFilmShoulder;
        uniform vec3 uFilmShadowTint;
        uniform vec3 uFilmHighlightTint;
        uniform float uGrainSize;
        uniform float uGrainIntensity;
        uniform float uHueShift;
        uniform float uSepiaAmount;
        uniform float uCyanotypeAmount;
        uniform float uInfraredAmount;

        // Stage 3d: Ultimate Rétro 19th-Century Historical Plates & Processes
        uniform float uOrthoUv;
        uniform float uCollodionPlate;
        uniform float uDaguerreTarnish;
        uniform float uAutochromeMosaic;
        uniform float uPaperFiber;
        uniform float uBrushBorder;
        uniform float uPlatinumTone;

        // Stage 3b: Expiration Date Chemistry
        uniform float uExpFogLift;
        uniform vec3 uExpCast;
        uniform float uExpContrastMul;
        uniform float uExpSatMul;

        // Stage 3c: Lab Development Process
        uniform float uDevCrossProcess;
        uniform float uDevBleachBypass;
        uniform float uDevAcutance;

        // Stage 4: Procedural Multi-Filter Cinema / Video Modes (0=Off/Optical, 1=Super8, 2=Silent1920, 3=VHS89, 4=16mmBW, 5=35mmNeon)
        uniform float uVideoFilterMode;

        float hash12(vec2 p) {
            vec3 p3 = fract(vec3(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        float valueNoise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            float a = hash12(i);
            float b = hash12(i + vec2(1.0, 0.0));
            float c = hash12(i + vec2(0.0, 1.0));
            float d = hash12(i + vec2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }

        float fbm3(vec2 p) {
            float v = 0.0;
            float amp = 0.52;
            v += amp * valueNoise(p); p = p * 2.04 + vec2(13.7, 7.1); amp *= 0.5;
            v += amp * valueNoise(p); p = p * 2.02 + vec2(5.3, 19.4); amp *= 0.5;
            v += amp * valueNoise(p);
            return v;
        }

        // Domain-warped organic flow for video-like procedural light leaks, flares & emulsion clouds
        float warpedFbm(vec2 p, float t) {
            vec2 q = vec2(
                fbm3(p + vec2(t * 0.14, -t * 0.09)),
                fbm3(p + vec2(-t * 0.11 + 5.2, t * 0.13 + 1.3))
            );
            return fbm3(p + q * 1.45 + vec2(t * 0.07, -t * 0.05));
        }

        float sq(float x) {
            return x * x;
        }

        vec2 rot2d(vec2 p, float a) {
            float c = cos(a);
            float s = sin(a);
            return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
        }

        vec3 rgb2hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-5;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        vec2 applyDistortion(vec2 centered, float k) {
            float r2 = dot(centered, centered);
            return centered * (1.0 + k * r2);
        }

        vec3 sampleBase(vec2 screenUv) {
            vec2 mapped = (uTexMatrix * vec4(clamp(screenUv, 0.002, 0.998), 0.0, 1.0)).xy;
            return texture2D(uTexture, clamp(mapped, 0.002, 0.998)).rgb;
        }

        vec3 screenBlend(vec3 base, vec3 overlay) {
            return 1.0 - (1.0 - clamp(base, 0.0, 1.0)) * (1.0 - clamp(overlay, 0.0, 1.0));
        }

        float bokehWeight(vec3 c, float boost) {
            float hi = smoothstep(0.62, 1.0, max(c.r, max(c.g, c.b)));
            return 1.0 + boost * hi;
        }

        void main() {
            // Procedural Cinema Clocks:
            // tSlow = smooth continuous drift (light leaks, flares, halation breathing)
            // tFilm = 18 fps celluloid grain cadence
            // tDust = 6 fps mechanical film-gate dust & micro-fiber stepping
            float tSlow = uTime;
            float tFilm = floor(uTime * 18.0);
            float tDust = floor(uTime * 6.0);

            // Screen UV with optional 180-degree Inverted Ground Glass (chambre XIXe sous voile noir)
            // plus subtle mechanical cinema gate weave (Super 8 / Silent 1920 / 16mm) & VHS tracking wobble
            vec2 workUv = (uInvertedGroundGlass > 0.5) ? (vec2(1.0) - vScreenUv) : vScreenUv;
            if (uVideoFilterMode > 0.5) {
                if (uVideoFilterMode < 1.5) {
                    // Mode 1: Super 8 18fps subtle gate weave
                    float wT = floor(tSlow * 18.0);
                    workUv += vec2(hash12(vec2(wT, 1.7)) - 0.5, hash12(vec2(wT, 9.3)) - 0.5) * vec2(0.0045, 0.0075);
                } else if (uVideoFilterMode < 2.5) {
                    // Mode 2: Silent 1920 14fps rougher projector weave
                    float wT = floor(tSlow * 14.0);
                    workUv += vec2(hash12(vec2(wT, 2.3)) - 0.5, hash12(vec2(wT, 13.1)) - 0.5) * vec2(0.006, 0.011);
                } else if (uVideoFilterMode < 3.5) {
                    // Mode 3: VHS 1989 bottom-of-frame tracking wobble + horizontal jitter
                    float bottomBand = smoothstep(0.84, 0.98, workUv.y);
                    float trackWave = sin(workUv.y * 95.0 + tSlow * 28.0) * 0.014 * bottomBand;
                    float lineJitter = (hash12(vec2(floor(workUv.y * 240.0), floor(tSlow * 25.0))) - 0.5) * 0.0045;
                    workUv.x += trackWave + lineJitter;
                } else if (uVideoFilterMode < 4.5) {
                    // Mode 4: 16mm documentary micro gate weave
                    float wT = floor(tSlow * 24.0);
                    workUv += vec2(hash12(vec2(wT, 5.1)) - 0.5, hash12(vec2(wT, 8.7)) - 0.5) * vec2(0.0028, 0.0042);
                } else if (uVideoFilterMode > 6.5 && uVideoFilterMode < 7.5) {
                    // Mode 7: Hi8 Handycam 1996 gentle magnetic line shimmer
                    float lineJit = (hash12(vec2(floor(workUv.y * 280.0), floor(tSlow * 25.0))) - 0.5) * 0.0020;
                    workUv.x += lineJit;
                } else if (uVideoFilterMode > 7.5) {
                    // Mode 8: LomoKino 35mm hand-cranked 8 fps pronounced mechanical vertical frame jitter
                    float wT = floor(tSlow * 8.0);
                    workUv += vec2(hash12(vec2(wT, 4.9)) - 0.5, hash12(vec2(wT, 11.7)) - 0.5) * vec2(0.0075, 0.015);
                }
            }

            // --- STAGE 1: LENS (OBJECTIF) ---
            // Perform center-crop, zoom, and optical distortion in SCREEN SPACE before uTexMatrix
            // so 90°/270° camera sensor rotations in Portrait never swap crop axes!
            vec2 frameCentered = workUv - 0.5;
            vec2 distortedFrame = applyDistortion(frameCentered, uDistortionK);
            vec2 sensorCentered = distortedFrame * uCropScale / max(uZoom, 1.0);
            vec2 baseUv = sensorCentered + 0.5;

            if (baseUv.x < 0.0 || baseUv.x > 1.0 || baseUv.y < 0.0 || baseUv.y > 1.0) {
                gl_FragColor = vec4(0.03, 0.03, 0.03, 1.0);
                return;
            }

            // Lateral Chromatic Aberration (stronger toward image corners) + VHS/Hi8 Y/C horizontal chroma bleed
            float radialDist = length(frameCentered);
            vec2 caOffset = (frameCentered * uCropScale / max(uZoom, 1.0)) * uChromaticAberration * radialDist;
            if (uVideoFilterMode > 2.5 && uVideoFilterMode < 3.5) {
                caOffset += vec2(0.0075 * uCropScale.x / max(uZoom, 1.0), 0.0);
            } else if (uVideoFilterMode > 6.5 && uVideoFilterMode < 7.5) {
                caOffset += vec2(0.0042 * uCropScale.x / max(uZoom, 1.0), 0.0);
            }
            vec3 col;
            col.r = sampleBase(baseUv + caOffset).r;
            col.g = sampleBase(baseUv).g;
            col.b = sampleBase(baseUv - caOffset).b;

            // Aspect-corrected shortest-edge UV scale for 100% resolution-independent optical bokeh,
            // softness, acutance & halation parity across Boutique (600px), Viewfinder (1080p) & Labo (1800px)!
            float maxTexel = max(max(uTexelSize.x, uTexelSize.y), 1.0e-5);
            vec2 uvNorm = uTexelSize / maxTexel;
            vec2 blurUvScale = uvNorm * (uCropScale / max(uZoom, 1.0));

            // 16-tap Dual-Ring Optical Bokeh for Adjustable Tilt-Shift (horizontal or vertical) & Lo-Fi Softness
            float tiltCoord = (uTiltShiftVertical > 0.5) ? workUv.x : workUv.y;
            float tiltBand = abs(tiltCoord - uTiltShiftPos) * 2.0;
            float tiltBlur = uTiltShift * smoothstep(0.14, 0.74, tiltBand);
            float bokehR = uOpticalSoftness * 0.0052 + tiltBlur * 0.028;

            if (bokehR > 0.0005) {
                vec2 rInner = blurUvScale * (bokehR * 0.46);
                vec2 rOuter = blurUvScale * bokehR;
                float hlBoost = 0.75 * tiltBlur;

                vec3 acc = col * 0.50;
                float wSum = 0.50;

                // Inner ring (8 taps at 0, 45, 90... deg)
                vec3 s0 = sampleBase(baseUv + vec2( rInner.x, 0.0));           float w0 = bokehWeight(s0, hlBoost); acc += s0 * w0; wSum += w0;
                vec3 s1 = sampleBase(baseUv + vec2(-rInner.x, 0.0));           float w1 = bokehWeight(s1, hlBoost); acc += s1 * w1; wSum += w1;
                vec3 s2 = sampleBase(baseUv + vec2(0.0,  rInner.y));           float w2 = bokehWeight(s2, hlBoost); acc += s2 * w2; wSum += w2;
                vec3 s3 = sampleBase(baseUv + vec2(0.0, -rInner.y));           float w3 = bokehWeight(s3, hlBoost); acc += s3 * w3; wSum += w3;
                vec3 s4 = sampleBase(baseUv + vec2( rInner.x * 0.707,  rInner.y * 0.707)); float w4 = bokehWeight(s4, hlBoost); acc += s4 * w4; wSum += w4;
                vec3 s5 = sampleBase(baseUv + vec2(-rInner.x * 0.707,  rInner.y * 0.707)); float w5 = bokehWeight(s5, hlBoost); acc += s5 * w5; wSum += w5;
                vec3 s6 = sampleBase(baseUv + vec2( rInner.x * 0.707, -rInner.y * 0.707)); float w6 = bokehWeight(s6, hlBoost); acc += s6 * w6; wSum += w6;
                vec3 s7 = sampleBase(baseUv + vec2(-rInner.x * 0.707, -rInner.y * 0.707)); float w7 = bokehWeight(s7, hlBoost); acc += s7 * w7; wSum += w7;

                // Outer ring (8 taps rotated 22.5 deg for creamy circular aperture disc with zero ghosting)
                vec3 o0 = sampleBase(baseUv + vec2( rOuter.x * 0.924,  rOuter.y * 0.383)); float wo0 = bokehWeight(o0, hlBoost); acc += o0 * wo0; wSum += wo0;
                vec3 o1 = sampleBase(baseUv + vec2(-rOuter.x * 0.924,  rOuter.y * 0.383)); float wo1 = bokehWeight(o1, hlBoost); acc += o1 * wo1; wSum += wo1;
                vec3 o2 = sampleBase(baseUv + vec2( rOuter.x * 0.924, -rOuter.y * 0.383)); float wo2 = bokehWeight(o2, hlBoost); acc += o2 * wo2; wSum += wo2;
                vec3 o3 = sampleBase(baseUv + vec2(-rOuter.x * 0.924, -rOuter.y * 0.383)); float wo3 = bokehWeight(o3, hlBoost); acc += o3 * wo3; wSum += wo3;
                vec3 o4 = sampleBase(baseUv + vec2( rOuter.x * 0.383,  rOuter.y * 0.924)); float wo4 = bokehWeight(o4, hlBoost); acc += o4 * wo4; wSum += wo4;
                vec3 o5 = sampleBase(baseUv + vec2(-rOuter.x * 0.383,  rOuter.y * 0.924)); float wo5 = bokehWeight(o5, hlBoost); acc += o5 * wo5; wSum += wo5;
                vec3 o6 = sampleBase(baseUv + vec2( rOuter.x * 0.383, -rOuter.y * 0.924)); float wo6 = bokehWeight(o6, hlBoost); acc += o6 * wo6; wSum += wo6;
                vec3 o7 = sampleBase(baseUv + vec2(-rOuter.x * 0.383, -rOuter.y * 0.924)); float wo7 = bokehWeight(o7, hlBoost); acc += o7 * wo7; wSum += wo7;

                vec3 bokehCol = acc / max(wSum, 1.0);
                float tsMix = smoothstep(0.02, 0.28, tiltBlur);
                float softMix = clamp(uOpticalSoftness * 0.72, 0.0, 0.78);
                col = mix(col, bokehCol, max(tsMix, softMix));
            }

            // Stage 1a: Authentic 1840 Petzval / Helios Tangential Swirly Bokeh (razor-sharp center, swirling peripheral vortex!)
            if (uSwirlyBokeh > 0.04) {
                float swirlMask = smoothstep(0.16, 0.64, radialDist) * uSwirlyBokeh;
                if (swirlMask > 0.01) {
                    vec2 tangent = vec2(-frameCentered.y, frameCentered.x) / max(radialDist, 0.001);
                    vec2 radDir = frameCentered / max(radialDist, 0.001);
                    vec2 tStep = tangent * blurUvScale * (0.024 * swirlMask);
                    vec2 rStep = radDir * blurUvScale * (0.0045 * swirlMask);

                    vec3 swAcc = col * 0.75;
                    float swW = 0.75;
                    vec3 p1 = sampleBase(baseUv + tStep * 0.45 + rStep * 0.35); float wp1 = bokehWeight(p1, 0.95 * swirlMask); swAcc += p1 * wp1; swW += wp1;
                    vec3 p2 = sampleBase(baseUv - tStep * 0.45 + rStep * 0.35); float wp2 = bokehWeight(p2, 0.95 * swirlMask); swAcc += p2 * wp2; swW += wp2;
                    vec3 p3 = sampleBase(baseUv + tStep * 0.92 - rStep * 0.25); float wp3 = bokehWeight(p3, 1.15 * swirlMask); swAcc += p3 * wp3; swW += wp3;
                    vec3 p4 = sampleBase(baseUv - tStep * 0.92 - rStep * 0.25); float wp4 = bokehWeight(p4, 1.15 * swirlMask); swAcc += p4 * wp4; swW += wp4;
                    vec3 p5 = sampleBase(baseUv + tStep * 1.42 + rStep * 0.15); float wp5 = bokehWeight(p5, 1.30 * swirlMask); swAcc += p5 * wp5; swW += wp5;
                    vec3 p6 = sampleBase(baseUv - tStep * 1.42 + rStep * 0.15); float wp6 = bokehWeight(p6, 1.30 * swirlMask); swAcc += p6 * wp6; swW += wp6;
                    col = mix(col, swAcc / max(swW, 1.0), clamp(swirlMask * 0.94, 0.0, 0.92));
                }
            }

            // Dedicated tight unsharp kernel for Rodinal high-acutance, Tilt-Shift crisp plane & Petzval sharp center
            if (uDevAcutance > 0.02 || uTiltShift > 0.05 || uSwirlyBokeh > 0.25) {
                vec2 sharpStep = blurUvScale * 0.0020;
                vec3 u1 = sampleBase(baseUv + vec2(sharpStep.x, 0.0));
                vec3 u2 = sampleBase(baseUv - vec2(sharpStep.x, 0.0));
                vec3 u3 = sampleBase(baseUv + vec2(0.0, sharpStep.y));
                vec3 u4 = sampleBase(baseUv - vec2(0.0, sharpStep.y));
                vec3 sharpAvg = (u1 + u2 + u3 + u4) * 0.25;
                if (uDevAcutance > 0.02) {
                    col += (col - sharpAvg) * uDevAcutance * 0.68;
                }
                if (uTiltShift > 0.05) {
                    // Crisp miniature focus plane + subtle diorama color pop
                    float focusPlane = 1.0 - smoothstep(0.08, 0.42, tiltBand);
                    col += (col - sharpAvg) * uTiltShift * focusPlane * 0.30;
                    float lTs = dot(col, vec3(0.299, 0.587, 0.114));
                    col = mix(vec3(lTs), col, 1.0 + 0.16 * uTiltShift);
                }
                if (uSwirlyBokeh > 0.25) {
                    // Authentic brass Petzval sweet-spot central crispness
                    float centerSweet = 1.0 - smoothstep(0.10, 0.36, radialDist);
                    col += (col - sharpAvg) * uSwirlyBokeh * centerSweet * 0.28;
                }
            }

            // Resolution-independent Specular Highlight Halation & Silky Multi-Radius Bloom (8-tap dual-ring living glow)
            if (uHalation > 0.02) {
                vec2 rInnerH = blurUvScale * 0.0085;
                vec2 rOuterH = blurUvScale * 0.022;
                vec3 b1 = sampleBase(baseUv + vec2( rInnerH.x,  rInnerH.y));
                vec3 b2 = sampleBase(baseUv + vec2(-rInnerH.x,  rInnerH.y));
                vec3 b3 = sampleBase(baseUv + vec2( rInnerH.x, -rInnerH.y));
                vec3 b4 = sampleBase(baseUv + vec2(-rInnerH.x, -rInnerH.y));
                vec3 b5 = sampleBase(baseUv + vec2( rOuterH.x, 0.0));
                vec3 b6 = sampleBase(baseUv + vec2(-rOuterH.x, 0.0));
                vec3 b7 = sampleBase(baseUv + vec2(0.0,  rOuterH.y));
                vec3 b8 = sampleBase(baseUv + vec2(0.0, -rOuterH.y));
                vec3 innerAvg = (b1 + b2 + b3 + b4) * 0.25;
                vec3 outerAvg = (b5 + b6 + b7 + b8) * 0.25;
                float innerBright = smoothstep(0.58, 0.96, max(innerAvg.r, max(innerAvg.g, innerAvg.b)));
                float outerBright = smoothstep(0.54, 0.96, max(outerAvg.r, max(outerAvg.g, outerAvg.b)));
                float halShimmer = 0.92 + 0.08 * sin(tSlow * 1.15 + radialDist * 4.5 + uLightLeakSeed * 6.283);
                // Creamy diffused bloom + rich coral-crimson anti-halation backing dye bleed
                vec3 silkyGlow = (innerAvg * innerBright * 0.34 + outerAvg * outerBright * 0.26) * halShimmer;
                vec3 warmHalation = vec3(1.0, 0.38, 0.14) * (innerBright * 0.32 + outerBright * 0.26) * halShimmer;
                col = screenBlend(col, (silkyGlow + warmHalation) * uHalation);
            }

            // 100% Procedural Living Optical Lens Flares & Prismatic Caustics (4 animated signatures)
            if (uFlareStrength > 0.02) {
                // Physical ghosting reflected across the optical axis from real bright specular zones
                vec3 opposite = sampleBase(vec2(1.0 - baseUv.x, 1.0 - baseUv.y));
                float oppBright = smoothstep(0.64, 0.98, max(opposite.r, max(opposite.g, opposite.b)));
                float flareMode = floor(fract(uLightLeakSeed * 9.17 + 0.23) * 4.0);
                // Smooth organic incidence drift as if handheld optical glass gently catches stray light
                float angleDrift = sin(tSlow * 0.38 + uLightLeakSeed * 6.283) * 0.17 + cos(tSlow * 0.21 + 1.3) * 0.07;
                float axisAngle = fract(uLightLeakSeed * 5.71) * 6.28318 + angleDrift;
                float flareBreath = 0.86 + 0.16 * sin(tSlow * 0.74 + uLightLeakSeed * 11.3);
                vec2 lightOrigin = vec2(0.5 + cos(axisAngle) * 0.44, 0.5 + sin(axisAngle) * 0.42);
                vec2 axisVec = vec2(0.5) - lightOrigin;

                // Axis-opposed live ghost scaled by real scene highlights
                vec3 liveGhostTint = mix(vec3(0.40, 0.78, 1.0), vec3(1.0, 0.62, 0.32), step(1.5, flareMode));
                col += liveGhostTint * oppBright * uFlareStrength * 0.42 * flareBreath;

                vec3 flareAccum = vec3(0.0);
                if (flareMode < 0.5) {
                    // Mode 0: Multi-ring chromatic hexagonal aperture ghosts breathing along optical axis
                    vec2 g1 = lightOrigin + axisVec * (1.32 + 0.06 * sin(tSlow * 0.55));
                    vec2 g2 = lightOrigin + axisVec * (1.82 - 0.05 * cos(tSlow * 0.48));
                    vec2 g3 = lightOrigin + axisVec * (0.64 + 0.04 * sin(tSlow * 0.67 + 1.1));
                    vec2 d1 = vScreenUv - g1;
                    float ang1 = atan(d1.y, d1.x + 1.0e-5);
                    float hex1 = 1.0 + 0.045 * cos(ang1 * 6.0 + axisAngle);
                    float r1 = length(d1) * hex1;
                    float r2 = length(vScreenUv - g2);
                    float r3 = length(vScreenUv - g3);
                    float ring1R = exp(-sq((r1 - 0.106) * 26.0));
                    float ring1G = exp(-sq((r1 - 0.112) * 26.0));
                    float ring1B = exp(-sq((r1 - 0.119) * 26.0));
                    float core1 = exp(-r1 * r1 * 45.0) * 0.26;
                    float disc2 = exp(-r2 * r2 * 82.0) * 0.52;
                    float ring3 = exp(-sq((r3 - 0.07) * 32.0)) * 0.48;
                    flareAccum += vec3(0.35 * ring1R + core1, 0.78 * ring1G + core1, 1.0 * ring1B + core1) * 0.65;
                    flareAccum += vec3(1.0, 0.64, 0.28) * disc2;
                    flareAccum += vec3(0.84, 0.42, 0.96) * ring3;
                } else if (flareMode < 1.5) {
                    // Mode 1: Flowing Anamorphic horizontal streak + breathing golden core
                    float yPos = 0.20 + fract(uLightLeakSeed * 11.3) * 0.60 + sin(tSlow * 0.35) * 0.025;
                    float xCore = fract(uLightLeakSeed * 4.3 + sin(tSlow * 0.28) * 0.06);
                    float streakNoise = 0.45 + 0.55 * warpedFbm(vScreenUv * vec2(3.2, 8.5) + vec2(uLightLeakSeed * 7.0, 0.0), tSlow * 0.9);
                    float streak = exp(-abs(vScreenUv.y - yPos) * 22.0) * streakNoise;
                    float streakThin = exp(-abs(vScreenUv.y - yPos) * 85.0) * (0.55 + 0.45 * sin(vScreenUv.x * 18.0 - tSlow * 1.4));
                    float core = exp(-length((vScreenUv - vec2(xCore, yPos)) * vec2(1.5, 4.8)) * 3.4);
                    flareAccum += vec3(0.26, 0.68, 1.0) * (streak * 0.48 + streakThin * 0.30) + vec3(1.0, 0.84, 0.56) * core * 0.58;
                } else if (flareMode < 2.5) {
                    // Mode 2: 100% Procedural Prismatic Diffraction Starburst & Organic Veiling Glare
                    vec2 toOrigin = vScreenUv - lightOrigin;
                    float cornerDist = length(toOrigin);
                    float rayAngle = atan(toOrigin.y, toOrigin.x + 1.0e-5);
                    float cloudVeil = warpedFbm(vScreenUv * 3.8 + uLightLeakSeed * 12.0, tSlow * 0.85);
                    float veil = exp(-cornerDist * cornerDist * 2.4) * (0.58 + 0.48 * cloudVeil);
                    float rays = pow(max(0.5 + 0.5 * cos(rayAngle * 7.0 + tSlow * 0.22 + uLightLeakSeed * 5.0), 0.0), 5.0) * exp(-cornerDist * 2.4);
                    float causticR = exp(-sq((cornerDist - (0.44 + 0.03 * sin(tSlow * 0.6))) * 15.0));
                    float causticG = exp(-sq((cornerDist - (0.465 + 0.03 * sin(tSlow * 0.6))) * 15.0));
                    float causticB = exp(-sq((cornerDist - (0.49 + 0.03 * sin(tSlow * 0.6))) * 15.0));
                    flareAccum += vec3(1.0, 0.72, 0.38) * veil * 0.56;
                    flareAccum += vec3(1.0, 0.84, 0.58) * rays * 0.42;
                    flareAccum += vec3(causticR * 0.46, causticG * 0.38, causticB * 0.52) * (0.55 + 0.45 * cloudVeil) * 0.48;
                } else {
                    // Mode 3: Breathing soft uncoated-glass Newton rainbow arc ghosting
                    float arcShift = 0.025 * sin(tSlow * 0.52 + uLightLeakSeed * 4.0);
                    float arcR = length(vScreenUv - lightOrigin);
                    float arcR_red = exp(-sq((arcR - (0.55 + arcShift)) * 16.0));
                    float arcR_grn = exp(-sq((arcR - (0.578 + arcShift)) * 16.0));
                    float arcR_blu = exp(-sq((arcR - (0.606 + arcShift)) * 16.0));
                    float arcShimmer = 0.78 + 0.22 * fbm3(vScreenUv * 5.0 + vec2(tSlow * 0.2, -tSlow * 0.15));
                    flareAccum += vec3(arcR_red * 0.54, arcR_grn * 0.44, arcR_blu * 0.58) * arcShimmer * 0.62;
                }
                col = screenBlend(col, flareAccum * uFlareStrength * 1.02 * flareBreath);
            }

            // Stage 1b: Toy-Camera Plastic Lens Micro-Scratches, Cleaning Swirls & Milky Abrasion Scuffs
            // Real acrylic/plastic meniscus lenses scatter bright scene light along surface scuffs & t-shirt cleaning swirls!
            if (uPlasticScratches > 0.02) {
                float pS1 = fract(uLightLeakSeed * 17.31 + 0.29);
                float pS2 = fract(uLightLeakSeed * 37.93 + 0.53);
                float pS3 = fract(uLightLeakSeed * 71.17 + 0.11);
                float localLuma = dot(col, vec3(0.299, 0.587, 0.114));
                // Scratches catch bright light dynamically while staying clearly discernible even in midtones
                float lightCatch = 0.42 + 0.58 * smoothstep(0.14, 0.82, localLuma);
                float glintWave = 0.82 + 0.18 * sin(tSlow * 0.95 + radialDist * 7.5 + pS1 * 6.283);
                vec3 plasticScatter = vec3(0.0);

                // A. Concentric circular wiping micro-swirls (classic pocket/t-shirt plastic lens marks)
                vec2 swirlCenter = vec2(0.5 + (pS1 - 0.5) * 0.22, 0.5 + (pS2 - 0.5) * 0.22);
                vec2 sd = vScreenUv - swirlCenter;
                float sr = length(sd);
                float sAng = atan(sd.y, sd.x + 1.0e-5);
                float swirlMask = smoothstep(0.14, 0.42, sr) * smoothstep(0.78, 0.44, sr);
                float arcWindow = smoothstep(0.20, 0.82, sin(sAng * 2.0 + pS3 * 6.283 + sin(tSlow * 0.32) * 0.35));
                float swirlRing1 = exp(-sq((sr - (0.29 + pS1 * 0.09)) * 90.0)) * arcWindow;
                float swirlRing2 = exp(-sq((sr - (0.41 + pS2 * 0.08)) * 78.0)) * smoothstep(0.25, 0.85, cos(sAng * 3.0 - pS1 * 6.283));
                float fineSwirls = pow(max(0.5 + 0.5 * sin(sr * 135.0 + sAng * 1.0 + pS2 * 12.0), 0.0), 12.0) * swirlMask * arcWindow;
                float swirlTotal = (swirlRing1 * 0.62 + swirlRing2 * 0.52 + fineSwirls * 0.48) * glintWave;
                // Subtle warm-prismatic diffraction tint on grazing acrylic scratches
                vec3 swirlTint = mix(vec3(0.95, 0.92, 0.86), vec3(1.0, 0.84, 0.68), 0.5 + 0.5 * sin(sAng * 4.0 + tSlow * 0.6));
                plasticScatter += swirlTint * swirlTotal * 0.34;

                // B. 3 Procedural diagonal & gently curved acrylic gouges (soft milky scatter + crisp core)
                // Scratch 1 (primary diagonal lens nick)
                vec2 sc1Origin = vec2(0.18 + pS1 * 0.64, 0.16 + pS2 * 0.68);
                vec2 sc1 = rot2d(vScreenUv - sc1Origin, pS1 * 3.14159 + 0.42);
                float sc1Curve = sc1.y - sc1.x * sc1.x * (0.45 + pS3 * 0.75);
                float sc1Len = smoothstep(0.26, 0.17, abs(sc1.x));
                float sc1Break = 0.50 + 0.50 * fbm3(vec2(sc1.x * 16.0 + pS2 * 10.0, pS1 * 5.0));
                float sc1Core = exp(-abs(sc1Curve) * 230.0) * sc1Len * sc1Break;
                float sc1Haze = exp(-abs(sc1Curve) * 36.0) * sc1Len * 0.34;

                // Scratch 2 (secondary finer cross-scuff)
                vec2 sc2Origin = vec2(0.14 + pS3 * 0.72, 0.20 + pS1 * 0.60);
                vec2 sc2 = rot2d(vScreenUv - sc2Origin, -pS2 * 3.14159 - 0.78);
                float sc2Curve = sc2.y + sc2.x * sc2.x * 0.60;
                float sc2Len = smoothstep(0.21, 0.13, abs(sc2.x));
                float sc2Core = exp(-abs(sc2Curve) * 280.0) * sc2Len;
                float sc2Haze = exp(-abs(sc2Curve) * 44.0) * sc2Len * 0.28;

                // Scratch 3 (short corner abrasion nick)
                vec2 sc3Origin = vec2(0.76 - pS2 * 0.52, 0.78 - pS3 * 0.54);
                vec2 sc3 = rot2d(vScreenUv - sc3Origin, pS3 * 6.283);
                float sc3Len = smoothstep(0.13, 0.07, abs(sc3.x));
                float sc3Core = exp(-abs(sc3.y) * 320.0) * sc3Len;

                float gougeSum = (sc1Core * 0.72 + sc1Haze * 0.34 + sc2Core * 0.58 + sc2Haze * 0.26 + sc3Core * 0.50);
                plasticScatter += vec3(0.96, 0.93, 0.87) * gougeSum * 0.42 * (0.86 + 0.14 * sin(tSlow * 1.3 + pS3 * 6.283));

                // C. Localized hazy acrylic pocket-scuff patch (soft milky light bloom on highlights)
                vec2 scuffPos = vec2(0.22 + pS2 * 0.56, 0.24 + pS3 * 0.52);
                float scuffPatch = exp(-dot(vScreenUv - scuffPos, vScreenUv - scuffPos) * 14.0) *
                    fbm3(vScreenUv * 10.5 + vec2(pS1 * 13.0, pS2 * 7.0));
                plasticScatter += vec3(0.92, 0.90, 0.85) * scuffPatch * 0.20;

                col = screenBlend(col, plasticScatter * uPlasticScratches * lightCatch);
            }

            // Aperture cosine^4 peripheral vignetting
            float vig = 1.0 - uVignette * smoothstep(0.22, 0.95, radialDist * radialDist * 1.45);
            col *= clamp(vig, 0.18, 1.0);

            // --- STAGE 2: CAMERA BODY (BOÎTIER) ---
            col *= pow(2.0, uExposureEv);

            // Subtle organic mechanical gate / shutter exposure breathing (vintage & expired combos only)
            float vintageGate = clamp(uLightLeakIntensity * 0.85 + uExpFogLift * 2.4, 0.0, 1.0);
            if (vintageGate > 0.03) {
                float gateWave = sin(tSlow * 2.4 + uLightLeakSeed * 9.3) * 0.55 + sin(tSlow * 5.9 + 1.7) * 0.45;
                col *= (1.0 + gateWave * 0.015 * vintageGate);
            }

            // Body generation warmth & tint
            col.r *= (1.0 + uBodyWarmth * 0.55);
            col.b *= (1.0 - uBodyWarmth * 0.55);
            float lumaBody = dot(col, vec3(0.299, 0.587, 0.114));
            col += uBodyShadowTint * (1.0 - smoothstep(0.0, 0.55, lumaBody));
            col += uBodyHighlightTint * smoothstep(0.45, 1.0, lumaBody);

            // Body contrast & saturation
            col = (col - 0.5) * uBodyContrast + 0.5;
            float lumaAfterBody = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(vec3(lumaAfterBody), col, uBodySaturation);

            // 100% Procedural Video-Like Organic Film Light Leaks (8 unmistakable living domain-warped archetypes!)
            // Split into pre-emulsion reaction + post-shoulder incandescent glow so bright vermilion/amber burns stay luminous!
            vec3 postLeakGlow = vec3(0.0);
            if (uLightLeakIntensity > 0.01 || uExpFogLift > 0.025) {
                float s1 = fract(uLightLeakSeed * 13.73 + 0.17);
                float s2 = fract(uLightLeakSeed * 29.31 + 0.61);
                float s3 = fract(uLightLeakSeed * 53.19 + 0.37);
                float archetype = floor(s1 * 8.0);
                // Healthy per-shot intensity without triple-attenuation crush
                float expLeakFallback = (uExpFogLift > 0.025) ? min(uExpFogLift * 4.6, 0.62) : 0.0;
                float effectiveLeakIntensity = max(uLightLeakIntensity, expLeakFallback);
                float baseShotStrength = effectiveLeakIntensity * mix(0.84, 1.24, s2);
                vec3 leakAccum = vec3(0.0);

                // Flip coordinates randomly per shot so leaks enter from any edge/corner
                vec2 p = vScreenUv;
                if (s2 > 0.5) p.x = 1.0 - p.x;
                if (s3 > 0.5) p.y = 1.0 - p.y;

                // Domain-warped flowing plasma cloud + gentle breathing sweep
                float nCloud = warpedFbm(p * vec2(2.6, 3.6) + vec2(s1 * 17.0, s3 * 23.0), tSlow * 1.15);
                float nFine = fbm3(p * vec2(6.2, 5.0) + vec2(-tSlow * 0.22, tSlow * 0.16) + s2 * 11.0);
                float leakBreath = 0.88 + 0.14 * sin(tSlow * 0.82 + s2 * 6.283 + nCloud * 2.2);
                float sweepDrift = sin(tSlow * 0.52 + s3 * 6.283) * 0.048;

                if (uLightLeakIntensity <= 0.01) {
                    // Authentic expired-roll corner chemical light-fog veil on clean bodies
                    float expCorner = exp(-length((p + vec2(sweepDrift, -sweepDrift)) * vec2(1.18, 1.12)) * 2.35) * (0.58 + 0.55 * nCloud);
                    leakAccum = vec3(1.0, 0.38, 0.14) * expCorner * 0.92 + vec3(0.95, 0.16, 0.28) * expCorner * 0.35;
                } else if (archetype < 0.5) {
                    // 0. Vertical Door-Hinge Edge Burn (vivid crimson-amber strip + incandescent pale-gold core)
                    float edgeW = 0.14 + s3 * 0.15 + sweepDrift * 0.45;
                    float px = max(p.x + (nCloud - 0.5) * 0.085, 0.0);
                    float band = exp(-sq(px / max(edgeW, 0.05))) * (0.64 + 0.58 * nCloud);
                    float hotCore = exp(-sq(px / max(edgeW * 0.38, 0.02))) * (0.80 + 0.25 * nFine);
                    leakAccum = vec3(1.0, 0.24, 0.04) * band * 1.05 + vec3(1.0, 0.86, 0.42) * hotCore * 0.72;
                } else if (archetype < 1.5) {
                    // 1. Corner Sprocket Bleeding (drifting diagonal amber leak with living 35mm sprocket shadow bands)
                    float dCorner = length((p + vec2(sweepDrift * 0.7, 0.0)) * vec2(1.05, 0.88));
                    float sprocketShadow = 0.72 + 0.28 * sin(p.y * 32.0 + s2 * 6.283 + tSlow * 1.15);
                    float spill = exp(-dCorner * dCorner * (2.6 + s3 * 1.6)) * sprocketShadow * (0.65 + 0.50 * nCloud);
                    leakAccum = vec3(1.0, 0.48, 0.10) * spill * 1.12 + vec3(0.98, 0.14, 0.08) * spill * (0.38 + 0.24 * nFine);
                } else if (archetype < 2.5) {
                    // 2. Rear Film-Base Crimson Fog Veil (smoky liquid scarlet plumes washing across backing)
                    float wash = smoothstep(0.68 + sweepDrift, 0.0, p.x * 0.80 + p.y * 0.22 + (nCloud - 0.5) * 0.38);
                    vec3 deepRuby = vec3(0.94, 0.12, 0.16);
                    vec3 warmEdge = vec3(1.0, 0.48, 0.12);
                    leakAccum = mix(deepRuby, warmEdge, smoothstep(0.38, 0.88, nCloud)) * wash * (0.62 + 0.48 * nCloud) * 1.04;
                } else if (archetype < 3.5) {
                    // 3. Horizontal Shutter-Curtain Slit Ribbon (asymmetric warm peach-gold living streak)
                    float slitY = 0.12 + s3 * 0.28 + sweepDrift;
                    float waveY = slitY + (nCloud - 0.5) * 0.08;
                    float ribbon = exp(-sq((p.y - waveY) * (5.4 + s2 * 3.8))) * smoothstep(0.96, 0.08, p.x) * (0.64 + 0.56 * nCloud);
                    float filament = exp(-sq((p.y - waveY) * 16.0)) * smoothstep(0.84, 0.04, p.x) * (0.65 + 0.35 * nFine);
                    leakAccum = vec3(1.0, 0.54, 0.16) * ribbon * 1.02 + vec3(1.0, 0.90, 0.52) * filament * 0.65;
                } else if (archetype < 4.5) {
                    // 4. Diagonal Holga Bakelite Seam Leak (curved drifting amber beam + counter-breathing magenta stress fog)
                    float diag = abs((p.x * 0.84 + p.y * 0.54) - (0.30 + s3 * 0.34 + sweepDrift));
                    float beam = exp(-diag * diag * 15.0) * (0.62 + 0.58 * nCloud);
                    float oppPulse = 0.82 + 0.18 * cos(tSlow * 0.9 + s1 * 6.283);
                    float oppFog = exp(-length((1.0 - p) * 1.25) * 2.1) * (0.70 + 0.30 * nFine) * 0.62 * oppPulse;
                    leakAccum = vec3(1.0, 0.44, 0.10) * beam * 1.04 + vec3(0.92, 0.18, 0.58) * oppFog;
                } else if (archetype < 5.5) {
                    // 5. 100% Procedural Liquid Film-Burn Plasma & Chromatic Dispersion Ribbon
                    vec2 rotP = rot2d(p - 0.5, s2 * 6.283 + sin(tSlow * 0.24) * 0.18) + 0.5;
                    float plasmaA = warpedFbm(rotP * vec2(2.9, 4.1) + vec2(s1 * 9.0, 0.0), tSlow * 1.35);
                    float plasmaB = warpedFbm(rotP * vec2(5.0, 2.7) + vec2(0.0, s3 * 13.0), -tSlow * 1.05);
                    float burnMask = smoothstep(0.24, 0.82, plasmaA * 0.64 + plasmaB * 0.44 + (1.0 - rotP.x) * 0.32);
                    float coreBurn = smoothstep(0.56, 0.90, plasmaA * 0.60 + plasmaB * 0.55);
                    vec3 outerHalo = vec3(0.96, 0.14, 0.34);
                    vec3 midFlame = vec3(1.0, 0.44, 0.08);
                    vec3 hotGold = vec3(1.0, 0.92, 0.58);
                    vec3 burnCol = mix(outerHalo, midFlame, smoothstep(0.20, 0.68, plasmaA));
                    burnCol = mix(burnCol, hotGold, coreBurn * 0.78);
                    leakAccum = burnCol * burnMask * 1.08;
                } else if (archetype < 6.5) {
                    // 6. Twin Opposite-Corner Warm/Crimson Glow (breathing smoothly out of phase)
                    float pulseA = 0.84 + 0.16 * sin(tSlow * 0.88 + s1 * 6.283);
                    float pulseB = 0.84 + 0.16 * cos(tSlow * 0.74 + s2 * 6.283);
                    float cA = exp(-length((p + vec2(sweepDrift * 0.6, 0.0)) * vec2(1.15, 1.15)) * 2.55) * (0.68 + 0.45 * nCloud) * pulseA;
                    float cB = exp(-length(((1.0 - p) - vec2(0.0, sweepDrift * 0.6)) * vec2(1.22, 1.22)) * 2.80) * (0.62 + 0.45 * nFine) * pulseB;
                    leakAccum = vec3(1.0, 0.54, 0.12) * cA * 1.02 + vec3(0.96, 0.14, 0.20) * cB * 0.88;
                } else {
                    // 7. Warm Amber Top/Edge Sprocket Curtain Burn (tasteful edge leak, unmistakably present!)
                    float curtain = exp(-sq((p.x + sweepDrift * 0.5) / 0.24)) * (0.58 + 0.56 * nCloud);
                    float cornerFlare = exp(-length(p * vec2(1.2, 1.0)) * 2.4) * (0.65 + 0.35 * nFine);
                    leakAccum = vec3(1.0, 0.42, 0.10) * curtain * 0.86 + vec3(1.0, 0.74, 0.30) * cornerFlare * 0.62;
                }

                vec3 totalLeak = leakAccum * baseShotStrength * leakBreath;
                // Stage 2 pre-emulsion reaction (60%) + post-shoulder incandescent film burn (48%)
                col = screenBlend(col, totalLeak * 0.60);
                postLeakGlow = totalLeak * 0.48;
            }

            // --- STAGE 3: FILM EMULSION (PELLICULE) ---
            col = clamp(col, 0.0, 1.35);

            // Experimental Aerochrome Infrared (foliage green -> crimson/magenta)
            if (uInfraredAmount > 0.02) {
                float foliage = clamp((col.g - max(col.r, col.b)) * 3.0, 0.0, 1.0);
                vec3 irCol = vec3(
                    clamp(col.g * 1.38 + col.r * 0.22, 0.0, 1.0),
                    clamp(col.r * 0.55 + col.b * 0.25, 0.0, 1.0),
                    clamp(col.b * 1.15 + col.g * 0.18, 0.0, 1.0)
                );
                col = mix(col, irCol, uInfraredAmount * (0.45 + 0.55 * foliage));
            }

            // Experimental Hue Shift (Lomo Purple / Turquoise)
            if (abs(uHueShift) > 0.01) {
                vec3 hsv = rgb2hsv(clamp(col, 0.0, 1.0));
                hsv.x = fract(hsv.x + uHueShift);
                col = hsv2rgb(hsv);
            }

            // Film RGB sensitometric gains
            col *= uFilmRgbGain;

            // Monochrome silver halide conversion (with optional authentic 19th-century Orthochromatic UV-Blue spectrum!)
            if (uIsMonochrome > 0.5 || uOrthoUv > 0.05) {
                float stdBw = dot(col, vec3(0.32, 0.56, 0.12));
                // True 1850s collodion/daguerreotype plates are blue/UV sensitive & blind to red (lips/reds go charcoal, blue skies pale!)
                float orthoBw = clamp(col.b * 0.72 + col.g * 0.25 + col.r * 0.03, 0.0, 1.25);
                float bw = mix(stdBw, orthoBw, clamp(uOrthoUv, 0.0, 1.0));
                if (uIsMonochrome > 0.5) {
                    col = vec3(bw);
                } else {
                    col = mix(col, vec3(bw), uOrthoUv * 0.85);
                }
            }

            // Procedural Non-Uniform Chemical Emulsion Mottling for Expired/Aged Film
            float emulMottle = 1.0;
            if (uExpFogLift > 0.008 || length(uExpCast) > 0.005) {
                float mCloud = fbm3(vScreenUv * 4.4 + vec2(uLightLeakSeed * 19.3, uLightLeakSeed * 11.7) + vec2(tSlow * 0.035, -tSlow * 0.025));
                emulMottle = mix(0.68, 1.38, mCloud);
            }

            // Sensitometric S-curve: creamy analog shoulder roll-off & organic toe lift
            col = clamp(col, 0.0, 1.35);
            vec3 softClip = col / (1.0 + (uFilmShoulder * 1.15) * max(col - 0.52, 0.0));
            col = mix(col, softClip, 0.88);
            col = (col - 0.5) * (uFilmContrast * uExpContrastMul) + 0.5;
            col = mix(vec3(uFilmToeLift + uExpFogLift * emulMottle), vec3(0.985), clamp(col, 0.0, 1.0));

            // 3-way Film Shadow / Highlight split toning
            float lumaFilm = dot(col, vec3(0.299, 0.587, 0.114));
            col += uFilmShadowTint * (1.0 - smoothstep(0.0, 0.52, lumaFilm));
            col += uFilmHighlightTint * smoothstep(0.48, 1.0, lumaFilm);

            // Film Saturation & Expiration desaturation
            float lumaSat = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(vec3(lumaSat), col, uFilmSaturation * uExpSatMul);

            // --- STAGE 3b: FILM EXPIRATION CHEMISTRY ---
            // Aged C-41 shifts green; aged E-6 shifts magenta; aged B&W gets mottled silver veil
            float shadowWeight = 1.0 - smoothstep(0.0, 0.85, lumaSat);
            col += uExpCast * (0.35 + 0.65 * shadowWeight) * emulMottle;

            // --- STAGE 3c: LAB DEVELOPMENT CHEMISTRY ---
            if (uDevCrossProcess > 0.02) {
                float l = dot(col, vec3(0.299, 0.587, 0.114));
                vec3 xpro = vec3(
                    smoothstep(0.05, 0.92, col.r * 1.08),
                    smoothstep(0.02, 0.95, col.g * 1.12),
                    pow(clamp(col.b, 0.0, 1.0), 1.18) * 0.94 + (1.0 - l) * 0.10
                );
                xpro += vec3(-0.02, 0.05, 0.08) * (1.0 - l);
                xpro += vec3(0.06, 0.07, -0.05) * smoothstep(0.5, 1.0, l);
                col = mix(col, xpro, uDevCrossProcess);
            }

            if (uDevBleachBypass > 0.02) {
                float l = dot(col, vec3(0.299, 0.587, 0.114));
                vec3 silver = vec3(smoothstep(0.08, 0.92, l));
                vec3 overlay = mix(2.0 * col * silver, 1.0 - 2.0 * (1.0 - col) * (1.0 - silver), step(0.5, col));
                vec3 bleached = mix(vec3(l), overlay, 0.45);
                col = mix(col, bleached, uDevBleachBypass * 0.85);
            }

            // Sepia, Platinum-Palladium & Cyanotype archival baths (from Film or Lab Process)
            if (uSepiaAmount > 0.02) {
                float l = dot(col, vec3(0.299, 0.587, 0.114));
                vec3 sep = vec3(l * 1.12 + 0.04, l * 0.92 + 0.01, l * 0.66);
                col = mix(col, sep, clamp(uSepiaAmount, 0.0, 1.0));
            }
            if (uPlatinumTone > 0.02) {
                float l = dot(col, vec3(0.299, 0.587, 0.114));
                // Museum Platinum-Palladium champagne-graphite continuous tonal scale
                float softL = smoothstep(0.02, 0.98, l);
                vec3 plat = mix(vec3(0.07, 0.065, 0.058), vec3(0.95, 0.92, 0.86), softL);
                col = mix(col, plat, clamp(uPlatinumTone, 0.0, 1.0));
            }
            if (uCyanotypeAmount > 0.02) {
                float l = dot(col, vec3(0.299, 0.587, 0.114));
                vec3 cyan = mix(vec3(0.05, 0.16, 0.34), vec3(0.88, 0.93, 0.95), smoothstep(0.04, 0.96, l));
                col = mix(col, cyan, clamp(uCyanotypeAmount, 0.0, 1.0));
            }

            // --- STAGE 3d: ULTIMATE RÉTRO 19TH-CENTURY HISTORICAL PLATE & PAPER PHYSICS ---
            // 1. Autochrome Lumière 1907 — Authentic Potato-Starch Trichrome Pigment Grain Mosaic
            if (uAutochromeMosaic > 0.02) {
                vec2 starchCell = floor(gl_FragCoord.xy / 2.25);
                float gHash = hash12(starchCell + vec2(uLightLeakSeed * 37.0, 19.3));
                float gJitter = hash12(starchCell * 1.31 + vec2(4.7, uLightLeakSeed * 13.0));
                vec3 starchFilter;
                if (gHash < 0.34) {
                    // Dyed orange-red starch grain
                    starchFilter = vec3(1.24, 0.90, 0.72);
                } else if (gHash < 0.68) {
                    // Dyed apple-green starch grain
                    starchFilter = vec3(0.82, 1.22, 0.80);
                } else {
                    // Dyed blue-violet starch grain
                    starchFilter = vec3(0.86, 0.84, 1.28);
                }
                float microDark = 0.91 + 0.14 * gJitter;
                vec3 mosaicCol = col * starchFilter * microDark;
                // Subtle pastel impressionist warmth & gentle pigment bloom
                float lAuto = dot(mosaicCol, vec3(0.299, 0.587, 0.114));
                mosaicCol = mix(vec3(lAuto), mosaicCol, 1.12);
                col = mix(col, mosaicCol, clamp(uAutochromeMosaic * 0.72, 0.0, 0.75));
            }

            // 2. Handmade Cotton Rag Paper Fiber Texture (Calotype 1845, Platinum-Palladium 1880, Brush Cyanotype 1842)
            if (uPaperFiber > 0.02) {
                float fH = fbm3(workUv * vec2(18.0, 72.0) + uLightLeakSeed * 11.0);
                float fV = fbm3(workUv * vec2(72.0, 18.0) + uLightLeakSeed * 23.0);
                float rag = ((fH + fV) * 0.5 - 0.5) * 0.17 * uPaperFiber;
                col = clamp(col + vec3(rag * 1.05, rag * 0.98, rag * 0.86), 0.0, 1.0);
            }

            // 3. Hand-Brushed Emulsion Perimeter (Cyanotype 1842 / Salt Print — watercolor rag paper edge!)
            if (uBrushBorder > 0.02) {
                float edgeDist = min(min(workUv.x, 1.0 - workUv.x), min(workUv.y, 1.0 - workUv.y));
                float brushNoise = fbm3(workUv * vec2(14.0, 32.0) + uLightLeakSeed * 17.0) * 0.048 +
                    fbm3(workUv * vec2(32.0, 14.0) + uLightLeakSeed * 29.0) * 0.026;
                float paperMask = smoothstep(0.025 + brushNoise, 0.008 + brushNoise * 0.45, edgeDist);
                vec3 ragCream = vec3(0.93, 0.90, 0.83) - 0.05 * fbm3(workUv * 45.0);
                col = mix(col, ragCream, paperMask * clamp(uBrushBorder, 0.0, 1.0));
            }

            // 4. Wet-Plate Collodion / Ambrotype / Tintype 1855 (Irregular poured corners, silver pooling & oyster-shell ripples!)
            if (uCollodionPlate > 0.02) {
                float cEdge = min(min(workUv.x, 1.0 - workUv.x), min(workUv.y, 1.0 - workUv.y));
                float cornerDist = length((workUv - 0.5) * vec2(1.0, 1.0));
                float pourNoise = warpedFbm(workUv * 5.2 + vec2(uLightLeakSeed * 19.0, uLightLeakSeed * 7.0), 0.0);
                float finePour = fbm3(workUv * 18.0 + uLightLeakSeed * 31.0);
                // Irregular hand-poured collodion perimeter & un-coated corner glass/tin
                float edgeRaw = smoothstep(0.055 + pourNoise * 0.065, 0.005 + pourNoise * 0.02, cEdge);
                float cornerRaw = smoothstep(0.56 + pourNoise * 0.14, 0.72 + pourNoise * 0.08, cornerDist);
                float pourMask = clamp(max(edgeRaw, cornerRaw * 0.92), 0.0, 1.0);
                // Thickened pale silver-nitrate meniscus ridge right along the pour boundary
                float silverRidge = smoothstep(0.08, 0.45, pourMask) * smoothstep(0.85, 0.35, pourMask);
                // Subtle "oyster shell" pouring waves & silver bath mottle
                float oyster = sin(cornerDist * 34.0 + pourNoise * 6.5) * 0.5 + 0.5;
                float oysterMask = smoothstep(0.38, 0.65, cornerDist) * smoothstep(0.82, 0.52, cornerDist);
                col = col * (1.0 - pourMask * 0.84 * uCollodionPlate) +
                    vec3(0.07, 0.065, 0.055) * pourMask * 0.65 * uCollodionPlate;
                col += vec3(0.24, 0.22, 0.18) * silverRidge * (0.45 + 0.55 * finePour) * uCollodionPlate;
                col += vec3(0.09, 0.085, 0.075) * oyster * oysterMask * 0.55 * uCollodionPlate;
                // Sparse dark silver-bath chemical pinholes
                vec2 pinCell = floor(workUv * 24.0);
                float pinSeed = hash12(pinCell + vec2(uLightLeakSeed * 71.0, 13.7));
                if (pinSeed > 0.978) {
                    vec2 pLocal = fract(workUv * 24.0) - vec2(0.5);
                    float pinDot = smoothstep(0.16, 0.04, length(pLocal));
                    col *= (1.0 - pinDot * 0.65 * uCollodionPlate);
                }
            }

            // 5. Daguerreotype 1839 — Silvered Copper Mirror Luster, Buffing Lines & Iridescent Oxidized Tarnish Crown
            if (uDaguerreTarnish > 0.02) {
                float dEdge = length((workUv - 0.5) * vec2(1.08, 0.96));
                float tarnishNoise = fbm3(workUv * 6.5 + uLightLeakSeed * 15.0);
                float tarnishRing = smoothstep(0.36, 0.72, dEdge + (tarnishNoise - 0.5) * 0.16);
                // Authentic metallic silver sulfide tarnish gradient: warm brass -> peacock cyan -> dark oxidized violet
                vec3 brassOx = vec3(0.62, 0.44, 0.18);
                vec3 cyanOx = vec3(0.14, 0.34, 0.46);
                vec3 violetOx = vec3(0.18, 0.11, 0.24);
                vec3 oxCol = mix(brassOx, cyanOx, smoothstep(0.22, 0.62, tarnishRing));
                oxCol = mix(oxCol, violetOx, smoothstep(0.58, 0.95, tarnishRing));
                // Horizontal chamois-leather buffing micro-lines across the silver plate
                float buffLines = (valueNoise(vec2(workUv.x * 6.0, workUv.y * 280.0 + uLightLeakSeed * 20.0)) - 0.5) * 0.055;
                col = col + vec3(buffLines * uDaguerreTarnish);
                col = mix(col, col * 0.48 + oxCol * 0.46, tarnishRing * 0.78 * uDaguerreTarnish);
            }

            // --- STAGE 4: DEDICATED MULTI-FILTER CINEMA / VIDEO MODES ---
            if (uVideoFilterMode > 0.5) {
                if (uVideoFilterMode < 1.5) {
                    // Mode 1: SUPER 8 KODACHROME (18 fps warm nostalgia, rich contrast, subtle edge burn)
                    col = vec3(col.r * 1.08, col.g * 1.02, col.b * 0.88);
                    col = (col - 0.5) * 1.14 + 0.5;
                    float s8Vig = 1.0 - 0.38 * smoothstep(0.25, 0.88, radialDist * radialDist * 1.4);
                    col *= clamp(s8Vig, 0.28, 1.0);
                } else if (uVideoFilterMode < 2.5) {
                    // Mode 2: MUET PATHÉ 1920 (14 fps arc-lamp flicker, sepia-silver ortho, vertical projector scratches & iris!)
                    float f14 = floor(tSlow * 14.0);
                    float flicker = 0.88 + 0.22 * hash12(vec2(f14 * 7.3, 19.1));
                    float ortho = clamp(col.b * 0.65 + col.g * 0.30 + col.r * 0.05, 0.0, 1.0);
                    float cOrtho = clamp((ortho - 0.5) * 1.36 + 0.5, 0.0, 1.0) * flicker;
                    col = vec3(cOrtho * 1.06 + 0.02, cOrtho * 0.95, cOrtho * 0.76);
                    // Dancing vertical projector scratches
                    float sx1 = fract(hash12(vec2(floor(tSlow * 4.0), 3.7)) * 7.1 + sin(tSlow * 2.1) * 0.02);
                    float sx2 = fract(sx1 + 0.37);
                    float line1 = exp(-abs(workUv.x - sx1) * 620.0) * 0.42;
                    float line2 = exp(-abs(workUv.x - sx2) * 840.0) * 0.28;
                    col += vec3(0.32, 0.28, 0.22) * line1 - vec3(0.25) * line2;
                    // Classic silent-cinema circular iris vignette
                    float iris = 1.0 - smoothstep(0.36, 0.68, radialDist);
                    col *= (0.14 + 0.86 * iris);
                } else if (uVideoFilterMode < 3.5) {
                    // Mode 3: VHS CAMÉSCOPE 1989 (CRT scanlines, tape noise, slight magenta-teal analog cast & dropout line)
                    float scan = 0.90 + 0.10 * sin(gl_FragCoord.y * 1.95);
                    float vhsNoise = (hash12(vec2(floor(gl_FragCoord.x * 0.5), floor(gl_FragCoord.y * 0.5) + floor(tSlow * 25.0) * 17.0)) - 0.5) * 0.12;
                    col = col * scan + vec3(vhsNoise);
                    col = vec3(col.r * 1.03, col.g * 0.98, col.b * 1.04);
                    // Occasional horizontal white magnetic tape-dropout glitch bar
                    float dropT = floor(tSlow * 6.0);
                    float dropY = hash12(vec2(dropT, 41.3));
                    if (hash12(vec2(dropT, 88.7)) > 0.62) {
                        float dropLine = exp(-abs(workUv.y - dropY) * 260.0) * step(0.35, fbm3(vec2(workUv.x * 16.0, dropT)));
                        col = mix(col, vec3(0.92, 0.94, 0.96), dropLine * 0.55);
                    }
                } else if (uVideoFilterMode < 4.5) {
                    // Mode 4: 16MM NOUVELLE VAGUE N&B (crisp French cinema monochrome + rich silver grain)
                    float bw16 = dot(col, vec3(0.30, 0.58, 0.12));
                    bw16 = clamp((bw16 - 0.5) * 1.32 + 0.5, 0.0, 1.0);
                    col = vec3(bw16);
                } else if (uVideoFilterMode < 5.5) {
                    // Mode 5: 35MM NÉON CINÉ (ECN-2 teal shadows, glowing crimson highlights & blue anamorphic streak)
                    float lNeon = dot(col, vec3(0.299, 0.587, 0.114));
                    col += vec3(-0.02, 0.025, 0.055) * (1.0 - smoothstep(0.0, 0.55, lNeon));
                    col += vec3(0.055, 0.012, -0.015) * smoothstep(0.55, 1.0, lNeon);
                    float streakY = 0.42 + 0.14 * sin(tSlow * 0.65);
                    float anam = exp(-abs(workUv.y - streakY) * 42.0) * smoothstep(0.58, 0.96, lNeon);
                    col = screenBlend(col, vec3(0.18, 0.66, 1.0) * anam * 0.55);
                } else if (uVideoFilterMode < 6.5) {
                    // Mode 6: TECHNICOLOR 3-STRIP 1955 (vivid dye-transfer crimson, emerald cyan & golden velvet skin tones)
                    vec3 tc = vec3(
                        clamp(col.r * 1.24 - col.g * 0.10 - col.b * 0.04, 0.0, 1.0),
                        clamp(col.g * 1.12 - col.r * 0.04 + col.b * 0.05, 0.0, 1.0),
                        clamp(col.b * 1.08 + col.g * 0.10 - col.r * 0.12, 0.0, 1.0)
                    );
                    float lTc = dot(tc, vec3(0.299, 0.587, 0.114));
                    tc = mix(vec3(lTc), tc, 1.32);
                    tc = (tc - 0.5) * 1.14 + 0.5;
                    // Subtle warm golden-age projection halation
                    float hiTc = smoothstep(0.68, 0.98, lTc);
                    col = screenBlend(tc, vec3(0.98, 0.72, 0.42) * hiTc * 0.16);
                } else if (uVideoFilterMode < 7.5) {
                    // Mode 7: HI8 HANDYCAM 1996 (warm pastel 90s home-video tape, soft interlace & highlight bloom)
                    float interlace = 0.95 + 0.05 * sin(gl_FragCoord.y * 3.14159 + floor(tSlow * 25.0) * 3.14159);
                    float hi8Noise = (hash12(vec2(floor(gl_FragCoord.x * 0.75), floor(gl_FragCoord.y * 0.75) + floor(tSlow * 25.0) * 13.0)) - 0.5) * 0.065;
                    col = col * interlace + vec3(hi8Noise);
                    // Characteristic warm amber-rose Sony Hi8 white balance & lifted shadows
                    col = mix(vec3(0.05, 0.045, 0.06), vec3(0.97, 0.95, 0.90), clamp(col, 0.0, 1.0));
                    col = vec3(col.r * 1.07, col.g * 1.02, col.b * 0.93);
                    float lHi8 = dot(col, vec3(0.299, 0.587, 0.114));
                    col = screenBlend(col, vec3(1.0, 0.88, 0.72) * smoothstep(0.72, 0.99, lHi8) * 0.22);
                } else {
                    // Mode 8: LOMOKINO 35MM MANIVELLE (8 i/s cranked strip, panoramic letterbox, punchy Lomo contrast & edge burn)
                    float f8 = floor(tSlow * 8.0);
                    float crankFlicker = 0.90 + 0.18 * hash12(vec2(f8 * 5.7, 23.3));
                    col = (col - 0.5) * 1.28 + 0.5;
                    float lLk = dot(col, vec3(0.299, 0.587, 0.114));
                    col = mix(vec3(lLk), col, 1.24) * crankFlicker;
                    // Intermittent warm plastic-gate edge leak pulse per hand-crank frame
                    float edgeLeak = exp(-workUv.x * 7.5) * smoothstep(0.45, 0.95, hash12(vec2(f8, 9.1)));
                    col = screenBlend(col, vec3(1.0, 0.42, 0.10) * edgeLeak * 0.62);
                    // Authentic wide LomoKino horizontal filmstrip black frame mask top & bottom
                    float stripMask = smoothstep(0.11, 0.135, workUv.y) * smoothstep(0.89, 0.865, workUv.y);
                    col *= mix(0.06, 1.0, stripMask);
                }
            }

            // Post-shoulder luminous light-leak burn (keeps fiery amber/crimson cores incandescent on color film,
            // and bright silver-halide fogged edges on B&W emulsions without shoulder compression dulling!)
            if (max(postLeakGlow.r, max(postLeakGlow.g, postLeakGlow.b)) > 0.002) {
                vec3 finalLeak = postLeakGlow;
                if (uIsMonochrome > 0.5) {
                    float bwLeak = dot(finalLeak, vec3(0.45, 0.45, 0.10));
                    finalLeak = vec3(bwLeak * 0.85);
                }
                col = screenBlend(col, finalLeak);
            }

            // --- PROCEDURAL CELLULOID ARTIFACTS (DUST MOTES, MICRO-FIBERS & TRANSPORT HAIRLINES) ---
            // Scales organically with camera wear, plastic lens/gate roughness, film age, and high-ISO emulsion
            float artifactLevel = clamp(
                uLightLeakIntensity * 0.72 + uExpFogLift * 2.1 + uPlasticScratches * 0.55 + max(uGrainIntensity - 0.22, 0.0) * 0.35,
                0.0,
                1.0
            );
            if (artifactLevel > 0.04) {
                // 1. Sparse hashed grid of organic dust specks stepping at authentic 6 fps film-gate cadence
                vec2 dustGrid = vScreenUv * 8.5;
                vec2 dCell = floor(dustGrid);
                vec2 dLocal = fract(dustGrid);
                float dSeed = hash12(dCell + vec2(tDust * 13.7 + uLightLeakSeed * 41.0, tDust * 7.3));
                float dThreshold = 0.968 - artifactLevel * 0.038;
                if (dSeed > dThreshold) {
                    vec2 speckPos = vec2(
                        hash12(dCell + vec2(dSeed * 31.1, 4.7)),
                        hash12(dCell + vec2(9.2, dSeed * 47.3))
                    ) * 0.68 + 0.16;
                    vec2 dDelta = (dLocal - speckPos) * vec2(1.0, 0.72 + 0.55 * fract(dSeed * 19.3));
                    float speckRadius = 0.024 + fract(dSeed * 53.7) * 0.036;
                    float speck = smoothstep(speckRadius, speckRadius * 0.22, length(dDelta));
                    // Mix of bright scanned dust motes and subtle dark negative lint
                    float isBrightSpeck = step(0.38, fract(dSeed * 97.1));
                    col = mix(col * (1.0 - speck * 0.32 * artifactLevel), col + vec3(0.30, 0.27, 0.23) * speck * 0.48 * artifactLevel, isBrightSpeck);
                }

                // 2. Occasional organic curved micro-fiber / hair arc near frame periphery
                float fiberGate = hash12(vec2(tDust * 3.7 + uLightLeakSeed * 19.0, 11.4));
                if (fiberGate > (0.76 - artifactLevel * 0.24)) {
                    vec2 fiberOrigin = vec2(
                        mix(0.08, 0.92, hash12(vec2(fiberGate * 23.1, tDust * 1.9))),
                        mix(0.08, 0.92, hash12(vec2(tDust * 2.7, fiberGate * 37.9)))
                    );
                    vec2 fp = rot2d(vScreenUv - fiberOrigin, fiberGate * 6.283);
                    float arcCurve = fp.y - (fp.x * fp.x * 3.8);
                    float inSeg = smoothstep(0.055, 0.045, abs(fp.x));
                    float fiberLine = exp(-abs(arcCurve) * 420.0) * inSeg;
                    col += vec3(0.26, 0.23, 0.19) * fiberLine * 0.38 * artifactLevel;
                }

                // 3. Transient ultra-fine vertical film-transport hairline scratch on aged/toy rolls
                if (artifactLevel > 0.26) {
                    float scratchChance = hash12(vec2(floor(tSlow * 3.0) * 5.3, uLightLeakSeed * 29.0));
                    if (scratchChance > (0.52 - uPlasticScratches * 0.30)) {
                        float sx = fract(uLightLeakSeed * 19.9 + scratchChance * 3.7 + sin(tSlow * 1.4) * 0.004);
                        float scratchBreak = smoothstep(0.26, 0.78, fbm3(vec2(vScreenUv.y * 5.5, tSlow * 1.8 + sx * 10.0)));
                        float hairline = exp(-abs(vScreenUv.x - sx) * 720.0) * scratchBreak;
                        col += vec3(0.28, 0.25, 0.21) * hairline * (0.20 * artifactLevel + 0.16 * uPlasticScratches);
                    }
                }

                // 4. Dedicated Plastic Film-Gate Emulsion Pressure Scratch (persistent subtle groove on Holga / Jetable!)
                if (uPlasticScratches > 0.35) {
                    float railX1 = 0.14 + fract(uLightLeakSeed * 31.7 + 0.19) * 0.72 + sin(tSlow * 1.8 + 0.7) * 0.0022;
                    float railBreak1 = smoothstep(0.32, 0.76, fbm3(vec2(vScreenUv.y * 6.2 - tSlow * 0.9, railX1 * 14.0)));
                    float brightGroove = exp(-abs(vScreenUv.x - railX1) * 780.0) * railBreak1;
                    float darkShadowEdge = exp(-abs(vScreenUv.x - (railX1 + 0.0026)) * 680.0) * railBreak1;
                    col = col * (1.0 - darkShadowEdge * 0.16 * uPlasticScratches) +
                        vec3(0.34, 0.31, 0.27) * brightGroove * 0.28 * uPlasticScratches;

                    // Occasional second finer transport hairline on heavily worn plastic gates (Holga / Jetable)
                    float railX2 = fract(railX1 + 0.37) * 0.84 + 0.08;
                    float railBreak2 = smoothstep(0.46, 0.85, fbm3(vec2(vScreenUv.y * 8.5 + tSlow * 1.1, railX2 * 9.0)));
                    float fineGroove2 = exp(-abs(vScreenUv.x - railX2) * 920.0) * railBreak2;
                    col += vec3(0.28, 0.26, 0.22) * fineGroove2 * 0.18 * uPlasticScratches;
                }
            }

            // --- PROCEDURAL MULTI-SCALE ORGANIC SILVER-HALIDE & DYE-CLOUD FILM GRAIN (18 FPS) ---
            // Blends fine silver crystals with medium organic dye clumps, stepping at cinema cadence
            vec2 fineCoord = floor(gl_FragCoord.xy / max(uGrainSize, 0.65));
            vec2 clumpCoord = gl_FragCoord.xy / max(uGrainSize * 2.4, 1.55);
            vec2 tOffA = vec2(tFilm * 17.3, tFilm * 31.7);
            vec2 tOffB = vec2(tFilm * 11.1 + 4.91, tFilm * 23.9 + 7.13);
            float fine1 = hash12(fineCoord + tOffA);
            float fine2 = hash12(fineCoord * 1.37 + tOffB);
            float fineGrain = (fine1 + fine2) - 1.0;
            float clumpGrain = (valueNoise(clumpCoord + tOffA * 0.13) - 0.5) * 1.5;
            float lumaGrain = fineGrain * 0.68 + clumpGrain * 0.32;

            float lumaFinal = dot(clamp(col, 0.0, 1.0), vec3(0.299, 0.587, 0.114));
            float grainEnvelope = (1.0 - smoothstep(0.65, 1.0, lumaFinal) * 0.55) * (0.45 + 0.55 * smoothstep(0.0, 0.25, lumaFinal));

            if (uIsMonochrome > 0.5) {
                col += vec3(lumaGrain * uGrainIntensity * grainEnvelope * 0.43);
            } else {
                // Authentic color negative dye-coupler micro-chroma variance (subtle, mostly luminance)
                float cR = hash12(fineCoord + tOffA + vec2(3.1, 1.7)) - 0.5;
                float cB = hash12(fineCoord + tOffB + vec2(7.3, 5.9)) - 0.5;
                vec3 dyeGrain = vec3(lumaGrain) * 0.78 + vec3(cR, -0.35 * (cR + cB), cB) * 0.34;
                col += dyeGrain * uGrainIntensity * grainEnvelope * 0.43;
            }

            gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    const val FRAGMENT_SHADER_OES = """
        #extension GL_OES_EGL_image_external : require
        uniform samplerExternalOES uTexture;
    """ + FRAGMENT_BODY

    const val FRAGMENT_SHADER_2D = """
        uniform sampler2D uTexture;
    """ + FRAGMENT_BODY

    fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vShader)
        GLES20.glAttachShader(program, fShader)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("GL Program link failed: $log")
        }
        GLES20.glDeleteShader(vShader)
        GLES20.glDeleteShader(fShader)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("GL Shader compile failed: $log")
        }
        return shader
    }

    /**
     * Uploads all optical chain parameters into the currently active GL shader program.
     */
    fun bindOpticalUniforms(
        program: Int,
        params: OpticalChainParams,
        surfaceWidth: Int,
        surfaceHeight: Int,
        cropScaleX: Float,
        cropScaleY: Float,
        timeSeed: Float
    ) {
        val lens = params.lens
        val body = params.body
        val film = params.film
        val exp = params.expirationEffect
        val dev = params.development

        fun loc(name: String): Int = GLES20.glGetUniformLocation(program, name)

        GLES20.glUniform2f(loc("uCropScale"), cropScaleX, cropScaleY)
        GLES20.glUniform1f(loc("uZoom"), max(1.0f, lens.cropZoomFactor * params.zoomMultiplier))
        GLES20.glUniform1f(loc("uTime"), timeSeed)
        GLES20.glUniform2f(
            loc("uTexelSize"),
            1f / max(1, surfaceWidth).toFloat(),
            1f / max(1, surfaceHeight).toFloat()
        )

        // Stage 1: Lens
        GLES20.glUniform1f(loc("uDistortionK"), lens.distortionK)
        GLES20.glUniform1f(loc("uVignette"), lens.vignetteStrength)
        GLES20.glUniform1f(loc("uChromaticAberration"), lens.chromaticAberration)
        GLES20.glUniform1f(loc("uOpticalSoftness"), (lens.opticalSoftness + body.globalSoftness).coerceAtMost(0.9f))
        GLES20.glUniform1f(loc("uHalation"), (lens.halationStrength + film.halationBoost).coerceAtMost(1.25f))
        GLES20.glUniform1f(loc("uFlareStrength"), lens.flareStrength)
        GLES20.glUniform1f(loc("uTiltShift"), lens.tiltShiftAmount)
        GLES20.glUniform1f(loc("uTiltShiftPos"), params.tiltShiftPosition.coerceIn(0.12f, 0.88f))
        GLES20.glUniform1f(loc("uTiltShiftVertical"), if (params.tiltShiftVertical) 1.0f else 0.0f)
        val plasticScratches = max(body.plasticScratchIntensity, lens.plasticScratchIntensity).coerceIn(0f, 1f)
        GLES20.glUniform1f(loc("uPlasticScratches"), plasticScratches)
        GLES20.glUniform1f(loc("uSwirlyBokeh"), lens.swirlyBokehAmount.coerceIn(0f, 1f))

        // Stage 2: Body
        val totalEv = params.evCompensation + exp.exposureShiftEv + dev.exposureDelta
        GLES20.glUniform1f(loc("uExposureEv"), totalEv)
        GLES20.glUniform1f(loc("uBodyContrast"), body.contrast)
        GLES20.glUniform1f(loc("uBodyWarmth"), body.warmth)
        GLES20.glUniform1f(loc("uBodySaturation"), body.saturation)
        GLES20.glUniform3f(loc("uBodyShadowTint"), body.shadowR, body.shadowG, body.shadowB)
        GLES20.glUniform3f(loc("uBodyHighlightTint"), body.highlightR, body.highlightG, body.highlightB)
        GLES20.glUniform1f(loc("uLightLeakIntensity"), params.effectiveLightLeakIntensity)
        GLES20.glUniform1f(loc("uLightLeakSeed"), params.lightLeakSeed)
        GLES20.glUniform1f(loc("uInvertedGroundGlass"), if (params.invertedGroundGlass) 1.0f else 0.0f)

        // Stage 3: Film
        GLES20.glUniform1f(loc("uIsMonochrome"), if (film.isMonochrome) 1.0f else 0.0f)
        GLES20.glUniform3f(loc("uFilmRgbGain"), film.redGain, film.greenGain, film.blueGain)
        GLES20.glUniform1f(loc("uFilmContrast"), film.contrast * dev.contrastMul)
        GLES20.glUniform1f(loc("uFilmSaturation"), film.saturation * dev.saturationMul)
        GLES20.glUniform1f(loc("uFilmToeLift"), film.toeLift)
        GLES20.glUniform1f(loc("uFilmShoulder"), film.shoulderCompress)
        GLES20.glUniform3f(loc("uFilmShadowTint"), film.shadowTintR, film.shadowTintG, film.shadowTintB)
        GLES20.glUniform3f(loc("uFilmHighlightTint"), film.highlightTintR, film.highlightTintG, film.highlightTintB)

        val effectiveGrain = (film.grainIntensity * exp.grainMultiplier * dev.grainMul).coerceAtMost(0.95f)
        GLES20.glUniform1f(loc("uGrainSize"), film.grainSize)
        GLES20.glUniform1f(loc("uGrainIntensity"), effectiveGrain)
        GLES20.glUniform1f(loc("uHueShift"), film.hueShift)
        GLES20.glUniform1f(loc("uSepiaAmount"), max(film.sepiaAmount, dev.sepiaBathStrength))
        GLES20.glUniform1f(loc("uCyanotypeAmount"), max(film.cyanotypeAmount, dev.cyanotypeBathStrength))
        GLES20.glUniform1f(loc("uInfraredAmount"), film.infraredAmount)

        // Stage 3d: Ultimate Rétro 19th-Century Historical Plates & Processes
        val effectiveCollodion = max(max(film.collodionPlateAmount, body.collodionPlateAmount * 0.85f), dev.collodionBathStrength).coerceIn(0f, 1f)
        val effectiveDaguerre = max(max(film.daguerreTarnishAmount, body.daguerreTarnishAmount * 0.85f), dev.daguerreBathStrength).coerceIn(0f, 1f)
        val effectiveAutochrome = max(film.autochromeMosaicAmount, body.autochromeMosaicAmount * 0.80f).coerceIn(0f, 1f)
        val effectiveOrtho = max(film.orthoUvAmount, max(dev.collodionBathStrength * 0.90f, dev.daguerreBathStrength * 0.80f)).coerceIn(0f, 1f)
        GLES20.glUniform1f(loc("uOrthoUv"), effectiveOrtho)
        GLES20.glUniform1f(loc("uCollodionPlate"), effectiveCollodion)
        GLES20.glUniform1f(loc("uDaguerreTarnish"), effectiveDaguerre)
        GLES20.glUniform1f(loc("uAutochromeMosaic"), effectiveAutochrome)
        GLES20.glUniform1f(loc("uPaperFiber"), max(film.paperFiberAmount, dev.platinumBathStrength * 0.50f).coerceIn(0f, 1f))
        GLES20.glUniform1f(loc("uBrushBorder"), film.brushBorderAmount.coerceIn(0f, 1f))
        GLES20.glUniform1f(loc("uPlatinumTone"), dev.platinumBathStrength.coerceIn(0f, 1f))

        // Stage 3b: Expiration
        GLES20.glUniform1f(loc("uExpFogLift"), exp.fogLift)
        GLES20.glUniform3f(loc("uExpCast"), exp.castR, exp.castG, exp.castB)
        GLES20.glUniform1f(loc("uExpContrastMul"), exp.contrastFactor)
        GLES20.glUniform1f(loc("uExpSatMul"), exp.saturationFactor)

        // Stage 3c: Lab Development
        GLES20.glUniform1f(loc("uDevCrossProcess"), dev.crossProcessStrength)
        GLES20.glUniform1f(loc("uDevBleachBypass"), dev.bleachBypassStrength)
        GLES20.glUniform1f(loc("uDevAcutance"), dev.acutanceBoost)

        // Stage 4: Cinema Video Filter Mode
        GLES20.glUniform1f(loc("uVideoFilterMode"), params.videoFilterMode.toFloat())
    }
}
