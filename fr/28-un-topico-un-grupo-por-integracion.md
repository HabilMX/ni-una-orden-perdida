# Leçon 28 — Un topic, un groupe par intégration

> **Ce que tu vas obtenir :** que le routeur cesse d'appeler chaque canal et d'attendre sa réponse. À la place, il publie chaque
> événement du cycle de vie de la commande dans un **topic** Kafka, et chaque intégration le lit à son rythme. Si le partenaire est arrêté une
> heure, l'API et l'ERP continuent, et à son retour le partenaire rattrape son retard tout seul : personne ne lui renvoie rien.

**Avant de commencer, il te faut :** les leçons 12, 17 et 27 terminées et l'environnement de la 00 allumé. Et Kafka, qui,
à partir d'aujourd'hui, s'allume à part (une fois) :

```bash
docker compose -f infra/compose.yaml --profile kafka up -d --wait
```

## La nouveauté du jour, en une phrase

Jusqu'à la 27, le routeur parle à chaque canal par HTTP. Si int-edi est arrêté, le routeur retente, ouvre le
coupe-circuit et finit par laisser l'opération en rapprochement : quelqu'un doit faire un rerun à la main quand int-edi
revient. Le routeur porte la charge de ce que chaque canal soit en marche. Aujourd'hui, cela change : le routeur **publie** un événement par événement du cycle de vie
dans le topic `ordenes`, et chaque intégration le **lit** quand elle le peut. Deux idées font que cela fonctionne : la **clé** de
l'événement et le **groupe** de chaque intégration.

**L'image :** le journal du quartier. Le livreur dépose un exemplaire dans chaque maison, pas un seul pour toute la rue.
Si, dans une maison, on est parti en vacances, les journaux s'entassent devant la porte et, au retour, on les lit dans l'ordre ; les
autres maisons n'ont attendu personne. Et dans une maison de trois frères et sœurs, il arrive **un** journal : le lit celui qui l'attrape
le premier, pas les trois.

| Dans l'image | Dans le cours |
|---|---|
| Le journal de chaque jour | Un événement : un événement du cycle de vie d'une commande |
| Chaque maison | Un groupe : `int-rest`, `int-bd`, `int-edi` |
| Les frères et sœurs d'une maison | Les 3 répliques d'int-bd, qui partagent leur groupe |
| Les journaux entassés devant la porte | Ce que le groupe n'a pas encore lu : Kafka le lui garde |

---

## 1. Prépare ton dossier

```bash
cd lecciones/28-un-topico-un-grupo-por-integracion
cp -R inicio mio
```

À partir d'aujourd'hui, il y a des pièces nouvelles. Lis-les avant d'écrire :

| Fichier | Ce qu'il fait | L'écris-tu ? |
|---|---|---|
| `orden-router/reparto.camel.yaml` | Il n'appelle plus les canaux : il publie un événement dans le topic `ordenes` | **Oui, le TODO 1** |
| `int-rest/kafka.camel.yaml`, `int-bd/…`, `int-edi/…` | Le **pont** de chaque intégration : il lit le topic et remet l'événement à sa route habituelle (`/integrar`, `/embarque`, `/entrega`) | **Oui, le TODO 2** |
| `orden-router/desenlaces.camel.yaml` | Ce qu'a répondu chaque canal revient par un autre topic, `desenlaces`, et seul le routeur le lit | Non, lis-le |

Ce que chaque canal fait avec sa destination **n'a pas changé** : seule a changé la façon dont le travail lui arrive. Le routeur reste le seul
à écrire dans la base d'état, et il continue de décider quels canaux sont concernés par chaque commande (la 11) : l'événement porte
l'opération et la tentative de chaque canal concerné, et un pont qui n'y trouve pas les siennes le laisse passer.

Deux choses qui se remarquent dès aujourd'hui :

- **Le coupe-circuit du routeur (la 22) n'est plus là.** Son travail était de ne pas insister auprès d'un canal en panne ; maintenant, le routeur
  ne parle plus au canal : il n'y a donc plus personne auprès de qui insister. Kafka garde l'événement jusqu'à ce que le canal le lise.
- **Le rerun (la 23) répond `202` « `enviado` »** (envoyé) dès que Kafka a reçu l'événement, pas quand le canal a terminé.

Ouvre `infra/compose.yaml` et cherche `KAFKA_NUM_PARTITIONS: 3`, et dans le `compose.yaml` de la leçon, `replicas: 3`.

## 2. Prédis

Réponds sur papier :

1. Les trois intégrations lisent avec le **même** groupe, `integraciones`. Combien d'entre elles reçoivent la 4001 ?
2. Si int-edi est arrêté et que la 4002 et la 4003 arrivent, que se passe-t-il pour l'API et l'ERP ? Et qu'arrive-t-il aux 850 du
   partenaire quand int-edi revient ?
