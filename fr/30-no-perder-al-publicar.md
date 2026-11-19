# Leçon 30 — Ne pas perdre en publiant

> **Ce que tu vas obtenir :** refermer la dernière faille du cours. Si Kafka est en panne juste au moment où une commande arrive, la
> commande est enregistrée, la boutique reçoit son `202` et, quand Kafka revient, la commande part toute seule vers ses trois canaux, sans que
> personne fasse un rerun.

**Avant de commencer, il te faut :** les leçons 17 et 29 terminées, l'environnement de la 00 allumé et Kafka en marche
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## La nouveauté du jour, en une phrase

Depuis la 28, le routeur fait deux choses à la suite : il **enregistre** la commande dans la base d'état et publie **ensuite**
l'événement dans Kafka. Ce sont deux systèmes différents, et aucune transaction n'englobe les deux. Si Kafka est en panne à cet
instant, la commande est enregistrée et la boutique a déjà son `202`, mais l'événement n'est pas parti, et personne ne le
retente. L'inverse ne convient pas non plus : publier avant d'enregistrer laisse un événement pour une commande qui, si la base échoue, n'existe
pas. Aujourd'hui, le routeur enregistre **ce qu'il faut publier** dans la même transaction que la commande, dans une table appelée
**outbox**, et une scrutation le publie quand Kafka peut le recevoir. Le patron s'appelle *Transactional Outbox*.

**L'image :** la corbeille « à envoyer » d'un bureau. La personne qui reçoit le client ne court pas chez le transporteur
avec chaque enveloppe : elle remplit le formulaire et, **au même instant**, dépose l'enveloppe dans la corbeille. Le coursier passe de temps
en temps et emporte ce qu'il y a. Si un jour le transporteur est fermé, les enveloppes restent dans la corbeille, pas dans la
poche de quelqu'un, et partent quand il rouvre.

```
antes:  guardar (transacción) → 202 → publicar en Kafka           ← si Kafka está caído aquí, se pierde
hoy:    guardar orden + operaciones + outbox (UNA transacción) → 202
        cada 1 s: outbox pendiente → publicar → ¿Kafka lo recibió? sí → «publicado» · no → espera la siguiente vuelta
```

---

## 1. Prépare ton dossier

```bash
cd lecciones/30-no-perder-al-publicar
cp -R inicio mio
```

Ouvre `infra/postgres/01-estado.sql` et cherche `CREATE TABLE outbox` : chaque ligne pointe vers une `operacion` et a une
colonne `publicado`, vide tant que Kafka n'a pas reçu l'événement.

La scrutation est déjà faite : c'est la route `sondeo-outbox` de `mio/orden-router/reparto.camel.yaml`. Lis-la. Chaque seconde,
elle prend ce qui est en attente **dans l'ordre où c'est entré** et, si quelque chose ne part pas, ce qui suit attend le tour suivant
(`stopOnException`) : ainsi, ce qui concerne une même commande ne part pas dans le désordre. Le balayage de la 17 dépose déjà dans l'outbox ce qu'il
libère.

Ton travail se place dans deux fichiers :

| TODO | Fichier | Ce que tu fais |
|---|---|---|
| 1 | `mio/orden-router/estado.camel.yaml` | À l'enregistrement d'une nouvelle commande, déposer dans l'outbox une ligne pour chaque opération (événement `creada`) |
| 2 | Le même | À l'enregistrement d'un nouvel événement du cycle de vie (expédition, livraison), la même chose pour les opérations nées `pendiente` |
| 3 | `mio/orden-router/orden-router.camel.yaml` | Retirer l'envoi direct vers `seda:repartir` : à partir d'aujourd'hui, seul l'outbox publie |

## 2. Prédis

Réponds sur papier :

1. Avec la version de la 29, Kafka est en panne et la 4002 et la 4003 arrivent. La boutique reçoit-elle son `202` ? Combien de lignes
   y a-t-il dans l'outbox ? Qu'arrive-t-il aux commandes quand Kafka revient ?
2. Pourquoi la ligne de l'outbox doit-elle être écrite dans la **même** transaction que l'opération, et non juste
   après ?
3. Si tu laisses l'envoi direct en plus de l'outbox, qu'arrive-t-il à chaque événement ? L'API le créerait-elle deux fois ?

Regarde l'état actuel :

```bash
./verifica inicio
```

Avec Kafka en panne, le routeur enregistre les deux commandes et répond `202`, mais l'outbox reste **vide**. Quand Kafka
revient, les 6 opérations restent en attente : personne ne les renvoie.

## 3. La corbeille « à envoyer »

### TODO 1 : la nouvelle commande

