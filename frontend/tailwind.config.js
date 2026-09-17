/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        vault: {
          bg: '#05070A',
          'bg-secondary': '#080B10',
          'bg-elevated': '#0D1117',
          surface: 'rgba(255, 255, 255, 0.055)',
          'surface-strong': 'rgba(255, 255, 255, 0.085)',
          'surface-hover': 'rgba(255, 255, 255, 0.11)',
          glass: 'rgba(255, 255, 255, 0.06)',
          'glass-strong': 'rgba(255, 255, 255, 0.09)',
          'glass-border': 'rgba(255, 255, 255, 0.10)',
          'glass-border-strong': 'rgba(255, 255, 255, 0.16)',
          primary: '#7C5CFF',
          'primary-light': '#9B82FF',
          'primary-subtle': 'rgba(124, 92, 255, 0.12)',
          'primary-glow': 'rgba(124, 92, 255, 0.20)',
          text: '#F5F7FA',
          'text-secondary': '#A7AFBC',
          'text-muted': '#707986',
          'text-disabled': '#4B5563',
          success: '#34D399',
          'success-subtle': 'rgba(52, 211, 153, 0.10)',
          'success-border': 'rgba(52, 211, 153, 0.20)',
          warning: '#FBBF24',
          'warning-subtle': 'rgba(251, 191, 36, 0.10)',
          'warning-border': 'rgba(251, 191, 36, 0.20)',
          danger: '#F87171',
          'danger-subtle': 'rgba(248, 113, 113, 0.10)',
          'danger-border': 'rgba(248, 113, 113, 0.20)',
          info: '#60A5FA',
          'info-subtle': 'rgba(96, 165, 250, 0.10)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      backdropBlur: {
        xs: '2px',
      },
      animation: {
        'pulse-subtle': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'fade-in': 'fadeIn 0.2s ease-out',
        'scale-in': 'scaleIn 0.15s ease-out',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.97)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
      },
    },
  },
  plugins: [],
};
