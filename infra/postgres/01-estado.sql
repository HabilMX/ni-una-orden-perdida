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
  -- confirmada: el socio mandó su 997 de aceptación (lección 21); con un 997 de rechazo, la operación pasa a rechazada
  estado       text NOT NULL,              -- pendiente | estacionada | aceptada | confirmada | rechazada | no_se_sabe | conciliacion_pendiente
  numero_control text,                     -- lo asigna el router con numero_control_seq (lección 15)
  correlacion  text,                       -- el hilo con el que llegó este momento (lección 09); viaja al reenviarlo
  huella       text,                       -- sha256 de lo que llegó en este momento (lección 18): reconoce un repetido
  creada       timestamptz NOT NULL DEFAULT now(), -- desde cuándo se espera (lección 25): de aquí corre el plazo
  UNIQUE (orden, momento, canal)            -- la llave del momento (su ce-id) + el canal: no hay dos iguales
);

-- Un momento de la orden está COMPLETO cuando cada canal que le toca llegó a lo que la máquina de estados dice que
-- espera de él (lección 25): la API y el ERP, «aceptada»; el socio, «confirmada» (su 997). Una fila por momento.
-- Al truncar orden (como hacen los verifica) se va con ella.
CREATE TABLE completitud (
  orden        text NOT NULL REFERENCES orden(orden),
  momento      text NOT NULL,
  completa     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (orden, momento)
);

-- Un canal que pasó su plazo sin llegar a lo que se espera de él (lección 25). Avisa y NO cierra: el momento sigue
-- abierto, y si lo que faltaba llega tarde, se completa. Un aviso por canal y momento, aunque el barrido lo vea varias
-- veces.
CREATE TABLE alerta (
  id           bigserial PRIMARY KEY,
  orden        text NOT NULL REFERENCES orden(orden),
  momento      text NOT NULL,
  canal        text NOT NULL,
  tipo         text NOT NULL,              -- vencido
  estado       text NOT NULL,              -- en qué estado estaba el canal al vencer
  espera       text NOT NULL,              -- lo que se esperaba de él
  creada       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (orden, momento, canal, tipo)
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

-- Lo que salió en cada intento, completo (lección 26). El intento guarda su huella (sha256) y su tamaño; el cuerpo
-- vive aquí, una vez por huella: si dos intentos mandan lo mismo, comparten la fila. Es text, sin límite de largo:
-- no se recorta, por grande que sea.
CREATE TABLE payload (
  sha256       text PRIMARY KEY,
  cuerpo       text NOT NULL,
  tamano       int NOT NULL,
  guardado     timestamptz NOT NULL DEFAULT now()
);

-- Cada acuse 997 que mandó el socio (lección 21), tal como se leyó, y a qué operación correspondió. El 997 cita
-- lo que acusa por el grupo (AK1: GS01 y GS06) y el documento (AK2: ST01 y ST02). Si no corresponde a ninguna
-- operación esperándolo, operacion queda vacía: el acuse no se pierde, y se ve.
CREATE TABLE acuse (
  id             bigserial PRIMARY KEY,
  archivo        text NOT NULL,
  control_socio  text,                     -- ISA13 del 997: el número del socio, no el nuestro
  grupo_tipo     text NOT NULL,            -- AK1-01: PO, SH, QM
  grupo_control  text NOT NULL,            -- AK1-02: el GS06 que mandamos (operacion.numero_control)
  documento_tipo text NOT NULL,            -- AK2-01: 850, 856, 214
  documento_control text NOT NULL,         -- AK2-02: el ST02 que mandamos
  codigo         text NOT NULL,            -- AK5-01: A aceptado, R rechazado
  errores        jsonb,
  operacion      bigint REFERENCES operacion(id),
  recibido       timestamptz NOT NULL DEFAULT now()
);
