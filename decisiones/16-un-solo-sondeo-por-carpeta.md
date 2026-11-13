# Una carpeta, un solo sondeo

> **Dónde aparece:** lección 13.

## El problema

La bodega no avisa: deja un archivo en una carpeta. Alguien tiene que ir a revisarla cada tanto. Si lo hacen dos
copias a la vez, las dos pueden tomar el mismo archivo y el embarque sale dos veces. Y si alguien toma el archivo
mientras la bodega todavía lo escribe, se lleva un embarque a medias.

## Qué decidimos

El sondeo de la carpeta (Polling Consumer) es un **servicio aparte, `sondeo-bodega`, que corre en una sola copia**.
No hace nada más que leer la carpeta y pasarle cada embarque al router por HTTP; todo lo que puede crecer en copias
(el router, los canales) queda del otro lado de esa llamada. El componente `file` de Camel se configura con cuatro
cuidados:

1. **`include`**: solo toma los archivos con el nombre de un embarque terminado (`embarque-<algo>.json`). La bodega
   escribe primero un `.tmp` y lo renombra al final, así que el `.tmp` no se toca.
2. **`readLock: changed`**: si alguien escribe directo con el nombre final, espera a que el archivo deje de crecer.
3. **`move` y `moveFailed`**: lo que el router recibió se aparta a `.camel/` y lo que falló a `.error/`. No se
   borra nada: queda la evidencia de qué llegó y qué no.
4. **`delay`**: cada cuánto se revisa la carpeta (en la práctica, cada 2 segundos).

## Por qué

- **Una sola copia es la forma más simple de que nadie más tome el archivo.** El componente `file` aparta el archivo
  al terminar, pero entre que una copia lo lee y lo aparta, otra puede leerlo también.
- **El que sondea no necesita crecer.** Leer una carpeta es poco trabajo; lo pesado (mapear, llamar a los destinos)
  lo hacen el router y los canales, que sí pueden tener varias copias (lección 12).
- **Apartar es mejor que borrar.** Con `.camel/` y `.error/` cualquiera ve qué se procesó y qué hay que revisar.
- **El destino también se protege.** Si un embarque llegara dos veces, la API lo reconoce por su llave de
  idempotencia (ficha 05). Pero el ERP y el SFTP no tienen por qué pagar un error que se evita en el origen.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Que el router sondee la carpeta | El router crece en copias; cada copia sondearía la misma carpeta |
| Varias copias del sondeo con un candado entre ellas (por ejemplo, el componente `master` de Camel, que elige una sola copia activa) | Resuelve lo mismo con más piezas: un servicio de coordinación que operar. Vale cuando el sondeo no puede detenerse ni un momento |
| Tomar cualquier archivo y borrarlo al terminar | Se lleva los `.tmp` a medias, y si algo falla no queda rastro |
| Pedirle a la bodega que llame al router por HTTP | Sería lo ideal, pero la bodega no lo hace: deja archivos. La integración se adapta a lo que el otro sistema sabe hacer |

## Qué nos haría reabrirla

- Que el sondeo tenga que seguir trabajando mientras su única copia se reinicia: ahí entra una copia de respaldo con
  candado (`master`, o un candado en la base).
- Que la bodega aprenda a avisar por HTTP o por un broker: el sondeo deja de hacer falta.

## Fuentes

- Componente File de Camel (`include`, `readLock`, `move`, `moveFailed`, `delay`): https://camel.apache.org/components/next/file-component.html
- Polling Consumer, de *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/patterns/messaging/PollingConsumer.html
- Componente Master de Camel (una sola copia activa entre varias): https://camel.apache.org/components/next/master-component.html
