/**
 * A seven-segment display, drawn rather than typeset.
 *
 * No font reproduces the mitred segment joints or the dark ghost of an unlit
 * segment, and the ghost is what makes the readout look like a real counter
 * instead of red text.
 */

// Segment order: a (top), b, c, d (bottom), e, f, g (middle).
const SHAPES: Record<string, string> = {
  a: '8,2 32,2 27,7 13,7',
  b: '33,3 33,27 28,23 28,8',
  c: '33,31 33,55 28,50 28,35',
  d: '8,56 32,56 27,51 13,51',
  e: '7,31 7,55 12,50 12,35',
  f: '7,3 7,27 12,23 12,8',
  g: '9,29 31,29 27,33 13,33 ',
};

const GLYPHS: Record<string, string> = {
  '0': 'abcdef',
  '1': 'bc',
  '2': 'abged',
  '3': 'abgcd',
  '4': 'fgbc',
  '5': 'afgcd',
  '6': 'afgedc',
  '7': 'abc',
  '8': 'abcdefg',
  '9': 'abcdfg',
  '-': 'g',
  ' ': '',
};

interface SegmentsProps {
  /** Characters to display: digits, spaces, hyphens, and ':' or '.' separators. */
  value: string;
  /** Height of one digit in pixels; width follows the glyph's own ratio. */
  height?: number;
}

export function Segments({ value, height = 36 }: SegmentsProps) {
  const width = Math.round(height * 0.56);

  return (
    <span
      className="segments"
      style={{ ['--digit-h' as string]: `${height}px`, ['--digit-w' as string]: `${width}px` }}
      role="img"
      aria-label={value}
    >
      {value.split('').map((char, index) => {
        if (char === ':' || char === '.') {
          return (
            <span className="segment-sep" key={index} aria-hidden="true">
              {char}
            </span>
          );
        }
        const lit = GLYPHS[char] ?? '';
        return (
          <span className="segment-digit" key={index} aria-hidden="true">
            <svg viewBox="0 0 40 58" preserveAspectRatio="none">
              {Object.entries(SHAPES).map(([name, points]) => (
                <polygon key={name} points={points} data-off={lit.includes(name) ? undefined : 'true'} />
              ))}
            </svg>
          </span>
        );
      })}
    </span>
  );
}

/**
 * Right-aligns a number into a fixed number of digit cells, so the readout
 * keeps its width and the eye can park on one position.
 */
export function padDigits(value: number, cells: number): string {
  const text = String(Math.max(0, Math.floor(value)));
  return text.length >= cells ? text.slice(-cells) : ' '.repeat(cells - text.length) + text;
}
