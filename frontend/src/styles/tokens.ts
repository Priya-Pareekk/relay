export const STATUS_COLORS = {
  COMPLETED: {
    text: 'text-emerald-400',
    bg: 'bg-emerald-500/10',
    border: 'border-emerald-500/20',
    dot: 'bg-emerald-400',
    label: 'Completed',
  },
  RETRYING: {
    text: 'text-amber-400',
    bg: 'bg-amber-500/10',
    border: 'border-amber-500/20',
    dot: 'bg-amber-400',
    label: 'Retrying',
  },
  DEAD_LETTER: {
    text: 'text-rose-400',
    bg: 'bg-rose-500/10',
    border: 'border-rose-500/20',
    dot: 'bg-rose-400',
    label: 'Dead Letter',
  },
  PENDING: {
    text: 'text-slate-400',
    bg: 'bg-slate-500/10',
    border: 'border-slate-500/20',
    dot: 'bg-slate-400',
    label: 'Pending',
  },
  CANCELLED: {
    text: 'text-purple-400',
    bg: 'bg-purple-500/10',
    border: 'border-purple-500/20',
    dot: 'bg-purple-400',
    label: 'Cancelled',
  },
  PROCESSING: {
    text: 'text-blue-400',
    bg: 'bg-blue-500/10',
    border: 'border-blue-500/20',
    dot: 'bg-blue-400',
    label: 'Processing',
  },
} as const;

export type JobStatusKey = keyof typeof STATUS_COLORS;

export const CIRCUIT_STATE_COLORS = {
  CLOSED: {
    text: 'text-emerald-400',
    bg: 'bg-emerald-950/40',
    border: 'border-emerald-700/50',
    dot: 'bg-emerald-400',
    label: 'Closed (Healthy)',
  },
  OPEN: {
    text: 'text-rose-400',
    bg: 'bg-rose-950/40',
    border: 'border-rose-700/50',
    dot: 'bg-rose-400',
    label: 'Open (Tripped)',
  },
  HALF_OPEN: {
    text: 'text-amber-400',
    bg: 'bg-amber-950/40',
    border: 'border-amber-700/50',
    dot: 'bg-amber-400',
    label: 'Half-Open (Probing)',
  },
} as const;

export const DENSITY = {
  tableRowHeight: 'h-9',
  fontSizeData: 'text-xs',
  fontSizeHeader: 'text-2xs',
  fontMono: 'font-mono text-2xs',
} as const;