Dans `mio/orden-router/estado.camel.yaml`, cherche le TODO 1. Il se trouve dans la transaction qui enregistre la commande et ses
opérations. Au même endroit, un `INSERT … SELECT` dépose une ligne pour chaque opération qui vient d'être enregistrée :

```yaml
- to:
    uri: "sql:INSERT INTO outbox (operacion) SELECT id FROM operacion WHERE orden = :#${variable.orden} AND momento = 'creada' ORDER BY id"
    parameters:
      dataSource: "#estado"
```

Le `ORDER BY id` fait entrer les lignes dans l'ordre où les opérations sont nées, et la scrutation publie dans cet
ordre.

### TODO 2 : un nouvel événement

Plus bas, dans la même transaction qui enregistre l'expédition ou la livraison, le TODO 2. C'est le même `INSERT`, avec deux
différences :

| | TODO 1 | TODO 2 |
|---|---|---|
| Quel événement | `'creada'` | `:#${variable.momento}` |
| Quelles opérations | Toutes | Seulement celles qui sont nées `pendiente` : `AND estado = 'pendiente'` |

Pourquoi seulement celles en attente ? Parce que les `estacionada` (la 17) ne peuvent pas encore partir. Celles-là, c'est le
balayage qui les dépose dans l'outbox, quand il les libère.

### TODO 3 : un seul chemin

Dans `mio/orden-router/orden-router.camel.yaml`, dans la route `contestar-y-repartir`, retire l'étape `to: seda:repartir`.
Si tu la laisses, chaque événement part par **deux** chemins : le direct et l'outbox. L'API le reconnaîtrait à sa clé (la 18),
mais chaque canal recevrait deux événements et le routeur noterait deux tentatives. Un seul chemin est plus facile à expliquer et à
tracer.

## 4. Casses-en une exprès

Avec ta version terminée, efface l'`INSERT` du TODO 1 et lance `./verifica`.

Avant cela, prédis : Kafka est en marche au début. La 4001 arrive-t-elle ?

Non. Le verifica s'arrête à la deuxième coche : la 4001 n'arrive à aucun de ses canaux. Tu as déjà retiré l'envoi
direct (TODO 3) : l'outbox est donc **le seul chemin**, et une commande qui ne laisse pas sa ligne n'est publiée par personne. Avant
l'outbox, « Kafka est en marche » suffisait ; maintenant, ce qui commande, c'est la ligne enregistrée. Remets-le.

## 5. Vérifie

```bash
./verifica
```

`verifica` envoie la 4001 avec tout en marche, **arrête Kafka**, envoie la 4002 et la 4003, contrôle l'outbox et l'API
pendant quelques secondes, redémarre Kafka et attend que tout soit parti. Elle est terminée quand tu vois les huit coches
vertes :

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Con Kafka caído, el router guardó la 4002 y la 4003 y contestó 202 a las dos
✅ En el outbox esperan 6 filas sin publicar, una por operación de la 4002 y la 4003
✅ Control: mientras Kafka está caído, la API no tiene ninguna de las dos
✅ Kafka volvió y lo guardado salió solo: la API, el ERP y el socio tienen la 4002 y la 4003
✅ En la API se creó cada una UNA vez (2 creaciones para 2 órdenes)
✅ El outbox quedó sin pendientes: todo lo que se guardó, salió
```

La cinquième est un **contrôle** : si, avec Kafka en panne, l'API avait déjà les commandes, la sixième coche ne prouverait pas que
l'outbox les a fait partir.

Regarde toi-même l'outbox, avec les services en marche :

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT x.id, p.orden, p.canal, x.publicado FROM outbox x JOIN operacion p ON p.id = x.operacion ORDER BY x.id"
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et `diff -r mio solucion` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `A la base de estado le falta la tabla outbox` | Ta base date d'avant la 30 : recrée-la avec la commande que donne le message (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` | Il manque le TODO 1 : avec le TODO 3 fait, sans ligne dans l'outbox, rien n'est publié |
| `En el outbox esperaba 6 filas sin publicar … y hay 0` | L'`INSERT` du TODO 1 ne s'exécute pas : vérifie qu'il est dans la transaction, après l'enregistrement des opérations |
| `Kafka volvió y lo guardado no salió` | Regarde quel canal est resté dans quel état (le message le dit) et vérifie la route `sondeo-outbox` dans le journal du routeur |
| `En la API hubo … creaciones para 2 órdenes` | L'API en a créé une deux fois : as-tu changé la clé qu'int-rest lui envoie ? (la 18) |
| `El outbox tiene … filas sin publicar` | Quelque chose est resté bloqué : regarde le journal du routeur, sous `sondeo-outbox` |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**Deux systèmes ne partagent pas une transaction.** La base et Kafka valident chacun ce qui est à eux. Si une action doit
figurer dans les deux, on l'enregistre dans un seul (la base, avec la commande) et l'autre est alimenté à partir de là.

