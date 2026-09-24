import { useEffect } from 'react';
import { useUploadFlow } from './hooks/useUploadFlow';
import { useUploadLibrary } from './hooks/useUploadLibrary';
import { Panel } from './components/rack/Panel';
import { Indicator } from './components/rack/Lamp';
import { Controls } from './components/Controls';
import { FaultLog } from './components/FaultLog';
import { Handoff } from './components/Handoff';
import { Intake } from './components/Intake';
import { MeterBridge } from './components/MeterBridge';
import { PatchBay } from './components/PatchBay';
import { SignalPath } from './components/SignalPath';
import { TapeLibrary, LIBRARY_FILTERS } from './components/TapeLibrary';
import { Transport } from './components/Transport';
import { formatBytes, shortId } from './lib/format';

export default function App() {
  const flow = useUploadFlow();
  const { state, serverSession, events, file } = flow;
  const library = useUploadLibrary(flow.api);

  // A finished or abandoned transmission changes what the catalogue holds.
  useEffect(() => {
    if (state.status === 'done' || state.status === 'cancelled' || state.status === 'failed') {
      void library.refresh();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.status]);

  const polls = events.filter(
    (event) => event.label.startsWith('Poll upload status') && event.kind === 'response',
  ).length;

  const stored = state.parts.filter((part) => part.state === 'uploaded').length;
  const startable = Boolean(file) && ['idle', 'failed', 'cancelled'].includes(state.status);
  const retrying = state.status === 'failed' && Boolean(state.uploadId);

  return (
    <div className="rack">
      <div className="rack__inner">
        <header className="nameplate">
          <div>
            <h1>
              upload<em>·</em>api <em>/</em> sala de máquinas
            </h1>
            <p className="nameplate__sub">
              Cinco gigas sin dejar a nadie mirando una barra. Cada lámpara, cada medidor y
              cada cable de esta pantalla está accionado por una petición real contra el API
              que corre en tu máquina.
            </p>
          </div>
          <div className="nameplate__spec">
            <span className="spec">
              Endpoint<b>{flow.config.baseUrl}</b>
            </span>
            <span className="spec">
              Operador<b>{shortId(flow.config.userId)}…</b>
            </span>
            {serverSession ? (
              <span className="spec">
                Canal<b>{formatBytes(serverSession.partSize, 0)}</b>
              </span>
            ) : null}
          </div>
        </header>

        <div className="bay">
          <div className="bay__column">
            {!file ? (
              <Panel
                designation="Admisión"
                stencil="sin cinta montada"
                legend="El API acepta cualquier tipo de contenido y conserva el nombre original para la descarga. La clave de almacenamiento se sanea aparte."
              >
                <Intake onFile={flow.selectFile} />
              </Panel>
            ) : (
              <Transport file={file} state={state} serverSession={serverSession}>
                <div className="key-bank">
                  <button className="key key--armed" disabled={!startable} onClick={flow.start}>
                    <span className={`key__led${state.status === 'uploading' ? ' key__led--on' : ''}`} />
                    {retrying ? 'Reintentar' : 'Transmitir'}
                  </button>
                  {state.status === 'paused' ? (
                    <button className="key" onClick={flow.unpause}>
                      Continuar
                    </button>
                  ) : (
                    <button
                      className="key"
                      disabled={state.status !== 'uploading'}
                      onClick={flow.pause}
                    >
                      Pausa
                    </button>
                  )}
                  <button
                    className="key"
                    disabled={!state.uploadId || state.status === 'cancelled'}
                    onClick={flow.cancel}
                  >
                    Abortar
                  </button>
                  <button className="key" onClick={flow.reset}>
                    Expulsar
                  </button>
                </div>
              </Transport>
            )}

            {flow.error ? (
              <div className="fault-strip">
                <div>
                  <div className="fault-strip__title">Transmisión detenida</div>
                  <div className="fault-strip__detail">{flow.error}</div>
                </div>
              </div>
            ) : null}

            <Panel
              designation="Puente de medidores"
              stencil={
                state.parts.length ? `${stored}/${state.parts.length} canales` : 'sin canales'
              }
              controls={
                <span className="indicator-bank">
                  <Indicator label="ON AIR" colour="tally" on={state.parts.some((p) => p.state === 'uploading')} />
                  <Indicator label="READY" colour="ready" on={stored > 0} />
                  <Indicator label="PLAYBACK" colour="playback" on={state.parts.some((p) => p.restored)} />
                  <Indicator label="FAULT" colour="fault" on={state.parts.some((p) => p.state === 'failed')} blink />
                </span>
              }
              legend="Un canal por parte. La aguja sube de golpe y baja despacio, como un medidor de programa de verdad, y la marca blanca es la retención de pico. Rojo no es un error aquí: es transmitiendo."
            >
              <MeterBridge parts={state.parts} selfTest={state.uploadId} />
            </Panel>

            <Panel
              designation="Bahía de patcheo"
              stencil="enrutamiento real"
              legend="El cable grueso del suelo es el archivo. Sale del jack del navegador y entra en el del almacenamiento sin tocar el del API en ningún punto del trayecto."
            >
              <PatchBay events={events} state={state} />
            </Panel>

            {state.status === 'done' ? <Handoff session={serverSession} polls={polls} /> : null}

            <Panel
              designation="Catálogo de cintas"
              stencil={library.loading ? 'leyendo…' : `${library.data?.totalElements ?? 0} registradas`}
              controls={
                <span style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span className="selector">
                    {LIBRARY_FILTERS.map((option) => (
                      <button
                        key={option}
                        data-on={library.filter === option}
                        onClick={() => library.changeFilter(option)}
                      >
                        {option}
                      </button>
                    ))}
                  </span>
                  <button className="key key--sm key--dark" onClick={() => void library.refresh()}>
                    Releer
                  </button>
                </span>
              }
              legend="Lo que el API guarda para este operador, no lo que recuerda esta pestaña. Las transmisiones a medias se retoman montando otra vez el mismo archivo: se valida nombre y tamaño antes de enviar un solo byte."
            >
              <TapeLibrary
                data={library.data}
                filter={library.filter}
                page={library.page}
                loading={library.loading}
                error={library.error}
                onPage={library.setPage}
                onDownload={library.download}
                onResume={flow.resumeFromLibrary}
                canResume={Boolean(file)}
              />
            </Panel>

            <Panel designation="Banco de pruebas" stencil="para romperlo a propósito">
              <Controls
                settings={flow.settings}
                state={state}
                onSettings={flow.setSettings}
                onResumeFromServer={flow.resumeFromServer}
              />
            </Panel>
          </div>

          <div className="bay__column bay__column--side">
            <Panel
              designation="Cadena de señal"
              stencil={serverSession?.status ?? 'sin sesión'}
              legend="Los dos últimos relés no tienen cable detrás porque nada los acciona todavía."
            >
              <SignalPath status={serverSession?.status} />
            </Panel>

            <Panel
              designation="Registro"
              stencil={`${events.length} eventos · ${polls} sondeos`}
              flush
              legend="Sin canal de push, el navegador pregunta en bucle. Ese repique idéntico es el precio del polling, y por eso está a la vista."
            >
              <div style={{ paddingTop: 14 }}>
                <FaultLog events={events} />
              </div>
            </Panel>
          </div>
        </div>
      </div>
    </div>
  );
}
