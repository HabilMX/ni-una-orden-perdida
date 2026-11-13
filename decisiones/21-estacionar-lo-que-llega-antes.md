# Lo que llega antes de tiempo se estaciona, no se rechaza

> **Dónde aparece:** lección 17.

## El problema

La paquetería avisa que entregó la orden 4002, pero la bodega todavía no manda su embarque. Para la API y el ERP,
una entrega sin embarque es un error: el ERP la rechaza (`registrar_entrega` exige que el pedido esté EMBARCADO) y
la API contesta `422 fuera-de-orden`. El aviso no está mal; llegó antes. Si el router lo manda tal cual, el destino
lo rechaza y la entrega queda perdida; si el router lo rechaza, la paquetería no tiene otra forma de avisar.

## Qué decidimos

El router guarda el aviso igual que en la 16 (ficha 18) y contesta 202, pero cada operación de la entrega **nace
estacionada** en el canal donde todavía no está aceptado el embarque. Quién necesita a quién lo dice un archivo,
`reglas/maquina-de-estados.yaml`, y la decisión la calcula un mapeo con sus casos de oro: procesa, estaciona o no es
válido. Lo estacionado espera en la base de estado, sin intentos y sin salir a ningún destino.

Lo suelta el **barrido**: vuelve a decidir cada operación estacionada con lo que hoy hay en la base y, si ya puede
salir, la pasa a pendiente y la encola en el reparto. El barrido corre cuando un canal acepta un momento (el evento
que puede destrabar a otro) y también se puede pedir a mano (`POST /barrido`). El cambio de estado se hace con
`… AND estado = 'estacionada'`: si dos barridos corren a la vez, solo uno la cambia, y la entrega sale una vez.

## Por qué

- **El orden de llegada no es el orden de los hechos.** Entre sistemas que no se coordinan entre sí, un aviso puede
  adelantarse a otro. El patrón Resequencer existe para esto: guardar lo que llegó fuera de orden y soltarlo en el
  orden correcto.
- **Estacionar no pierde nada y no inventa nada.** El aviso queda guardado, con su contenido, a la vista en la base;
  no se manda a un destino que lo va a rechazar.
- **La regla vive en un solo lugar y se lee como datos.** Quién necesita a quién está en el YAML; agregar un momento
  (una devolución, por ejemplo) es agregar una línea, no otra ruta.
- **Por canal, porque cada destino lleva su propio orden.** Que la API ya tenga el embarque no dice nada del ERP.
- **El barrido por evento contesta al momento en que algo cambió**, sin revisar la base cada tanto cuando no pasó
  nada.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Rechazar el aviso con 4xx | La paquetería no tiene cómo mandar el embarque; lo reintentaría igual, o se rendiría |
| Mandarlo al destino y dejar que reintente | El destino lo rechaza con 4xx, la operación queda «rechazada» y nadie la vuelve a mandar |
| El resequencer de Camel (`resequence`) en memoria | Guarda lo que espera en la memoria del proceso: si el router se reinicia, se pierde lo estacionado |
| Un barrido programado cada N segundos | Corre aunque no haya nada que soltar; el evento de «canal aceptó» ya dice cuándo vale la pena |
| Escribir las reglas como `choice` en la ruta | Cada momento nuevo sería otra rama; el YAML y su mapeo con casos de oro se prueban aparte |

## Qué nos haría reabrirla

- Que algo se quede estacionado sin plazo (un embarque que no llega): hace falta un plazo y un aviso a una
  persona. Es lo que mide la conciliación de las lecciones siguientes.
- Que los momentos tengan más de un camino (una devolución antes de la entrega): la máquina tendría que decir
  «uno de estos», no solo «este».

## Fuentes

- Resequencer, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/Resequencer.html
- Resequence EIP de Camel: https://camel.apache.org/components/next/eips/resequence-eip.html
- Componente SQL de Camel (parámetros y `CamelSqlUpdateCount`): https://camel.apache.org/components/next/sql-component.html
- Formato de datos YAML (SnakeYAML) de Camel: https://camel.apache.org/components/next/dataformats/snakeYaml-dataformat.html
