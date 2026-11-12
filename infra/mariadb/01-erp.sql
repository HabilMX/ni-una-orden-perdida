-- El ERP emulado. No es permisivo: valida lo que recibe, como uno real.
CREATE TABLE pedido_cabecera (
  pedido       VARCHAR(20) PRIMARY KEY,
  tienda       VARCHAR(10) NOT NULL,
  fecha        DATETIME NOT NULL,
  total        DECIMAL(12,2) NOT NULL,
  estatus      VARCHAR(20) NOT NULL DEFAULT 'RECIBIDO',
  correlacion  VARCHAR(64) NULL     -- el hilo de la integración que lo trajo (lección 09); vacío si nadie lo mandó
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
