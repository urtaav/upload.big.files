import type { ReactNode } from 'react';

interface PanelProps {
  /** Silkscreened title on the left of the faceplate. */
  designation: string;
  /** Small stencilled note beside it: a count, a state, a unit. */
  stencil?: string;
  /** Anything mounted on the right of the faceplate: lamps, a selector, a key. */
  controls?: ReactNode;
  /** Engraved text under the panel. */
  legend?: ReactNode;
  /** Brushed aluminium instead of dark anodized. Reserved for the transport. */
  alu?: boolean;
  /** Content sits directly on the faceplate with no padding of its own. */
  flush?: boolean;
  children: ReactNode;
}

/**
 * A piece of rack equipment. Replaces the card: square corners, metal bevels,
 * screws in the ears, and a silkscreened designation rather than a heading.
 */
export function Panel({
  designation,
  stencil,
  controls,
  legend,
  alu,
  flush,
  children,
}: PanelProps) {
  return (
    <section className={`panel${alu ? ' panel--alu' : ''}`}>
      <header className="panel__face">
        <h2 className="panel__designation">
          {designation}
          {stencil ? <span>{stencil}</span> : null}
        </h2>
        {controls}
      </header>
      {flush ? children : <div className="panel__body">{children}</div>}
      {legend ? <p className="panel__legend">{legend}</p> : null}
    </section>
  );
}
