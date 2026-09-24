import { useRef, useState } from 'react';
import { formatBytes } from '../lib/format';

const SYNTHETIC_SIZES_MIB = [25, 120, 500];

/**
 * Builds a file of the requested size without allocating it twice: the same
 * 1 MiB chunk is handed to the Blob constructor N times and the browser spills
 * to disk on its own.
 */
function syntheticFile(sizeMib: number): File {
  const chunk = new Uint8Array(1024 * 1024);
  for (let i = 0; i < chunk.length; i += 997) chunk[i] = i % 251;
  const chunks = Array.from({ length: sizeMib }, () => chunk);
  return new File(chunks, `prueba-${sizeMib}MB.mp4`, { type: 'video/mp4' });
}

export function Intake({ onFile }: { onFile: (file: File) => void }) {
  const [armed, setArmed] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <div
      className={`well intake${armed ? ' intake--armed' : ''}`}
      onDragOver={(event) => {
        event.preventDefault();
        setArmed(true);
      }}
      onDragLeave={() => setArmed(false)}
      onDrop={(event) => {
        event.preventDefault();
        setArmed(false);
        const dropped = event.dataTransfer.files?.[0];
        if (dropped) onFile(dropped);
      }}
    >
      <h2>Monta una cinta</h2>
      <p>
        Cualquier tipo, cualquier tamaño hasta el límite del API. No pasa por Spring ni se
        carga en memoria: se corta en canales y cada canal sale firmado hacia el
        almacenamiento.
      </p>

      <div className="intake__keys">
        <button className="key key--armed" onClick={() => inputRef.current?.click()}>
          Elegir archivo
        </button>
        {SYNTHETIC_SIZES_MIB.map((mib) => (
          <button key={mib} className="key key--dark" onClick={() => onFile(syntheticFile(mib))}>
            Generar {formatBytes(mib * 1024 * 1024, 0)}
          </button>
        ))}
      </div>

      <p className="intake__spec">el nombre original se conserva tal cual para la descarga</p>

      <input
        ref={inputRef}
        type="file"
        hidden
        onChange={(event) => {
          const picked = event.target.files?.[0];
          if (picked) onFile(picked);
          event.target.value = '';
        }}
      />
    </div>
  );
}
