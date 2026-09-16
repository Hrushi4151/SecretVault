---
name: High-Contrast Bold Dark System
colors:
  surface: '#30000f'
  surface-dim: '#30000f'
  surface-bright: '#760031'
  surface-container-lowest: '#26000b'
  surface-container-low: '#3f0016'
  surface-container: '#47001a'
  surface-container-high: '#5a0023'
  surface-container-highest: '#6e002d'
  on-surface: '#ffd9df'
  on-surface-variant: '#cfc4c5'
  inverse-surface: '#ffd9df'
  inverse-on-surface: '#660029'
  outline: '#988e90'
  outline-variant: '#4c4546'
  surface-tint: '#c6c6c6'
  primary: '#c6c6c6'
  on-primary: '#303030'
  primary-container: '#000000'
  on-primary-container: '#757575'
  inverse-primary: '#5e5e5e'
  secondary: '#c6c6c7'
  on-secondary: '#2f3131'
  secondary-container: '#454747'
  on-secondary-container: '#b4b5b5'
  tertiary: '#ffb4a8'
  on-tertiary: '#690001'
  tertiary-container: '#000000'
  on-tertiary-container: '#ec0005'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c6'
  on-primary-fixed: '#1b1b1b'
  on-primary-fixed-variant: '#474747'
  secondary-fixed: '#e2e2e2'
  secondary-fixed-dim: '#c6c6c7'
  on-secondary-fixed: '#1a1c1c'
  on-secondary-fixed-variant: '#454747'
  tertiary-fixed: '#ffdad5'
  tertiary-fixed-dim: '#ffb4a8'
  on-tertiary-fixed: '#410000'
  on-tertiary-fixed-variant: '#930001'
  background: '#30000f'
  on-background: '#ffd9df'
  surface-variant: '#6e002d'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
---

# Design System Document

## Brand & Style
The design system adopts a High-Contrast / Bold dark aesthetic. It leverages a stark black background combined with high-impact accents (pure white, vivid red, and striking magenta/pink) to create an aggressive, modern, and striking visual identity. The tone is uncompromising, direct, and engineered for maximum visual impact and legibility in dark environments.

## Colors
The color system is built around a stark dark mode foundation. 
- **Primary (`#000000`)**: Deep obsidian black used for absolute background grounding and high-contrast containers.
- **Secondary (`#ffffff`)**: Pure white providing stark, high-contrast typography and critical UI lines.
- **Tertiary (`#ff0006`)**: Electric red accent used for primary actions, alerts, and focal points.
- **Neutral (`#ff0071`)**: Vibrant magenta/pink neutral variant used for borders, secondary surfaces, and interactive states.

## Typography
The system uses **Inter** exclusively across all text roles (headlines, body, and labels) to maintain a clean, highly legible, geometric sans-serif aesthetic. Type hierarchy relies heavily on bold weights and sharp contrast against the dark background.

## Layout & Spacing
A robust fluid grid model is utilized with a standard spacing factor of 2. Layouts prioritize clean alignments, predictable gutters, and spacious containers that let the high-contrast elements breathe.

## Elevation & Depth
Elevation is conveyed primarily through high-contrast outlines and stark background shifts rather than complex drop shadows. The system uses sharp, crisp borders and low-contrast outlines to delineate containers against the dark canvas, maintaining a flat yet structurally distinct hierarchy.

## Shapes
The shape language is uniformly **Rounded** (roundedness level `2`), featuring a default 0.5rem border radius for standard UI elements, balancing the sharp color contrasts with approachable, smooth geometries.

## Components
- **Buttons**: Feature rounded corners with high-contrast text and solid red or white fills.
- **Inputs**: Dark backgrounds bounded by sharp magenta/pink neutral outlines, focusing with stark white text.
- **Cards**: Surface containers utilizing subtle border definitions and rounded corners to separate content cleanly from the black background.
- **Chips, Checkboxes & Radios**: Styled with crisp borders and high-visibility accent states using the tertiary red and neutral pink hues.