3. Le topic a 3 partitions et int-bd tourne en 3 répliques. Si un événement **ne porte pas de clé**, les événements du cycle de vie de la
   même commande peuvent-ils tomber dans des partitions différentes ? Pourquoi cela aurait-il de l'importance ?

Regarde l'état actuel :

```bash
./verifica inicio
```

La première coche de livraison sort en rouge : la 4001 arrive à **un** de ses trois canaux, et les deux autres restent en
`enviada`. Avec un seul groupe, Kafka traite int-rest, int-bd et int-edi comme trois répliques de la même chose, et donne chaque
événement à une seule. Et la dernière coche aussi : aucun événement ne porte la commande comme clé.

## 3. Pose la clé et sépare les groupes

### TODO 1 : la clé de l'événement

Dans `mio/orden-router/reparto.camel.yaml`, cherche le TODO 1, juste avant le `to: kafka:ordenes`. Le composant kafka
de Camel prend la clé dans l'en-tête `CamelKafkaKey`. La commande est déjà dans la variable `orden` :

```yaml
- setHeader:
    name: CamelKafkaKey
    expression:
      simple:
        expression: "${variable.orden}"
```

Kafka envoie dans la **même partition** tout ce qui porte la même clé, et à l'intérieur d'une partition l'ordre est
respecté. Ainsi, le creada, l'embarcada et l'entregada de la 4001 sont lus dans l'ordre où ils ont été publiés. Une
partition est lue par une seule réplique de chaque groupe à la fois : si le groupe se rééquilibre (une réplique tombe ou une autre arrive),
une autre réplique peut la reprendre, mais dans le même ordre. Sans clé, chaque événement tombe n'importe où, et deux répliques différentes pourraient traiter deux événements
de la même commande, chacune à son rythme.

### TODO 2 : un groupe par intégration

Dans les trois `kafka.camel.yaml`, remplace `groupId: integraciones` par le nom de l'intégration :

| Fichier | `groupId` |
|---|---|
| `mio/int-rest/kafka.camel.yaml` | `int-rest` |
| `mio/int-bd/kafka.camel.yaml` | `int-bd` |
| `mio/int-edi/kafka.camel.yaml` | `int-edi` |

Avec cela, tu as les deux façons de distribuer que donne Kafka, en même temps :

| | Entre groupes différents | Entre répliques du même groupe |
|---|---|---|
| Qui reçoit un événement | **Tous** les groupes, chacun sa copie | **Une** seule réplique |
| Le patron s'appelle | *Publish-Subscribe* | *Competing Consumers* |
| Dans le cours | L'API, l'ERP et le partenaire reçoivent chaque commande | Les 3 partitions sont attribuées aux 3 répliques d'int-bd |

Chaque groupe tient **son propre compte** de l'endroit jusqu'où il a lu : l'*offset*. C'est pour cela qu'un groupe arrêté n'arrête pas les
autres, et qu'à son retour il reprend là où il s'était arrêté.

## 4. Casses-en une exprès

Avec ta version terminée, retire le `setHeader` de `CamelKafkaKey` et lance `./verifica`.

Avant cela, prédis : les trois intégrations ont leur groupe. Les commandes arrivent-elles ?

Elles arrivent, et presque toutes les coches sortent vertes. Seule la dernière sort en rouge : les événements ne portent plus la commande comme
clé. Avec une commande à la fois, on ne remarque rien, et c'est ce qui rend cette erreur traîtresse : elle apparaît le jour où deux
événements de la même commande arrivent ensemble et où deux répliques différentes d'int-bd les traitent. La machine à états de la
17 gare celui qui arrive trop tôt, mais la clé évite que cela se produise. **Que cela fonctionne aujourd'hui ne prouve pas que l'ordre
soit garanti.** Remets la clé.

## 5. Vérifie

```bash
./verifica
```

`verifica` recrée un Kafka vide, envoie la 4001 avec tout en marche, **arrête int-edi**, envoie la 4002 et la 4003, contrôle
que l'API et l'ERP ont continué et que le partenaire n'a pas ses 850, rallume int-edi et attend qu'il rattrape son
retard. À la fin, il lit le topic en entier avec un lecteur à part pour contrôler la clé de chaque événement. Elle est terminée quand
tu vois les sept coches vertes :

```
✅ Arrancaron el router (que publica en Kafka), los tres canales con su puente y el sondeo
✅ La 4001 llegó por Kafka a sus tres canales: la API, el ERP y el socio
✅ Con int-edi detenido, la API y el ERP recibieron la 4002 y la 4003 (API 2 de 2, ERP 2 de 2)
✅ El socio todavía no tiene sus 850, y Kafka se los guarda: el grupo int-edi va 2 eventos atrás
✅ int-edi volvió y se puso al día solo: 2 archivos 850 en el socio, y el router no reenvió nada (2 intentos para 2 operaciones)
✅ Los tres grupos (int-rest, int-bd, int-edi) quedaron al día: 0 eventos por leer
✅ Los 3 eventos del tópico ordenes llevan la orden como llave
```

