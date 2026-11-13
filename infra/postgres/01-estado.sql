-- Estado de la integración. Lo escribe SOLO el router (ver conceptos/decisiones/).
-- Una orden; una operación por orden, momento y canal; uno o más intentos por operación.
CREATE TABLE orden (
  orden        text PRIMARY KEY,
  momento      text NOT NULL,              -- creada | embarcada | entregada
  datos        jsonb NOT NULL,
  recibida     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE operacion (
  id           bigserial PRIMARY KEY,
  orden        text NOT NULL REFERENCES orden(orden),
  momento      text NOT NULL,
  canal        text NOT NULL,              -- rest | bd | edi
  -- estacionada: llegó antes de lo que necesita (una entrega sin su embarque) y espera a que la suelten (lección 17)
  estado       text NOT NULL,              -- pendiente | estacionada | aceptada | rechazada | no_se_sabe | conciliacion_pendiente
  numero_control text,                     -- lo asigna el router con numero_control_seq (lección 15)
  correlacion  text,                       -- el hilo con el que llegó este momento (lección 09); viaja al reenviarlo
  huella       text,                       -- sha256 de lo que llegó en este momento (lección 18): reconoce un repetido
  UNIQUE (orden, momento, canal)            -- la llave del momento (su ce-id) + el canal: no hay dos iguales
);

-- Un aviso con la llave de uno que ya teníamos, pero con otro contenido (lección 18). No es un repetido: alguien
-- cambió algo. No se reparte ni pisa lo guardado; queda aquí, a la vista, para que una persona decida.
CREATE TABLE conflicto (
  id             bigserial PRIMARY KEY,
  llave          text NOT NULL,            -- el ce-id del momento: orden-<orden>-<momento>
  orden          text NOT NULL,
  momento        text NOT NULL,
  huella_guardada text NOT NULL,
  huella_nueva   text NOT NULL,
  cuerpo         jsonb NOT NULL,           -- lo que llegó la segunda vez, completo
  correlacion    text,
  recibido       timestamptz NOT NULL DEFAULT now()
);

-- El número de control del sobre X12 (ISA13, GS06) de cada documento que sale al socio. Desde la lección 15 lo
-- asigna el router, uno nuevo por documento: así un 850 y un 856 de la misma orden no comparten número.
-- El ISA pide 9 dígitos; la secuencia arranca en 100000001 para que todos midan lo mismo.
CREATE SEQUENCE numero_control_seq START WITH 100000001 MAXVALUE 999999999;

CREATE TABLE intento (
  id           bigserial PRIMARY KEY,
  operacion    bigint NOT NULL REFERENCES operacion(id),
  n            int NOT NULL,
  tipo         text NOT NULL DEFAULT 'auto', -- auto | manual (rerun)
  pedido_por   text,                        -- quién pidió el rerun
  motivo       text,                        -- y por qué
  version_mapeo text,
  tamano       int,
  sha256       text,
  desenlace    text,                        -- NULL mientras va en camino: se escribe ANTES de enviar
  respuesta    jsonb,
  inicio       timestamptz NOT NULL DEFAULT now(),
  fin          timestamptz,
  UNIQUE (operacion, n)
);
