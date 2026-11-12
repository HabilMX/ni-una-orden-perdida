# Cambios

Aquí se anota qué trae cada entrega de la práctica y qué se corrigió después. La forma sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/): lo más nuevo arriba.

## [Sin publicar] — Entrega 1, lecciones 00 a 12

### Lecciones
- **00 Ambiente listo:** el ambiente de la práctica con un solo comando: la API de pedidos, el ERP en MariaDB, el SFTP
  del socio y la base de estado.
- **01 Mi primera ruta corre** · **02 La orden llega a la API** · **03 Traducir la orden** · **04 La orden llega al
  ERP** · **05 Todo o nada** · **06 Armar el EDI** · **07 Al socio**: un destino a la vez, cada uno con su `verifica`.
- **08 El router** · **09 El hilo entre servicios** · **10 Todos los canales** · **11 No todo va a todos** · **12 Crecer
  en réplicas**: los servicios separados, la orden que sale a todos los canales y un servicio que crece solo.

### Decisiones
- 15 fichas en [`decisiones/`](decisiones/README.md), cada una con el problema, qué decidimos, por qué, qué
  descartamos y qué nos haría reabrirla.

### Cómo se comprueba
- Cada lección trae un `verifica` que mide el destino (la bitácora de la API, las filas del ERP, el archivo en el
  SFTP), no lo que dice la ruta. Con `inicio/` sale en rojo y con `solucion/` en verde.
- La verificación automática corre el `verifica` de cada lección con su solución en cada cambio.
