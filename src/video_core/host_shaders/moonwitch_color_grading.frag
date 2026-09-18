// SPDX-FileCopyrightText: Copyright 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later

#version 450

layout(location = 0) in vec2 texcoord;
layout(location = 0) out vec4 frag_color;

layout(binding = 0) uniform sampler2D color_texture;

layout(push_constant) uniform ColorGradingPushConstants {
    int mode;
    float strength;
} grading;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 change_saturation(vec3 color, float saturation) {
    float luminance = dot(color, LUMA);
    return mix(vec3(luminance), color, saturation);
}

vec3 change_contrast(vec3 color, float contrast) {
    return (color - vec3(0.5)) * contrast + vec3(0.5);
}

vec3 apply_grade(vec3 source, int mode) {
    float luminance = dot(source, LUMA);
    vec3 result = source;

    if (mode == 1) {
        // Vivid: stronger separation and color without crushing highlights.
        result = change_contrast(source, 1.08);
        result = change_saturation(result, 1.20);
        result *= vec3(1.025, 1.015, 0.985);
    } else if (mode == 2) {
        // Dark: deeper midtones, slightly cooler shadows.
        result = mix(source, source * source, 0.22);
        result = change_contrast(result, 1.10);
        result = change_saturation(result, 0.95);
        result *= vec3(0.95, 0.975, 1.01);
    } else if (mode == 3) {
        // Cinematic: restrained saturation with teal shadows and warm highlights.
        result = change_contrast(source, 1.10);
        result = change_saturation(result, 0.92);
        float shadows = 1.0 - smoothstep(0.12, 0.62, luminance);
        float highlights = smoothstep(0.42, 0.95, luminance);
        result += shadows * vec3(-0.022, 0.006, 0.025);
        result += highlights * vec3(0.026, 0.010, -0.018);
    } else if (mode == 4) {
        // Warm: golden highlights while retaining neutral blacks.
        float highlights = smoothstep(0.18, 0.92, luminance);
        result = change_saturation(source, 1.05);
        result *= mix(vec3(1.015, 1.0, 0.98), vec3(1.075, 1.025, 0.91), highlights);
    } else if (mode == 5) {
        // Cool: blue/cyan bias with a small contrast lift.
        result = change_contrast(source, 1.04);
        result = change_saturation(result, 1.03);
        result *= vec3(0.94, 1.005, 1.075);
    } else if (mode == 6) {
        // Moonlight: dark fantasy grade with blue shadows and muted warm channels.
        float shadows = 1.0 - smoothstep(0.16, 0.72, luminance);
        result = mix(source, source * source, 0.16);
        result = change_contrast(result, 1.08);
        result = change_saturation(result, 0.94);
        result *= vec3(0.89, 0.96, 1.09);
        result += shadows * vec3(-0.018, 0.008, 0.035);
    } else if (mode == 7) {
        // Monochrome: neutral luminance with a little extra definition.
        result = change_contrast(vec3(luminance), 1.07);
    } else if (mode == 8) {
        // HDR+: SDR-safe tone shaping that reveals shadow and highlight detail.
        float mapped_luminance = luminance +
            0.72 * luminance * (1.0 - luminance) * (0.52 - luminance);
        float luminance_gain = mapped_luminance / max(luminance, 0.0001);
        result = source * luminance_gain;
        result = change_contrast(result, 1.04);
        result = change_saturation(result, 1.10);
        float highlights = smoothstep(0.62, 1.0, luminance);
        result = mix(result, result / (0.92 + 0.08 * result), 0.28 * highlights);
    }

    return clamp(result, 0.0, 1.0);
}

void main() {
    vec4 source = texture(color_texture, texcoord);
    vec3 graded = apply_grade(source.rgb, grading.mode);
    float amount = clamp(grading.strength, 0.0, 1.0);
    frag_color = vec4(mix(source.rgb, graded, amount), source.a);
}
