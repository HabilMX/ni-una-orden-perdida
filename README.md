# Ni una orden perdida

[![verifica](https://github.com/HabilMX/ni-una-orden-perdida/actions/workflows/verifica.yml/badge.svg)](https://github.com/HabilMX/ni-una-orden-perdida/actions/workflows/verifica.yml)

Una práctica abierta y gratuita de integración de sistemas con [Apache Camel](https://camel.apache.org/). Una tienda
inventada vende, y cada orden tiene que llegar a tres sistemas que no se parecen entre sí: una API REST, un ERP en
MariaDB y un socio comercial que recibe documentos EDI X12 por SFTP. Todo corre en tu máquina con Docker.

Lo que se aprende con las manos: que no se pierda ninguna orden, que todo deje rastro, que un reintento no duplique y
que un destino caído no tumbe a los demás.

## Antes de empezar

- Terminal y git, leer JSON y SQL básico.
- Docker con Compose. De Docker basta con `docker compose up` y `down`; lo demás viene escrito.
- No hace falta saber Java ni Camel: las rutas son YAML y se aprenden aquí.

Funciona en Linux, macOS y Windows con WSL.

## Cómo se trabaja

Cada lección vive en `lecciones/NN-…/` con tres cosas: `inicio/` (de donde partes), `solucion/` (la nuestra) y un
`verifica` que mide lo que llegó a cada sistema. Copias `inicio/` a `mio/`, trabajas ahí y corres `./verifica`: sale
en rojo diciendo qué falta, y en verde cuando lo lograste.

No es un recetario: cada lección abre pidiéndote que predigas qué va a pasar, te hace romper algo a propósito, y
termina ligando la ficha que explica por qué se hizo así.

## Temario

Son 31 lecciones, de la 00 a la 30, todas en Docker Compose. Se hacen en orden: cada una parte de lo que dejó la anterior.

### Que llegue

| # | Lección |
|---|---|
| 00 | [Ambiente listo](es/00-ambiente.md) |
| 01 | [Mi primera ruta corre](es/01-mi-primera-ruta.md) |
| 02 | [La orden llega a la API](es/02-la-orden-llega-a-la-api.md) |
| 03 | [Traducir la orden](es/03-traducir-la-orden.md) |
| 04 | [Al ERP](es/04-al-erp.md) |
| 05 | [Todo o nada](es/05-todo-o-nada.md) |
| 06 | [Armar el EDI](es/06-armar-el-edi.md) |
| 07 | [Al socio](es/07-al-socio.md) |
| 08 | [El router](es/08-el-router.md) |
| 09 | [El hilo entre servicios](es/09-el-hilo-entre-servicios.md) |
| 10 | [Todos los canales](es/10-todos-los-canales.md) |
| 11 | [No todo va a todos](es/11-no-todo-va-a-todos.md) |
| 12 | [Crecer en réplicas](es/12-crecer-en-replicas.md) |

### Que avance

| # | Lección |
|---|---|
| 13 | [Llega el embarque](es/13-llega-el-embarque.md) |
| 14 | [Un archivo, varias órdenes](es/14-un-archivo-varias-ordenes.md) |
| 15 | [El estado vive fuera del proceso](es/15-el-estado-vive-fuera-del-proceso.md) |
| 16 | [Llega la entrega](es/16-llega-la-entrega.md) |
| 17 | [La entrega llegó antes que la guía](es/17-la-entrega-llego-antes-que-la-guia.md) |

### Que no falle

| # | Lección |
|---|---|
| 18 | [¿Cuándo dos mensajes son el mismo?](es/18-cuando-dos-mensajes-son-el-mismo.md) |
| 19 | [El destino no contesta](es/19-el-destino-no-contesta.md) |
| 20 | [Aceptado, rechazado o no se sabe](es/20-aceptado-rechazado-o-no-se-sabe.md) |
| 21 | [Enviado no es confirmado](es/21-enviado-no-es-confirmado.md) |
| 22 | [Tirar un canal](es/22-tirar-un-canal.md) |
| 23 | [Reprocesar lo que falló](es/23-reprocesar-lo-que-fallo.md) |
| 24 | [No tumbar al destino](es/24-no-tumbar-al-destino.md) |
| 25 | [Esperar a los canales](es/25-esperar-a-los-canales.md) |
| 26 | [Seguir una orden](es/26-seguir-una-orden.md) |
| 27 | [Lo que el rastro no debe mostrar](es/27-lo-que-el-rastro-no-debe-mostrar.md) |

### Que escale

| # | Lección |
|---|---|
| 28 | [Un tópico, un grupo por integración](es/28-un-topico-un-grupo-por-integracion.md) |
| 29 | [No perder al consumir](es/29-no-perder-al-consumir.md) |
| 30 | [No perder al publicar](es/30-no-perder-al-publicar.md) |

### Apéndice opcional

La práctica termina en la 30. El apéndice lleva una integración a Kubernetes y la compara en JVM y en nativo; pide un
equipo con más memoria y disco, y lo dice al principio.

| # | Lección |
|---|---|
| 31 | [De laboratorio a proyecto, en Kubernetes](es/31-de-laboratorio-a-kubernetes.md) |
| 32 | [JVM contra nativo, medido](es/32-jvm-contra-nativo.md) |

## Más

- [Decisiones de diseño](decisiones/README.md): qué decidimos, por qué y qué nos haría cambiarlo.
- [Cambios](CHANGELOG.md): qué trae cada versión.
- ¿Algo no hace lo que dice? [Abre un issue](../../issues/new/choose).
- Licencia: ver [LICENSE.md](LICENSE.md).

---

> **Qué hace Hábil**
>
> Al cerrar esta práctica:
> - un aviso repetido no duplica nada;
> - si no se sabe si algo llegó, queda pendiente de conciliar en vez de darse por fallado;
> - lo depositado no se da por confirmado hasta que el socio acusa recibo;
> - un canal caído no frena a los demás;
> - cada destino recibe solo las llamadas que aguanta;
> - cada orden se puede seguir sin mostrar datos personales.
>
> En una empresa, esto suele sonar así: «No sabemos si esa operación llegó. Se reintenta a mano, y a veces se
> duplica». Y hay fallas que no marcan error: el envío salió, pero la respuesta nunca llegó.
>
> En Hábil construimos a la medida la integración entre su tienda, su ERP, sus socios y sus canales.
> **[Hábil MDW](https://www.habil.mx/es/habil-mdw/?utm_source=github&utm_medium=referral&utm_campaign=2026-11_ni-una-orden-perdida_es)**
> está diseñada para operar 24/7, con rastro de cada operación: qué se mandó a cada sistema, qué contestó, a qué hora
> y, si falló, por qué. Cada envío queda con su respuesta o con su motivo de falla, y los reintentos están diseñados
> para no repetir el efecto. Un envío que salió mal lo reintenta su equipo de operación como un envío nuevo, con
> rastro de quién lo hizo y sin tocar la base de datos a mano.
>
> **[Pida su primera conversación, sin costo.](https://wa.me/525667541323?text=Hola%2C%20vengo%20de%20GitHub%20%28Ni%20una%20orden%20perdida%29.%20Me%20interesa%20una%20primera%20conversaci%C3%B3n.%0AIndustria%3A%0APuesto%3A%0ASistemas%20principales%3A)**
