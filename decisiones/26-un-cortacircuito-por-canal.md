# Un cortacircuito por canal, en el router

> **Dónde aparece:** lección 22.

## El problema

Se cae el SFTP del socio. Desde la 19, el router reintenta en el lugar lo que no sabe cómo le fue, y la cola de
reparto espera con él: cada envío al socio tarda medio minuto en rendirse, y mientras tanto la API y el ERP no
reciben nada, aunque estén bien. Un canal caído frena a los otros dos.

## Qué decidimos

- La llamada a cada canal va detrás de **su propio cortacircuito** (`circuitBreaker` de Camel, con Resilience4j):
  `cortacircuito-rest`, `cortacircuito-bd` y `cortacircuito-edi`.
- **Cerrado**: se llama. Con al menos 2 llamadas y la mitad o más fallando (de las últimas 4), **se abre**.
- **Abierto**: durante 10 s no se llama al canal. La operación no se manda, su intento queda como `no_enviado` y la
  operación sigue `pendiente`: no le pasó nada en el destino. No se reintenta en el lugar, así que la cola sigue.
- **Medio abierto**: pasados los 10 s, se deja pasar una llamada de prueba; si sale bien, se cierra.
- Cuenta como falla del canal un 5xx o la falta de respuesta. Un 4xx no: es un «no» del destino, y el canal está bien.
  Tampoco un 429: el canal contestó, y su «prueba después» lo atiende el reintento con espera (ficha 24).
- Lo que quedó pendiente con el cortacircuito abierto se reprocesa por la misma puerta que lo demás (lección 23).

## Por qué

- **Uno por canal**, porque cada canal se cae por su lado: si el socio no contesta, la API no tiene la culpa.
- **No llamar a lo que sabemos caído** ahorra la espera y no lo golpea mientras vuelve.
- **«No enviado» es un hecho, no una duda**: con el cortacircuito abierto, el router sabe que no llamó. Por eso no es
  `no_se_sabe` y no va a conciliación.
- **La prueba de medio abierto** deja que el canal se recupere solo, sin que alguien tenga que cerrarlo a mano.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Un solo cortacircuito para los tres canales | Un canal caído lo abriría para todos |
| Solo reintentos, sin cortacircuito | Cada envío al canal caído vuelve a esperar todos sus reintentos, y la cola con él |
| Contar los 4xx como falla | Un pedido rechazado por negocio abriría el cortacircuito de un destino que está bien |
| Marcar lo cortado como `no_se_sabe` | Mandaría a conciliación algo que se sabe que no salió |

## Qué nos haría reabrirla

- Que un canal se caiga seguido y por poco tiempo: la ventana y la espera se ajustarían con lo que muestre el trace.
- Que haya varias copias del router: cada una tendría su propio cortacircuito, y cada una lo descubriría por su cuenta.
- En Camel Quarkus nativo no está Resilience4j: el cortacircuito lo da MicroProfile Fault Tolerance, con la misma
  llamada `circuitBreaker` en el YAML.

## Fuentes

- Circuit Breaker EIP de Camel: https://camel.apache.org/components/next/eips/circuitBreaker-eip.html
- Resilience4j EIP de Camel: https://camel.apache.org/components/next/eips/resilience4j-eip.html
- Opciones de `resilience4jConfiguration` en Camel: https://camel.apache.org/components/next/eips/resilience4jConfiguration-eip.html
- CircuitBreaker, en la documentación de Resilience4j: https://resilience4j.readme.io/docs/circuitbreaker
