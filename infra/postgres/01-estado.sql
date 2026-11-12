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
  estado       text NOT NULL,              -- pendiente | aceptada | rechazada | no_se_sabe | conciliacion_pendiente
  numero_control text,                     -- lo asigna el router (lección 15)
  UNIQUE (orden, momento, canal)
);

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
