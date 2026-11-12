# Sin casos dorados no hay mapeo

> **Dónde muerde:** lección 03 en adelante.

## Qué decidimos

Cada mapeo trae sus **casos dorados**: `cases/<caso>/entrada.json` y `esperado.json`. Un mapeo nuevo o un cambio a uno
existente no se da por bueno hasta que todos sus casos dan exactamente lo esperado.

## Por qué

- **Un mapeo es comportamiento, no un ajuste.** Cambiarlo cambia lo que recibe un destino; se prueba como código.
- **Los casos dicen qué se espera sin importar la herramienta.** Si mañana el mapeo se reescribe, los casos siguen
  valiendo.
- **Cubren los bordes:** centavos que no suman exacto en binario, caracteres que rompen un EDI, renglones inválidos.
- `probar-mapeo` se niega a dar verde con cero casos: un verde sin pruebas no cuenta.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Probar el mapeo solo corriendo la ruta completa | Lento, y cuando falla no sabes si fue el mapeo o el destino |
| Revisar el mapeo a ojo | No deja evidencia y no detecta regresiones |

## Qué nos haría reabrirla

- Ninguna previsible: es la base para poder cambiar un mapeo sin miedo.

## Fuentes

- Idea general de pruebas de caracterización (golden master): https://en.wikipedia.org/wiki/Characterization_test
