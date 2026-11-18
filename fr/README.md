# Pas une commande perdue

**Por Dorian Chávez, fundador de Hábil y arquitecto de integración.**

**À qui il s'adresse :** à celles et ceux qui utilisent déjà le terminal et git, lisent du JSON et connaissent les bases de SQL, et qui veulent apprendre l'intégration de
systèmes. Inutile de connaître Java ou Camel : les routes sont en YAML et s'apprennent ici.

**Ce que vous saurez faire à la fin :** obtenir, de vos propres mains, qu'aucune commande ne se perde, que tout soit traçable, qu'une
nouvelle tentative ne crée pas de doublon et qu'une destination en panne ne fasse pas tomber les autres.

**Ce qu'il vous faut avant de commencer :** le terminal et git, savoir lire du JSON et un SQL de base, et Docker avec Compose. De Docker, il suffit de
connaître `docker compose up` et `down` ; le reste est écrit dans les leçons. Le cours fonctionne sous Linux, macOS et Windows avec WSL.

## Le projet

Une boutique inventée vend, et chaque commande doit arriver dans trois systèmes qui ne se ressemblent pas : une API REST, un
ERP sous MariaDB et un partenaire commercial qui reçoit des documents EDI X12 par SFTP. Tout tourne sur votre machine avec Docker. C'est un
cours pratique, ouvert et gratuit, d'intégration de systèmes avec [Apache Camel](https://camel.apache.org/).

## Les leçons

Il y a 31 leçons, de la 00 à la 30, toutes sous Docker Compose, plus une annexe facultative en lecture seule. Elles se suivent dans
l'ordre : chacune part de ce que la précédente a laissé. Commencez par la [00](00-ambiente.md), qui démarre l'environnement.

### Partie 1 — Qu'elle arrive

| # | Leçon |
|---|---|
| 00 | [Environnement prêt](00-ambiente.md) |
| 01 | [Ma première route tourne](01-mi-primera-ruta.md) |
| 02 | [La commande arrive à l'API](02-la-orden-llega-a-la-api.md) |
| 03 | [Traduire la commande](03-traducir-la-orden.md) |
| 04 | [Vers l'ERP](04-al-erp.md) |
| 05 | [Tout ou rien](05-todo-o-nada.md) |
| 06 | [Construire l'EDI](06-armar-el-edi.md) |
| 07 | [Vers le partenaire](07-al-socio.md) |
| 08 | [Le routeur](08-el-router.md) |
| 09 | [Le fil entre les services](09-el-hilo-entre-servicios.md) |
| 10 | [Tous les canaux](10-todos-los-canales.md) |
| 11 | [Tout ne va pas à tout le monde](11-no-todo-va-a-todos.md) |
| 12 | [Grandir en répliques](12-crecer-en-replicas.md) |

### Partie 2 — Qu'elle avance

| # | Leçon |
|---|---|
| 13 | [L'expédition arrive](13-llega-el-embarque.md) |
| 14 | [Un fichier, plusieurs commandes](14-un-archivo-varias-ordenes.md) |
| 15 | [L'état vit hors du processus](15-el-estado-vive-fuera-del-proceso.md) |
| 16 | [La livraison arrive](16-llega-la-entrega.md) |
| 17 | [La livraison est arrivée avant le numéro de suivi](17-la-entrega-llego-antes-que-la-guia.md) |

### Partie 3 — Que rien n'échoue

| # | Leçon |
|---|---|
| 18 | [Quand deux messages sont-ils le même ?](18-cuando-dos-mensajes-son-el-mismo.md) |
| 19 | [La destination ne répond pas](19-el-destino-no-contesta.md) |
| 20 | [Accepté, rejeté ou on ne sait pas](20-aceptado-rechazado-o-no-se-sabe.md) |
| 21 | [Envoyé n'est pas confirmé](21-enviado-no-es-confirmado.md) |
| 22 | [Faire tomber un canal](22-tirar-un-canal.md) |
| 23 | [Retraiter ce qui a échoué](23-reprocesar-lo-que-fallo.md) |
| 24 | [Ne pas faire tomber la destination](24-no-tumbar-al-destino.md) |
| 25 | [Attendre les canaux](25-esperar-a-los-canales.md) |
| 26 | [Suivre une commande](26-seguir-una-orden.md) |
| 27 | [Ce que la piste d'audit ne doit pas montrer](27-lo-que-el-rastro-no-debe-mostrar.md) |

### Partie 4 — Que ça passe à l'échelle

| # | Leçon |
|---|---|
| 28 | [Un topic, un groupe par intégration](28-un-topico-un-grupo-por-integracion.md) |
| 29 | [Ne pas perdre en consommant](29-no-perder-al-consumir.md) |
| 30 | [Ne pas perdre en publiant](30-no-perder-al-publicar.md) |

### Annexe facultative

Le cours se termine à la leçon 30. L'annexe porte une intégration sur Kubernetes et la compare en JVM et en natif ; elle demande une
machine avec plus de mémoire et de disque, et le dit dès le début.

| # | Leçon |
|---|---|
| 31 | [Du laboratoire au projet, sur Kubernetes](31-de-laboratorio-a-kubernetes.md) |
| 32 | [JVM contre natif, mesuré](32-jvm-contra-nativo.md) |

Les décisions de conception, avec leur raison d'être, se trouvent dans [`decisiones/`](../decisiones/README.md).
