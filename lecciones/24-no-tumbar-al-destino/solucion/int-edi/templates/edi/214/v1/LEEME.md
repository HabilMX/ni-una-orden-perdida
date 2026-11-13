# Plantilla del X12 214 (estatus del transportista)

- **Solo acomoda.** Todo dato viene ya calculado por el mapeo (`mappings/edi/214/v1/mapeo.jsonata`), incluido el
  número de control que asignó el router.
- **Lo fijo de la plantilla** es lo que no cambia de una entrega a otra:
  - `GS*QM`: el grupo es de estatus del transportista (en el 850 es `PO` y en el 856, `SH`).
  - `B10*<guía>*<orden>*<paquetería>`: la guía de la paquetería, el número de embarque de quien lo mandó (aquí, la
    orden) y el código de la paquetería (su SCAC, de 2 a 4 letras).
  - `L11*<orden>*PO`: la referencia es una orden de compra (`PO`).
  - `LX*1`: el primer (y único) bloque de estatus.
  - `AT7*D1*NS***<fecha>*<hora>`: terminó de descargar en el destino (`D1`), sin novedad (`NS`), con su fecha
    (AAAAMMDD) y hora (HHMM). Los dos elementos vacíos son los de una cita, que aquí no aplica.
- **Llaves triples `{{{ }}}`** y **sin comentarios dentro de la plantilla**, por las mismas razones que en el 850.
