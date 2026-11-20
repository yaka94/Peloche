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
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """

    private const val FRAGMENT_BODY = """
        precision highp float;
        varying vec2 vTexCoord;

        // Geometry & Timing
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

        // Stage 2: Camera Body (Boîtier)
        uniform float uExposureEv;
        uniform float uBodyContrast;
        uniform float uBodyWarmth;
        uniform float uBodySaturation;
        uniform vec3 uBodyShadowTint;
        uniform vec3 uBodyHighlightTint;
        uniform float uLightLeakIntensity;
        uniform float uLightLeakSeed;

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

        // Stage 3b: Expiration Date Chemistry
        uniform float uExpFogLift;
        uniform vec3 uExpCast;
        uniform float uExpContrastMul;
        uniform float uExpSatMul;

        // Stage 3c: Lab Development Process
        uniform float uDevCrossProcess;
        uniform float uDevBleachBypass;
        uniform float uDevAcutance;

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

        vec3 sampleBase(vec2 uv) {
            return texture2D(uTexture, clamp(uv, 0.002, 0.998)).rgb;
        }

        vec3 screenBlend(vec3 base, vec3 overlay) {
            return 1.0 - (1.0 - clamp(base, 0.0, 1.0)) * (1.0 - clamp(overlay, 0.0, 1.0));
        }

        void main() {
            // --- STAGE 1: LENS (OBJECTIF) ---
            vec2 centered = (vTexCoord - 0.5) * uCropScale / max(uZoom, 1.0);
            vec2 distorted = applyDistortion(centered, uDistortionK);
            vec2 baseUv = distorted + 0.5;

            if (baseUv.x < 0.0 || baseUv.x > 1.0 || baseUv.y < 0.0 || baseUv.y > 1.0) {
                gl_FragColor = vec4(0.03, 0.03, 0.03, 1.0);
                return;
            }

            // Lateral Chromatic Aberration (stronger toward image corners)
            float radialDist = length(centered);
            vec2 caOffset = centered * uChromaticAberration * radialDist;
            vec3 col;
            col.r = sampleBase(baseUv + caOffset).r;
            col.g = sampleBase(baseUv).g;
            col.b = sampleBase(baseUv - caOffset).b;

            // Tilt-Shift directional blur + optical softness + Rodinal acutance neighborhood
            float tiltBand = abs(vTexCoord.y - 0.5) * 2.0;
            float tiltBlur = uTiltShift * smoothstep(0.24, 0.95, tiltBand);
            float blurRadius = (uOpticalSoftness * 2.6 + tiltBlur * 5.2);

            if (blurRadius > 0.05 || uDevAcutance > 0.05 || uHalation > 0.05) {
                vec2 stepUv = uTexelSize * max(blurRadius, 1.25);
                vec3 n1 = sampleBase(baseUv + vec2(stepUv.x, 0.0));
                vec3 n2 = sampleBase(baseUv - vec2(stepUv.x, 0.0));
                vec3 n3 = sampleBase(baseUv + vec2(0.0, stepUv.y));
                vec3 n4 = sampleBase(baseUv - vec2(0.0, stepUv.y));
                vec3 avg = (n1 + n2 + n3 + n4) * 0.25;

                // Softness / Tilt-Shift blend
                float mixBlur = clamp(uOpticalSoftness * 0.55 + tiltBlur * 0.78, 0.0, 0.85);
                col = mix(col, avg, mixBlur);

                // Rodinal high-acutance unsharp edge micro-contrast
                if (uDevAcutance > 0.02) {
                    col += (col - avg) * uDevAcutance * 0.65;
                }

                // Specular Highlight Halation (red-orange glow bleeding around bright sources)
                if (uHalation > 0.02) {
                    vec2 haloStep = uTexelSize * 7.5;
                    vec3 h1 = sampleBase(baseUv + vec2(haloStep.x, haloStep.y));
                    vec3 h2 = sampleBase(baseUv + vec2(-haloStep.x, haloStep.y));
                    vec3 h3 = sampleBase(baseUv + vec2(haloStep.x, -haloStep.y));
                    vec3 h4 = sampleBase(baseUv + vec2(-haloStep.x, -haloStep.y));
                    vec3 haloAvg = (h1 + h2 + h3 + h4) * 0.25;
                    float brightMask = smoothstep(0.66, 0.98, max(haloAvg.r, max(haloAvg.g, haloAvg.b)));
                    col += vec3(1.0, 0.34, 0.14) * brightMask * uHalation * 0.44;
                }
            }

            // Multi-Variant Optical Lens Flare & Chromatic Aperture Ghosts (4 distinct optical signatures)
            if (uFlareStrength > 0.02) {
                // Physical ghosting reflected across the optical axis from real bright specular zones
                vec3 opposite = sampleBase(vec2(1.0 - baseUv.x, 1.0 - baseUv.y));
                float oppBright = smoothstep(0.70, 0.99, max(opposite.r, max(opposite.g, opposite.b)));
                float flareMode = floor(fract(uLightLeakSeed * 9.17 + 0.23) * 4.0);
                float axisAngle = fract(uLightLeakSeed * 5.71) * 6.28318;
                vec2 lightOrigin = vec2(0.5 + cos(axisAngle) * 0.44, 0.5 + sin(axisAngle) * 0.42);
                vec2 axisVec = vec2(0.5) - lightOrigin;

                // Axis-opposed live ghost scaled by real scene highlights
                vec3 liveGhostTint = mix(vec3(0.40, 0.78, 1.0), vec3(1.0, 0.62, 0.32), step(1.5, flareMode));
                col += liveGhostTint * oppBright * uFlareStrength * 0.34;

                vec3 flareAccum = vec3(0.0);
                if (flareMode < 0.5) {
                    // Mode 0: Multi-ring chromatic aperture ghosts along randomized light axis
                    vec2 g1 = lightOrigin + axisVec * 1.35;
                    vec2 g2 = lightOrigin + axisVec * 1.85;
                    vec2 g3 = lightOrigin + axisVec * 0.65;
                    float r1 = length(vTexCoord - g1);
                    float r2 = length(vTexCoord - g2);
                    float r3 = length(vTexCoord - g3);
                    float ring1 = exp(-pow((r1 - 0.11) * 26.0, 2.0)) * 0.55 + exp(-r1 * r1 * 48.0) * 0.22;
                    float disc2 = exp(-r2 * r2 * 90.0) * 0.45;
                    float ring3 = exp(-pow((r3 - 0.07) * 34.0, 2.0)) * 0.40;
                    flareAccum += vec3(0.32, 0.76, 1.0) * ring1;
                    flareAccum += vec3(1.0, 0.64, 0.28) * disc2;
                    flareAccum += vec3(0.84, 0.42, 0.96) * ring3;
                } else if (flareMode < 1.5) {
                    // Mode 1: Anamorphic horizontal streak + warm edge bloom
                    float yPos = 0.20 + fract(uLightLeakSeed * 11.3) * 0.60;
                    float streak = exp(-abs(vTexCoord.y - yPos) * 28.0) * (0.45 + 0.55 * fbm3(vTexCoord * vec2(3.5, 9.0) + uLightLeakSeed * 7.0));
                    float core = exp(-length((vTexCoord - vec2(fract(uLightLeakSeed * 4.3), yPos)) * vec2(1.6, 5.5)) * 3.8);
                    flareAccum += vec3(0.28, 0.68, 1.0) * streak * 0.42 + vec3(1.0, 0.82, 0.55) * core * 0.52;
                } else if (flareMode < 2.5) {
                    // Mode 2: Vintage warm corner veiling glare + organic prismatic sub-crop
                    float cornerDist = length(vTexCoord - lightOrigin);
                    float veil = exp(-cornerDist * cornerDist * 2.8) * (0.62 + 0.38 * fbm3(vTexCoord * 4.2 + uLightLeakSeed * 12.0));
                    flareAccum += vec3(1.0, 0.72, 0.38) * veil * 0.48;
                    if (uHasOverlay > 0.5) {
                        vec2 subUv = rot2d((vTexCoord - 0.5) * 0.75, axisAngle) + vec2(0.5 + (fract(uLightLeakSeed * 3.1) - 0.5) * 0.24);
                        vec3 tex = texture2D(uOverlayTex, clamp(subUv, 0.02, 0.98)).rgb;
                        flareAccum += tex * 0.52;
                    }
                } else {
                    // Mode 3: Subtle soft rainbow arc ghosting (barely-there natural uncoated glass)
                    float arcR = length(vTexCoord - lightOrigin);
                    float arcR_red = exp(-pow((arcR - 0.56) * 18.0, 2.0));
                    float arcR_grn = exp(-pow((arcR - 0.585) * 18.0, 2.0));
                    float arcR_blu = exp(-pow((arcR - 0.61) * 18.0, 2.0));
                    flareAccum += vec3(arcR_red * 0.45, arcR_grn * 0.36, arcR_blu * 0.48) * 0.55;
                }
                col = screenBlend(col, flareAccum * uFlareStrength * 0.78);
            }

            // Aperture cosine^4 peripheral vignetting
            float vig = 1.0 - uVignette * smoothstep(0.22, 0.95, radialDist * radialDist * 1.45);
            col *= clamp(vig, 0.18, 1.0);

            // --- STAGE 2: CAMERA BODY (BOÎTIER) ---
            col *= pow(2.0, uExposureEv);

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

            // Multi-Archetype Organic Film Light Leaks & Micro-Defects (8 distinct per-shot variants!)
            if (uLightLeakIntensity > 0.01) {
                float s1 = fract(uLightLeakSeed * 13.73 + 0.17);
                float s2 = fract(uLightLeakSeed * 29.31 + 0.61);
                float s3 = fract(uLightLeakSeed * 53.19 + 0.37);
                float archetype = floor(s1 * 8.0);
                // Natural per-shot strength variation so a full roll feels organic
                float shotStrength = uLightLeakIntensity * mix(0.48, 1.12, s2);
                vec3 leakAccum = vec3(0.0);

                // Flip coordinates randomly per shot so leaks enter from any edge/corner
                vec2 p = vTexCoord;
                if (s2 > 0.5) p.x = 1.0 - p.x;
                if (s3 > 0.5) p.y = 1.0 - p.y;
                float nCloud = fbm3(p * vec2(3.2, 4.6) + vec2(s1 * 17.0, s3 * 23.0));

                if (archetype < 0.5) {
                    // 0. Vertical Door-Hinge Edge Burn (sharp fiery crimson-amber strip hugging film gate)
                    float edgeW = 0.09 + s3 * 0.14;
                    float band = exp(-pow(p.x / edgeW, 2.0)) * (0.55 + 0.65 * nCloud);
                    float hotCore = exp(-pow(p.x / (edgeW * 0.36), 2.0));
                    leakAccum = vec3(1.0, 0.24, 0.05) * band * 0.85 + vec3(1.0, 0.86, 0.42) * hotCore * 0.55;
                } else if (archetype < 1.5) {
                    // 1. Corner Sprocket Bleeding (diagonal amber leak with periodic 35mm sprocket shadow bands)
                    float dCorner = length(p * vec2(1.15, 0.95));
                    float sprocketShadow = 0.72 + 0.28 * sin(p.y * 34.0 + s2 * 6.28);
                    float spill = exp(-dCorner * dCorner * (3.6 + s3 * 2.2)) * sprocketShadow * (0.60 + 0.50 * nCloud);
                    leakAccum = vec3(1.0, 0.52, 0.12) * spill * 0.95 + vec3(0.95, 0.14, 0.08) * spill * 0.32;
                } else if (archetype < 2.5) {
                    // 2. Rear Film-Base Crimson Fog Veil (broad smoky scarlet wash through film backing)
                    float wash = smoothstep(0.62, 0.0, p.x * 0.85 + p.y * 0.25 + (nCloud - 0.5) * 0.35);
                    leakAccum = vec3(0.88, 0.12, 0.18) * wash * (0.50 + 0.45 * nCloud) * 0.78;
                } else if (archetype < 3.5) {
                    // 3. Horizontal Shutter-Curtain Slit Ribbon (asymmetric warm peach-gold streak)
                    float slitY = 0.08 + s3 * 0.24;
                    float ribbon = exp(-pow((p.y - slitY) * (7.5 + s2 * 5.0), 2.0)) * smoothstep(0.92, 0.12, p.x) * (0.58 + 0.55 * nCloud);
                    leakAccum = vec3(1.0, 0.66, 0.26) * ribbon * 0.82;
                } else if (archetype < 4.5) {
                    // 4. Diagonal Holga Bakelite Seam Leak (curved amber beam + opposite cool magenta stress fog)
                    float diag = abs((p.x * 0.85 + p.y * 0.55) - (0.30 + s3 * 0.35));
                    float beam = exp(-diag * diag * 24.0) * (0.55 + 0.65 * nCloud);
                    float oppFog = exp(-length((1.0 - p) * 1.4) * 2.6) * 0.38;
                    leakAccum = vec3(1.0, 0.46, 0.14) * beam * 0.78 + vec3(0.82, 0.20, 0.56) * oppFog;
                } else if (archetype < 5.5) {
                    // 5. Hybrid Rotated/Zoomed Photographic Overlay Burn + FBM organic mask & unique tint
                    if (uHasOverlay > 0.5) {
                        float rotAng = s2 * 6.28318;
                        vec2 cropUv = rot2d((p - 0.5) * mix(0.55, 0.92, s3), rotAng) + vec2(0.5 + (s1 - 0.5) * 0.26);
                        vec3 tex = texture2D(uOverlayTex, clamp(cropUv, 0.02, 0.98)).rgb;
                        float mask = smoothstep(0.22, 0.82, nCloud * 0.85 + (1.0 - p.x) * 0.35);
                        vec3 tint = mix(vec3(1.15, 0.45, 0.18), vec3(1.05, 0.24, 0.48), s3);
                        leakAccum = tex * tint * mask * 0.95;
                    } else {
                        leakAccum = vec3(1.0, 0.38, 0.12) * exp(-p.x * 5.5) * nCloud * 0.7;
                    }
                } else if (archetype < 6.5) {
                    // 6. Twin Opposite-Corner Warm/Crimson Glow
                    float cA = exp(-length(p * vec2(1.3, 1.3)) * 3.4) * (0.65 + 0.45 * nCloud);
                    float cB = exp(-length((1.0 - p) * vec2(1.4, 1.4)) * 3.8) * (0.55 + 0.45 * nCloud);
                    leakAccum = vec3(1.0, 0.58, 0.16) * cA * 0.72 + vec3(0.92, 0.16, 0.22) * cB * 0.58;
                } else {
                    // 7. Quasi-Clean Frame with only a whisper-soft warm edge kiss (natural roll breathing!)
                    float kiss = exp(-p.x * 11.0) * (0.5 + 0.5 * nCloud);
                    leakAccum = vec3(1.0, 0.48, 0.16) * kiss * 0.24;
                }

                // Subtle organic micro-dust motes & occasional ultra-fine transport hairline on toy/vintage frames
                float dustX = fract(uLightLeakSeed * 41.7 + 0.19);
                float dustY = fract(uLightLeakSeed * 73.1 + 0.43);
                float dustMote = smoothstep(0.0055, 0.001, length(vTexCoord - vec2(dustX, dustY)));
                float dustMote2 = smoothstep(0.0042, 0.0008, length(vTexCoord - vec2(fract(dustX + 0.47), fract(dustY + 0.31))));
                float scratchX = fract(uLightLeakSeed * 19.9 + 0.27);
                float hairline = step(0.68, s3) * exp(-abs(vTexCoord.x - scratchX) * 650.0) * (0.25 + 0.45 * nCloud);

                col = screenBlend(col, leakAccum * shotStrength);
                col += vec3(0.28, 0.24, 0.20) * (dustMote * 0.45 + dustMote2 * 0.35 + hairline * 0.16) * uLightLeakIntensity;
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

            // Monochrome silver halide conversion
            if (uIsMonochrome > 0.5) {
                float bw = dot(col, vec3(0.32, 0.56, 0.12));
                col = vec3(bw);
            }

            // Sensitometric S-curve: analog toe lift & soft shoulder roll-off
            col = clamp(col, 0.0, 1.25);
            col = col / (1.0 + uFilmShoulder * max(col - 0.55, 0.0));
            col = (col - 0.5) * (uFilmContrast * uExpContrastMul) + 0.5;
            col = mix(vec3(uFilmToeLift + uExpFogLift), vec3(1.0), clamp(col, 0.0, 1.0));

            // 3-way Film Shadow / Highlight split toning
            float lumaFilm = dot(col, vec3(0.299, 0.587, 0.114));
            col += uFilmShadowTint * (1.0 - smoothstep(0.0, 0.52, lumaFilm));
            col += uFilmHighlightTint * smoothstep(0.48, 1.0, lumaFilm);

            // Film Saturation & Expiration desaturation
            float lumaSat = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(vec3(lumaSat), col, uFilmSaturation * uExpSatMul);

            // --- STAGE 3b: FILM EXPIRATION CHEMISTRY ---
            // Aged C-41 shifts green; aged E-6 shifts magenta; aged B&W gets silver veil
            float shadowWeight = 1.0 - smoothstep(0.0, 0.85, lumaSat);
            col += uExpCast * (0.35 + 0.65 * shadowWeight);

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

            // Sepia & Cyanotype archival baths (from Film or Lab Process)
            if (uSepiaAmount > 0.02) {
                float l = dot(col, vec3(0.299, 0.587, 0.114));
                vec3 sep = vec3(l * 1.12 + 0.04, l * 0.92 + 0.01, l * 0.66);
                col = mix(col, sep, clamp(uSepiaAmount, 0.0, 1.0));
            }
            if (uCyanotypeAmount > 0.02) {
                float l = dot(col, vec3(0.299, 0.587, 0.114));
                vec3 cyan = mix(vec3(0.05, 0.16, 0.34), vec3(0.88, 0.93, 0.95), smoothstep(0.04, 0.96, l));
                col = mix(col, cyan, clamp(uCyanotypeAmount, 0.0, 1.0));
            }

            // --- PROCEDURAL DYNAMIC ANALOG FILM GRAIN ---
            // Scales with ISO, expiration age, and Push/Pull dev; strongest in shadows/midtones
            vec2 grainCoord = floor(gl_FragCoord.xy / max(uGrainSize, 0.65));
            float n1 = hash12(grainCoord + vec2(uTime * 17.3, uTime * 31.7));
            float n2 = hash12(grainCoord * 1.37 + vec2(uTime * 11.1, 4.91));
            float grain = ((n1 + n2) - 1.0);
            float lumaFinal = dot(clamp(col, 0.0, 1.0), vec3(0.299, 0.587, 0.114));
            float grainEnvelope = (1.0 - smoothstep(0.65, 1.0, lumaFinal) * 0.55) * (0.45 + 0.55 * smoothstep(0.0, 0.25, lumaFinal));
            col += vec3(grain * uGrainIntensity * grainEnvelope * 0.42);

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

        // Stage 2: Body
        val totalEv = params.evCompensation + exp.exposureShiftEv + dev.exposureDelta
        GLES20.glUniform1f(loc("uExposureEv"), totalEv)
        GLES20.glUniform1f(loc("uBodyContrast"), body.contrast)
        GLES20.glUniform1f(loc("uBodyWarmth"), body.warmth)
        GLES20.glUniform1f(loc("uBodySaturation"), body.saturation)
        GLES20.glUniform3f(loc("uBodyShadowTint"), body.shadowR, body.shadowG, body.shadowB)
        GLES20.glUniform3f(loc("uBodyHighlightTint"), body.highlightR, body.highlightG, body.highlightB)
        GLES20.glUniform1f(loc("uLightLeakIntensity"), body.lightLeakIntensity)
        GLES20.glUniform1f(loc("uLightLeakSeed"), params.lightLeakSeed)

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

        // Stage 3b: Expiration
        GLES20.glUniform1f(loc("uExpFogLift"), exp.fogLift)
        GLES20.glUniform3f(loc("uExpCast"), exp.castR, exp.castG, exp.castB)
        GLES20.glUniform1f(loc("uExpContrastMul"), exp.contrastFactor)
        GLES20.glUniform1f(loc("uExpSatMul"), exp.saturationFactor)

        // Stage 3c: Lab Development
        GLES20.glUniform1f(loc("uDevCrossProcess"), dev.crossProcessStrength)
        GLES20.glUniform1f(loc("uDevBleachBypass"), dev.bleachBypassStrength)
        GLES20.glUniform1f(loc("uDevAcutance"), dev.acutanceBoost)
    }
}
