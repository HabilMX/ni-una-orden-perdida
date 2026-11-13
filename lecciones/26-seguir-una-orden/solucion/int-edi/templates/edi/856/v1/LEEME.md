# Plantilla del X12 856 (aviso de embarque)

- **Solo acomoda.** Todo dato viene ya calculado por el mapeo (`mappings/edi/856/v1/mapeo.jsonata`): rellenos,
  fechas, la numeración de los niveles HL, la cuenta de segmentos y el texto limpio.
- **Lo fijo de la plantilla** es lo que no cambia de un embarque a otro:
  - `GS*SH`: el grupo es de avisos de embarque (en el 850 es `PO`).
  - `BSN*00*…*0004`: documento original (`00`) con la estructura «embarque, orden, artículo» (`0004`).
  - `HL*1**S*1` es el embarque (nivel `S`, sin padre, con hijos); `HL*2*1*O*1` es la orden (`O`, cuelga del 1).
    Cada artículo es un `HL*n*2*I*0` (`I`, cuelga de la orden, sin hijos).
  - `TD5**2*<paquetería>*U`: la paquetería se identifica con su código de transportista (`2`, el SCAC) y viaja
    como servicio de paquetería (`U`).
  - `REF*CN*<guía>`: la guía es el número de referencia del transportista (`CN`).
  - `DTM*011`: la fecha y hora en que salió el embarque (`011` = embarcado).
  - `PRF*<orden>`: a qué orden de compra corresponde. `LIN*n*VN*<sku>` y `SN1**<cantidad>*EA`: qué salió y cuánto.
- **`CTT`** cuenta los renglones de artículo. Cada socio dice en su guía de implementación qué espera ahí; esta
  práctica usa el número de artículos.
- **Llaves triples `{{{ }}}`** y **sin comentarios dentro de la plantilla**, por las mismas razones que en el 850.
