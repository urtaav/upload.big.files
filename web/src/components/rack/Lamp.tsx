export type LampColour = 'tally' | 'fault' | 'ready' | 'playback';

interface LampProps {
  colour: LampColour;
  on: boolean;
  /** Faults blink; a steady lamp and a blinking one mean different things. */
  blink?: boolean;
}

export function Lamp({ colour, on, blink }: LampProps) {
  return (
    <span
      className={`lamp lamp--${colour}${on ? ' lamp--on' : ''}${on && blink ? ' lamp--blink' : ''}`}
    />
  );
}

interface IndicatorProps extends LampProps {
  /** The engraved label under the lamp. Every lamp carries one. */
  label: string;
}

/**
 * A lamp with its engraving. Machine-room colour convention is the opposite of
 * web convention — red means transmitting, not broken — so no lamp is ever
 * shown without the word next to it.
 */
export function Indicator({ label, colour, on, blink }: IndicatorProps) {
  return (
    <span className={`indicator${on ? ' indicator--lit' : ''}`}>
      <Lamp colour={colour} on={on} blink={blink} />
      {label}
    </span>
  );
}
