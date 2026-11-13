-- El ERP emulado. No es permisivo: valida lo que recibe, como uno real.
CREATE TABLE pedido_cabecera (
  pedido       VARCHAR(20) PRIMARY KEY,
  tienda       VARCHAR(10) NOT NULL,
  fecha        DATETIME NOT NULL,
  total        DECIMAL(12,2) NOT NULL,
  estatus      VARCHAR(20) NOT NULL DEFAULT 'RECIBIDO',
  correlacion  VARCHAR(64) NULL     -- el hilo de la integración que lo trajo (lección 10); vacío si nadie lo mandó
);

CREATE TABLE pedido_detalle (
  pedido       VARCHAR(20) NOT NULL,
  posicion     INT NOT NULL,
  sku          VARCHAR(20) NOT NULL,
  cantidad     INT NOT NULL CHECK (cantidad > 0),
  precio       DECIMAL(12,2) NOT NULL CHECK (precio >= 0),
  PRIMARY KEY (pedido, posicion),
  FOREIGN KEY (pedido) REFERENCES pedido_cabecera(pedido)
);

-- Cierra el registro: comprueba que el total cuadre con el detalle y lo marca REGISTRADO.
DELIMITER //
CREATE PROCEDURE registrar_pedido(IN p_pedido VARCHAR(20))
BEGIN
  DECLARE v_total DECIMAL(12,2);
  DECLARE v_suma  DECIMAL(12,2);
  SELECT total INTO v_total FROM pedido_cabecera WHERE pedido = p_pedido;
  IF v_total IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El pedido no existe';
  END IF;
  SELECT COALESCE(SUM(cantidad * precio), 0) INTO v_suma FROM pedido_detalle WHERE pedido = p_pedido;
  IF v_suma <> v_total THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El total no cuadra con el detalle';
  END IF;
  UPDATE pedido_cabecera SET estatus = 'REGISTRADO' WHERE pedido = p_pedido;
END //
DELIMITER ;

-- El embarque y la entrega (lección 13 en adelante). Un pedido se embarca una vez y se entrega una vez:
-- por eso la llave de las dos tablas es el pedido.
CREATE TABLE embarque (
  pedido       VARCHAR(20) PRIMARY KEY,
  guia         VARCHAR(30) NOT NULL,
  paqueteria   VARCHAR(10) NOT NULL,
  fecha        DATETIME NOT NULL,
  -- ON DELETE CASCADE: al borrar un pedido (como hacen los verifica de la 04 a la 12) se va con su embarque
  FOREIGN KEY (pedido) REFERENCES pedido_cabecera(pedido) ON DELETE CASCADE
);

CREATE TABLE entrega (
  pedido       VARCHAR(20) PRIMARY KEY,
  guia         VARCHAR(30) NOT NULL,
  fecha        DATETIME NOT NULL,
  FOREIGN KEY (pedido) REFERENCES pedido_cabecera(pedido) ON DELETE CASCADE
);

-- Registra que el pedido salió de la bodega: solo un pedido REGISTRADO, y lo deja EMBARCADO.
DELIMITER //
CREATE PROCEDURE registrar_embarque(IN p_pedido VARCHAR(20), IN p_guia VARCHAR(30),
                                    IN p_paqueteria VARCHAR(10), IN p_fecha DATETIME)
BEGIN
  DECLARE v_estatus VARCHAR(20);
  SELECT estatus INTO v_estatus FROM pedido_cabecera WHERE pedido = p_pedido FOR UPDATE;
  IF v_estatus IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El pedido no existe';
  END IF;
  IF v_estatus <> 'REGISTRADO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se embarca un pedido REGISTRADO';
  END IF;
  INSERT INTO embarque (pedido, guia, paqueteria, fecha) VALUES (p_pedido, p_guia, p_paqueteria, p_fecha);
  UPDATE pedido_cabecera SET estatus = 'EMBARCADO' WHERE pedido = p_pedido;
END //

-- Registra que la paquetería lo entregó: solo un pedido EMBARCADO y con la MISMA guía; lo deja ENTREGADO.
CREATE PROCEDURE registrar_entrega(IN p_pedido VARCHAR(20), IN p_guia VARCHAR(30), IN p_fecha DATETIME)
BEGIN
  DECLARE v_estatus VARCHAR(20);
  DECLARE v_guia    VARCHAR(30);
  SELECT estatus INTO v_estatus FROM pedido_cabecera WHERE pedido = p_pedido FOR UPDATE;
  IF v_estatus IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El pedido no existe';
  END IF;
  IF v_estatus <> 'EMBARCADO' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se entrega un pedido EMBARCADO';
  END IF;
  SELECT guia INTO v_guia FROM embarque WHERE pedido = p_pedido;
  IF v_guia IS NULL OR v_guia <> p_guia THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La guía no es la del embarque';
  END IF;
  INSERT INTO entrega (pedido, guia, fecha) VALUES (p_pedido, p_guia, p_fecha);
  UPDATE pedido_cabecera SET estatus = 'ENTREGADO' WHERE pedido = p_pedido;
END //
DELIMITER ;
