/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        relay: {
          bg: '#1B2430',
          sidebar: '#141C26',
          surface: '#222C3A',
          'surface-hover': '#2A3647',
          border: 'rgba(51, 64, 84, 0.5)',
          'border-solid': '#334054',
          text: '#E4DED0',
          muted: '#8891A3',
          brass: '#C9A227',
          'brass-dim': '#987A1E',
          sage: '#6B8F71',
          'sage-dim': '#4F6C54',
          amber: '#C98A3A',
          'amber-dim': '#96672B',
          red: '#B23A34',
          'red-dim': '#862B26',
        }
      },
      fontFamily: {
        typewriter: ['"Special Elite"', 'cursive', 'serif'],
        sans: ['"IBM Plex Sans"', '-apple-system', 'BlinkMacSystemFont', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      borderRadius: {
        DEFAULT: '4px',
        sm: '4px',
        md: '4px',
        lg: '4px',
      },
      fontSize: {
        '3xs': '0.625rem',
        '2xs': '0.6875rem',
        xs: '0.75rem',
        sm: '0.8125rem',
        base: '0.875rem',
        lg: '1rem',
        xl: '1.125rem',
        '2xl': '1.25rem',
        '3xl': '1.5rem',
      }
    },
  },
  plugins: [],
}

