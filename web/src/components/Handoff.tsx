import { Panel } from './rack/Panel';
import { Indicator } from './rack/Lamp';
import type { UploadResponse } from '../lib/types';

interface HandoffProps {
  session: UploadResponse | null;
  polls: number;
}

/**
 * Mounts the moment the upload reaches COMPLETED, which is exactly where the
 * synchronous flow runs out of wire. Everything it says is readable on the rack
 * behind it: the signal path stopped advancing and the polls keep returning the
 * same answer.
 */
export function Handoff({ session, polls }: HandoffProps) {
  return (
    <Panel
      designation="Fin de línea"
      stencil={session ? `api · ${session.status}` : undefined}
      controls={<Indicator label="SIN CONSUMIDOR" colour="fault" on blink />}
      legend={
        <>
          El enum <code>UploadStatus</code> ya declara{' '}
          <code>COMPLETED → PROCESSING → READY</code>. Las dos últimas transiciones no las
          acciona nadie todavía.
        </>
      }
    >
      <p className="handoff__lead">
        El objeto existe en el almacenamiento y la petición HTTP terminó. Llevas {polls}{' '}
        {polls === 1 ? 'consulta' : 'consultas'} de estado devolviendo lo mismo.
      </p>
      <p className="handoff__body">
        Subir nunca necesitó Kafka: el navegador habla directo con el almacenamiento y el API
        solo lleva el libro. La pregunta aparece después de <code>COMPLETED</code>, cuando un
        hecho — «el archivo está arriba» — debe disparar trabajo que dura minutos y que nadie
        está esperando en pantalla.
      </p>

      <div className="options">
        <div className="option">
          <h4>Nada</h4>
          <p>
            Correcto si no hay post-proceso. La subida ya terminó y el usuario ya se fue. No
            añadas infraestructura para un problema que no tienes.
          </p>
        </div>
        <div className="option">
          <h4>@Async</h4>
          <p>
            Una sola tarea corta que puedes perder sin drama. Vive en el mismo proceso: si el
            pod reinicia a mitad, el trabajo desaparece y nadie se entera.
          </p>
        </div>
        <div className="option">
          <h4>Outbox + jobs</h4>
          <p>
            Sobrevive reinicios y da reintentos con la base que ya tienes. Techo: un solo
            consumidor lógico y polling sobre la BD.
          </p>
        </div>
        <div className="option option--live">
          <h4>Kafka</h4>
          <p>
            Un <code>upload.completed</code> con varios consumidores independientes —
            transcodificar, indexar, antivirus, avisar — que escalan y fallan por separado,
            con reintentos y replay. Es fan-out durable, no una cola de una tarea.
          </p>
        </div>
      </div>
    </Panel>
  );
}
