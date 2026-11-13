# Tres copias no reparten solas: el reparto es por conexión

> **Dónde aparece:** lección 12.

## El problema

Si pides tres copias de un servicio y las llamas por su nombre, todas las órdenes pueden caer en una sola copia, y
las otras dos se quedan sin hacer nada.

## Qué decidimos

Para crecer un servicio se piden más copias (réplicas) en un solo lugar, `deploy.replicas`, sin tocar a los demás
servicios. Y la lección enseña **por qué eso no basta**: quien llama abre una conexión y la reusa, y la red reparte
**conexiones**, no peticiones. Para que el trabajo se reparta, el router le da a cada conexión una **vida finita**
(`connectionTimeToLive` del componente `http`) y su JVM recuerda la dirección de cada servicio por **poco tiempo**
(`sun.net.inetaddr.ttl`). Al vencer la conexión, la siguiente pregunta otra vez por la dirección y puede caer en otra
copia. Las dos cosas van en el compose, como opciones de arranque; no hay archivo de propiedades.

El `verifica` no exige un reparto parejo, porque quien elige la copia es la red y no es exacto. Pide que **más de una
copia registre órdenes** y que entre todas sumen **las 30, una sola vez**. No pide que todas reciban: una copia
podría quedarse sin ninguna por azar del DNS, aunque la integración esté bien.

## Por qué

- **Es lo que pasa en la vida real.** Un cliente HTTP mantiene viva su conexión para no pagar el costo de abrirla en
  cada llamada; mientras viva, todas sus peticiones van a la misma copia.
- **En Docker reparte el DNS:** cada vez que alguien pregunta por `int-bd`, contesta las tres direcciones en distinto
  orden. Pero la JVM guarda la respuesta un rato, y mientras la guarda no vuelve a preguntar.
- **En Kubernetes la idea sigue siendo cierta.** Un Service elige un destino para cada **conexión** nueva; una conexión
  que nunca se cierra nunca se reparte.
- **La caché de direcciones de Java** se gobierna con `networkaddress.cache.ttl`, una propiedad de seguridad de la
  JVM, documentada. La práctica usa `sun.net.inetaddr.ttl`, una propiedad **privada** del JDK que hace lo mismo desde
  la línea de comando: así cabe en el compose sin archivos de configuración. Por ser privada, una versión futura de
  Java podría quitarla, y el `verifica` de la 12 lo delataría. En un proyecto real se prefiere la documentada.
- **El 1 segundo es para verlo en la práctica**, no una cifra recomendada. Cuánto debe vivir una conexión depende de
  cuánto tráfico hay y de qué tan rápido cambian las copias.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Pedir las réplicas y dar por hecho que reparten | Es justo el error que la lección mide: 30 órdenes a una copia, 0 a las otras |
| Cerrar la conexión después de cada llamada | Reparte, pero paga el costo de abrir una conexión en cada orden |
| Un balanceador propio entre el router y sus copias | Otra pieza que operar para resolver lo que la red ya hace por conexión |
| El patrón Load Balancer de Camel con la lista de copias | El router tendría que saber cuántas copias hay y cómo se llaman; al crecer o bajar, habría que cambiar la ruta |
| Pedir un reparto exacto (10 / 10 / 10) en el `verifica` | Al azar no sale exacto; el `verifica` fallaría sin que nada estuviera mal |

## Qué nos haría reabrirla

- Que los servicios se hablen por mensajes en un broker (lección 28): ahí el reparto lo hace el grupo de consumidores, no
  la conexión.
- Que la práctica pase a una malla de servicios, que reparte por petición y no por conexión.

## Fuentes

- Docker Compose, `deploy.replicas`: https://docs.docker.com/reference/compose-file/deploy/
- Docker Compose, redes (cada servicio se encuentra por su nombre): https://docs.docker.com/compose/how-tos/networking/
- Kubernetes, IP virtuales y proxies de los Services (cómo se elige el destino): https://kubernetes.io/docs/reference/networking/virtual-ips/
- Java, propiedades de red (`networkaddress.cache.ttl`): https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/doc-files/net-properties.html
- Componente HTTP de Camel (`connectionTimeToLive`): https://camel.apache.org/components/next/http-component.html
