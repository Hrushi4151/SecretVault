/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,jsx,ts,tsx}',
    './src/*.{js,jsx,ts,tsx}',
    './src/components/**/*.{js,jsx,ts,tsx}',
    './src/context/**/*.{js,jsx,ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        // High-Contrast Bold Dark System — Surface & Background Hierarchy
        'vault-root': '#26000B',
        'vault-root-dark': '#0D0106',
        'vault-sidebar': '#1E000A',
        'vault-canvas': '#26000B',
        'surface-container-lowest': '#0D0106',
        'surface-lowest': '#0D0106',
        'surface': '#30000F',
        'surface-base': '#30000F',
        'surface-container-low': '#3F0016',
        'surface-low': '#3F0016',
        'surface-container': '#4A001C',
        'surface-mid': '#4A001C',
        'surface-container-high': '#580023',
        'surface-high': '#580023',
        'surface-container-highest': '#760031',
        'surface-bright': '#760031',
        'surface-active': '#800035',

        // Typography & Contrast Tokens
        'text-primary': '#FFFFFF',
        'on-surface': '#FFFFFF',
        'text-secondary': '#F4B5C8',
        'text-secondary-alt': '#E598AF',
        'text-muted': '#A26377',
        'on-surface-variant': '#F4B5C8',
        'text-disabled': '#5E3240',

        // Brand & Action Accent Tokens
        'brand-primary': '#FF2D6D',
        'primary': '#FF2D6D',
        'on-primary': '#FFFFFF',
        'brand-hover': '#FF4D85',
        'brand-active': '#B8003E',
        'brand-subtle': 'rgba(255, 45, 109, 0.12)',
        'tertiary': '#FF2D6D',
        'tertiary-fixed-dim': '#F4B5C8',
        'on-tertiary-container': '#FF2D6D',

        // Semantic State & Telemetry Tokens
        // Optimal / Healthy / Synced (Green)
        'state-optimal': '#34D399',
        'state-optimal-bg': 'rgba(52, 211, 153, 0.12)',
        'state-optimal-border': 'rgba(52, 211, 153, 0.24)',

        // Warning / Review Due / Drift (Amber)
        'state-warning': '#FBBF24',
        'state-warning-bg': 'rgba(251, 191, 36, 0.12)',
        'state-warning-border': 'rgba(251, 191, 36, 0.24)',

        // Critical / Quarantine / Leaks (Red)
        'state-critical': '#F87171',
        'state-critical-solid': '#D30018',
        'state-critical-bg': 'rgba(248, 113, 113, 0.15)',
        'state-critical-border': 'rgba(248, 113, 113, 0.32)',
        'error': '#F87171',
        'error-container': 'rgba(248, 113, 113, 0.15)',
        'on-error-container': '#F87171',

        // Info / AI Ops / Attestation (Violet / Blue)
        'state-info': '#818CF8',
        'state-info-alt': '#60A5FA',
        'state-info-bg': 'rgba(129, 140, 248, 0.12)',
        'state-info-border': 'rgba(129, 140, 248, 0.22)',
        'secondary': '#F4B5C8',

        // Glass & Border Stroke Tokens
        'outline': 'rgba(255, 180, 200, 0.24)',
        'outline-variant': 'rgba(255, 180, 200, 0.14)',
        'stroke-subtle': 'rgba(255, 255, 255, 0.08)',
        'stroke-rose': 'rgba(255, 180, 200, 0.14)',
        'stroke-rose-strong': 'rgba(255, 180, 200, 0.24)',
        'stroke-card': 'rgba(255, 180, 200, 0.18)',
      },
      fontFamily: {
        headline: ['Inter', 'sans-serif'],
        display: ['Inter', 'sans-serif'],
        body: ['Inter', 'sans-serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"SF Mono"', 'monospace'],
      },
      borderRadius: {
        DEFAULT: '8px',
        sm: '6px',
        md: '8px',
        lg: '10px',
        xl: '14px',
        '2xl': '18px',
        full: '9999px',
      },
      animation: {
        'fade-in': 'fadeIn 0.15s ease-out',
        'scale-in': 'scaleIn 0.15s ease-out',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.97)' },
          '100%': { opacity: '1' },
        },
      },
    },
  },
  plugins: [],
};
