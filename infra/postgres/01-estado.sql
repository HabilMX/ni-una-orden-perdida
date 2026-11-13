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
  UNIQUE (orden, momento, canal)
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