Lis la cinquième : « 2 intentos para 2 operaciones ». Jusqu'à la 27, un canal en panne finissait en rapprochement, avec un rerun à la
main. Aujourd'hui, personne n'a rien fait : int-edi a lu ce que Kafka lui avait gardé.

Regarde toi-même où en est la lecture de chaque groupe, avec les services en marche :

```bash
docker compose -f ../../infra/compose.yaml exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups
```

La colonne `LAG` est ce qu'il reste à lire à chaque groupe dans chaque partition.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et `diff -r mio solucion` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `Kafka no levantó` | Allume-le à la main avec la commande de « Avant de commencer » et réessaie |
| `A la base de estado le faltan tablas de lecciones anteriores` | Ta base date d'avant : recrée-la avec la commande que donne le message (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` avec l'un d'eux en `enviada` | Ce canal n'a pas son propre groupe : vérifie le `groupId` de son `kafka.camel.yaml` |
| `Con int-edi detenido, la API y el ERP debían seguir` | Vérifie le `groupId` d'int-rest et d'int-bd : s'ils partagent un groupe avec un autre, ils se prennent les événements entre eux |
| `el grupo int-edi va -1 atrás` | Le groupe `int-edi` n'existe pas : int-edi lit avec un autre nom de groupe |
| `De … eventos del tópico ordenes, … llevan la orden como llave` | Il manque le TODO 1, ou le `setHeader` s'est retrouvé après le `to: kafka:ordenes` |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**Publier n'est pas appeler.** Celui qui appelle a besoin que l'autre soit en marche ; celui qui publie a seulement besoin que le topic
soit en marche. Le routeur a cessé de porter la disponibilité de chaque canal, et c'est ce qu'on appelle **découpler**.

**Le groupe décide qui reçoit.** Le même topic sert à répartir du travail entre des répliques (un groupe) et à prévenir
plusieurs systèmes (plusieurs groupes). Se tromper de groupe est l'erreur la plus courante quand on débute avec Kafka, et elle ne produit pas d'erreur : les
événements arrivent simplement à un autre.

**La clé décide de l'ordre.** Kafka ne promet pas l'ordre sur tout le topic, seulement à l'intérieur d'une partition. Si quelque chose doit
être lu dans l'ordre, cela doit porter la même clé.

**Ce qui n'a pas été lu est conservé.** Un consommateur arrêté ne perd rien tant que Kafka conserve l'événement : à son
retour, il reprend à partir de son offset. Cela change ce que signifie « le partenaire a été en panne une heure ».

## Exercice : le journal du quartier

1. Avec tes services en marche, arrête int-bd (`DONDE=mio docker compose -f compose.yaml stop int-bd`), envoie une commande
   et lance la commande `kafka-consumer-groups.sh`. Quel groupe a du `LAG` ? Rallume-le et relance-la.
2. Sur papier : si demain le partenaire demande à recevoir aussi les avis de livraison, et que quelqu'un des finances veut un nouveau
   système qui lise **toutes** les commandes, combien de groupes y a-t-il ? Que changes-tu dans le routeur pour le nouveau système ?
3. Les 3 partitions sont attribuées aux 3 répliques d'int-bd. Que se passerait-il si tu faisais tourner 5 répliques ? (Indice : une partition est
   lue par une seule réplique de chaque groupe.)

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Un événement par événement du cycle de vie dans un topic, avec la commande comme clé, et un groupe de consommateurs par intégration](../decisiones/32-un-topico-un-grupo-por-integracion.md)

## Révision

1. Qu'est-ce qu'un topic, et qu'est-ce qui change pour le routeur quand il publie au lieu d'appeler ?
2. Que fait la clé d'un événement ? Pourquoi la clé est-elle la commande ?
3. Quelle différence y a-t-il entre deux répliques du même groupe et deux groupes différents ?
4. Qu'est-ce que l'offset, et pourquoi un groupe arrêté n'arrête-t-il pas les autres ?
5. **La question de clôture :** le partenaire a été arrêté une heure. Explique, étape par étape, pourquoi aujourd'hui il n'a pas fallu de
   rerun alors que jusqu'à la 27 il en fallait un.

**Suite :** dans la leçon 29, le problème passe de l'autre côté : que se passe-t-il si int-bd lit un événement et n'a pas le temps de
laisser son issue ? Kafka tient-il l'événement pour lu ?