**Ce qui est enregistré est ce qui part.** La scrutation publie ce que dit l'outbox, en lisant la base. Si le routeur redémarre au
milieu, la ligne est toujours là.

**Publier en trop se tolère ; publier en moins, non.** Si le routeur tombe au milieu de l'envoi, ou après avoir
publié et avant d'avoir marqué `publicado`, la ligne part une seconde fois. C'est pour cela que celui qui lit doit reconnaître les doublons (la 18 et la 29). Perdre un événement, en revanche,
personne ne le remarque jusqu'à ce qu'un client réclame.

**Un seul chemin.** Garder l'envoi direct « au cas où » à côté de l'outbox duplique tout et ne répare rien :
l'outbox couvre déjà le cas où Kafka est en marche.

## Exercice : la corbeille

1. Arrête Kafka avec tes services en marche, envoie une commande et lance la requête de l'étape 5. Que dit la colonne
   `publicado` ? Redémarre Kafka et relance-la.
2. Sur papier : le routeur a enregistré la commande et sa ligne de l'outbox, la scrutation l'a publiée et le routeur est tombé avant de marquer
   `publicado`. Que se passe-t-il au retour ? Quelle pièce évite que l'API la crée deux fois ?
3. Fais la liste des façons de perdre une commande que tu as vues dans le cours (la boutique qui ne reçoit pas le `202`,
   l'ERP en panne, le 997 qui n'arrive pas, l'événement validé sans issue…). À côté de chacune, la leçon qui la
   referme.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Ce qu'il faut publier s'enregistre dans la même transaction (outbox), et une scrutation le publie](../decisiones/34-el-outbox-publica-lo-guardado.md)

## Révision

1. Quelle faille reste-t-il entre enregistrer et publier, et pourquoi ne se répare-t-elle pas en publiant d'abord ?
2. Que conserve la table `outbox`, et dans quelle transaction s'écrit-elle ?
3. Pourquoi le TODO 2 laisse-t-il de côté les opérations `estacionada` ?
4. Que se passerait-il si tu laissais l'envoi direct à côté de l'outbox ?
5. **La question de clôture :** une boutique envoie une commande alors que Kafka est en panne. Raconte, étape par étape, ce qui se passe depuis le
   `202` jusqu'à ce que le partenaire ait son 850, et dans quelles tables il en reste une trace.

**Suite :** avec cette leçon se termine le cours sous Docker Compose. Si ton équipe travaille avec Kubernetes ou veut
voir l'intégration sans conteneurs, l'annexe (leçons 31 et 32) se lit seulement et elle est facultative.

---

> **Ce que fait Hábil**
>
> À la fin de ce cours :
> - un avis en double ne duplique rien ;
> - si l'on ne sait pas si quelque chose est arrivé, cela reste en attente de rapprochement au lieu d'être tenu pour un échec ;
> - ce qui est déposé n'est pas tenu pour confirmé tant que le partenaire n'en a pas accusé réception ;
> - un canal en panne ne freine pas les autres ;
> - chaque destination ne reçoit que les appels qu'elle supporte ;
> - chaque commande peut être suivie sans montrer de données personnelles.
>
> Dans une entreprise, cela s'entend souvent ainsi : « Nous ne savons pas si cette opération est arrivée. On la retente à la main, et parfois elle est
> dupliquée ». Et il y a des pannes qui ne signalent aucune erreur : l'envoi est parti, mais la réponse n'est jamais arrivée.
>
> Chez Hábil, nous construisons sur mesure l'intégration entre votre boutique, votre ERP, vos partenaires et vos canaux.
> **[Hábil MDW](https://www.habil.mx/es/habil-mdw/?utm_source=github&utm_medium=referral&utm_campaign=2026-11_ni-una-orden-perdida_es)**
> est conçue pour fonctionner 24/7, avec une piste d'audit de chaque opération : ce qui a été envoyé à chaque système, ce qu'il a répondu, à quelle heure
> et, en cas d'échec, pourquoi. Chaque envoi reste avec sa réponse ou avec son motif d'échec, et les nouvelles tentatives sont conçues
> pour ne pas répéter l'effet. Un envoi qui s'est mal passé est retenté par votre équipe d'exploitation comme un nouvel envoi, avec
> la trace de la personne qui l'a fait et sans toucher à la base de données à la main.
>
> **[Demandez votre première conversation, sans frais.](https://wa.me/525667541323?text=Hola%2C%20vengo%20de%20GitHub%20%28Ni%20una%20orden%20perdida%29.%20Me%20interesa%20una%20primera%20conversaci%C3%B3n.%0AIndustria%3A%0APuesto%3A%0ASistemas%20principales%3A)**
