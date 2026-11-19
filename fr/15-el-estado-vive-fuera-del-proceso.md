# Leçon 15 — L'état vit hors du processus

> **Ce que tu vas obtenir :** que le routeur **enregistre** chaque commande dans sa propre base avant de répondre à la boutique,
> avec une ligne par canal et une trace de chaque tentative, et que, grâce à cela, une commande ne se perde pas même si une destination
> est en panne.

**Avant de commencer, il te faut :** la leçon 14 terminée, l'environnement de la 00 allumé (la base d'état,
`estado`, est celle que tu as démarrée dès le premier jour et qui ne servait pas jusqu'à aujourd'hui) et ta réponse à la question de
clôture de la 12.

## La nouveauté du jour, en une phrase

Jusqu'à la 14, le routeur recevait la commande, appelait les trois canaux, attendait et répondait à la boutique ce
qu'avait dit chacun. Si le routeur tombait au milieu, personne ne savait quelles destinations avaient déjà la commande. À partir d'aujourd'hui, le routeur
**note d'abord et distribue ensuite** : il enregistre la commande dans PostgreSQL, répond à la boutique **202 « `recibida` »** (reçue) et,
une fois la boutique rassurée, part distribuer.

**L'image :** le guichet d'un transporteur. On prend ton colis, on l'enregistre dans le système et on te remet ton
reçu. Tu ne restes pas au comptoir jusqu'à ce qu'il arrive à destination. Le reçu vaut parce que **le colis est déjà
enregistré**, pas parce que l'employé se souvient de toi. Si l'équipe change, celui qui arrive lit le registre.

Reviens à ta réponse de la 12 : « si le routeur avait des répliques, où vivrait ce qui concerne chaque commande ? ». Aujourd'hui, nous y
répondons : **hors du processus, dans une base**. Ainsi, n'importe quelle réplique du routeur peut le lire, et si l'une tombe, ce qui est
noté reste là.

---

## 1. Prépare ton dossier

```bash
cd lecciones/15-el-estado-vive-fuera-del-proceso
cp -R inicio mio
```

Le routeur a maintenant trois fichiers. Ouvre-les dans cet ordre :

| Fichier | Ce qu'il fait | L'écris-tu ? |
|---|---|---|
| `orden-router.camel.yaml` | Reçoit la commande ou l'expédition, la fait enregistrer, répond 202 et met la distribution en file | Non, lis-le |
| `estado.camel.yaml` | Enregistre dans la base. C'est ici que se trouve `guardar-orden`, ton travail | **Oui** |
| `reparto.camel.yaml` | Lit dans la base ce qui est en attente, note la tentative, appelle le canal et note comment cela s'est passé | Non, lis-le |

Et ouvre le schéma de la base, `infra/postgres/01-estado.sql`. Aujourd'hui, trois tables comptent :

| Table | Une ligne par… | L'important |
|---|---|---|
| `orden` | commande | À quel événement du cycle de vie elle en est (`creada`, `embarcada`, `entregada`) et ses `datos`, la commande telle qu'elle est arrivée |
| `operacion` | commande, événement et canal | Ce qu'il faut faire dans chaque destination et comment cela s'est terminé (`estado` : `pendiente`, `aceptada`, `rechazada`, `no_se_sabe`) |
| `intento` | tentative faite sur une opération | L'empreinte de ce qui est parti, la version du mapping et l'issue |

Une commande de franchise, à sa création, laisse **une** ligne dans `orden` et **trois** dans `operacion` : `rest`, `bd` et `edi`.

## 2. Prédis

Réponds sur papier :

1. Aujourd'hui, le routeur répond 202 alors que `guardar-orden` n'enregistre rien. Qu'arrive-t-il à la commande après le
   202, selon toi ? (Indice : la distribution lit dans la base ce qui est en attente.)
2. Si l'ERP est éteint quand la commande arrive, que devrait répondre le routeur à la boutique ? Et que devrait-il
   rester dans la base ?

Regarde l'état actuel :

```bash
./verifica inicio
```

La boutique reçoit son 202, et ensuite il ne se passe rien : la base n'a pas la commande et aucune destination ne la reçoit. **Le routeur
a promis quelque chose qu'il n'a pas tenu.** C'est la pire erreur de ce cours, parce que la boutique croit que tout s'est bien passé.

## 3. Enregistre avant de répondre

Ouvre `mio/orden-router/estado.camel.yaml` et cherche `guardar-orden`. Il a trois TODO. Ton modèle est juste en dessous :
`guardar-momento` fait la même chose pour l'expédition. Lis-le en entier avant d'écrire.

| TODO | Ce que tu écris | Pour quoi faire |
|---|---|---|
| 1 | `transacted` avec `ref: todoONada` | Que la commande et ses opérations soient enregistrées **toutes ou aucune** (la même idée que dans la 05, maintenant dans l'état) |
| 2 | Un `to` avec `sql:INSERT INTO orden (orden, momento, datos) VALUES (…)` | La ligne de la commande : son numéro, l'événement `'creada'` et la commande complète en `jsonb` |
| 3 | Un `split` sur `${variable.canales.split(',')}` avec un `INSERT INTO operacion …` à l'intérieur | Une opération par canal, en `'pendiente'`, avec son fil. Celle du partenaire, avec son numéro de contrôle |

Trois indices :

- Les variables dont tu as besoin existent déjà quand cette route est appelée : `orden`, `canales` et `correlacion`. Le corps est
  la commande telle que la boutique l'a envoyée.
- Pour enregistrer le corps en `jsonb` : `CAST(:#${body} AS jsonb)`. `:#${…}` est la façon dont le composant `sql` passe une
  valeur en paramètre, sans la coller au texte du SQL.
- L'`INSERT INTO operacion` de `guardar-momento` sert presque tel quel : ce qui change, c'est l'événement.

Regarde la ligne du numéro de contrôle :
`CASE WHEN :#${body} = 'edi' THEN CAST(nextval('numero_control_seq') AS text) END`. Seule l'opération du partenaire
demande un numéro à la séquence. **À partir d'aujourd'hui, le numéro de contrôle du X12 est attribué par le routeur**, il ne vient plus du numéro de
commande comme dans la 06 et la 13.

## 4. Casses-en une exprès

Avec ton `guardar-orden` terminé, éteins la base d'état et envoie une commande au routeur :

```bash
DONDE=mio docker compose -f compose.yaml up -d
docker compose -f ../../infra/compose.yaml stop estado
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4003.json
```

Avant de l'envoyer, prédis : le routeur répond-il 202 ?

Il ne le fait pas : il répond par une erreur. Sans base, il ne peut pas enregistrer, et **sans enregistrer, il ne promet pas**. La boutique voit l'erreur et sait
qu'elle doit réessayer. C'est exactement le contraire de `inicio/`, qui répondait 202 sans enregistrer. Rallume
tout :

```bash
docker compose -f ../../infra/compose.yaml start estado
DONDE=mio docker compose -f compose.yaml down
```

## 5. Vérifie

```bash
./verifica
```

`verifica` envoie la 4001 et son expédition, contrôle la base et les destinations et, à la fin, éteint l'ERP et envoie la 4002. Elle est
terminée quand tu vois les douze coches vertes :

```
✅ Arrancaron el router (con su base de estado), los tres canales y el sondeo
✅ La tienda mandó la 4001 y el router contestó 202 «recibida»
✅ Estado: una fila de la orden 4001, con sus datos
✅ Estado: tres operaciones de la 4001, una por canal, y las tres aceptadas
✅ Estado: cada operación tiene su intento, con huella sha256, versión del mapeo y desenlace
✅ Destinos: la API tiene la 4001 CREADO y el ERP REGISTRADO
✅ Socio: el 850 de la 4001 lleva en el ISA el número de control de la base (…)
✅ Estado: el embarque de la 4001 tiene sus tres operaciones aceptadas y la orden va en «embarcada»
✅ Socio: el 856 lleva su propio número de control (…), distinto del 850 (…)
✅ Destinos: la API tiene la 4001 EMBARCADO
✅ Con el ERP caído, la tienda recibió su 202 por la 4002
✅ Estado: la operación del ERP quedó sin aceptar y está guardada (…); las otras dos siguieron
```

Les numéros de contrôle changent à chaque exécution : c'est la séquence qui les donne.

Regarde les deux dernières. Avec l'ERP en panne, la boutique a **bien** reçu son 202, parce que le routeur a enregistré, et ce qui n'est pas parti
est resté noté : l'opération `bd` de la 4002 est en `no_se_sabe` ou `rechazada`, alors que l'API et le partenaire l'ont
déjà. Dans la 14, cela se perdait dans un journal. Aujourd'hui, c'est dans une ligne que l'on peut consulter.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/estado.camel.yaml solucion/orden-router/estado.camel.yaml` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `no está la fila de la orden 4001` | Il manque l'INSERT de `orden`, ou il ne porte pas `dataSource: "#estado"` |
| `esperaba «bd=aceptada edi=aceptada rest=aceptada» y hay «nada»` | Il manque le `split` des opérations, ou les INSERT sont restés en dehors |
| `column "datos" is of type jsonb but expression is of type…` | Tu as oublié le `CAST(:#${body} AS jsonb)` |
| `el 850 … no lleva el número de control de la base` | L'opération `edi` a été enregistrée sans `numero_control` : revois le `CASE WHEN` |
| `El puerto 8080 está ocupado` | Les services de l'étape 4 sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**Répondre « reçu » est une promesse, et elle n'est tenue que si c'est déjà écrit.** C'est pour cela que le routeur enregistre d'abord. C'est
ce que fait une banque avec un virement : elle te donne le numéro de référence quand il est déjà dans son livre, pas quand il est arrivé.

**L'état hors du processus est ce qui permet au routeur de grandir.** Un processus qui se souvient dans sa mémoire ne peut pas être
répliqué : chaque réplique se souviendrait de choses différentes, et au redémarrage elle oublierait. Avec la base, n'importe quelle réplique lit la
même chose.

**Séparer « j'ai reçu » de « j'ai livré » change la conversation.** La boutique n'attend plus que les trois destinations aient répondu.
En échange, quelqu'un doit regarder ce qui est resté en attente : c'est ce que construit la partie 3.

## Exercice : la commande dans trois tables

Avec ta version terminée, lance `./verifica` et entre dans la base :

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. Sur papier, **dessine** la commande 4001 dans les trois tables : une case pour sa ligne de `orden`, une pour chaque `operacion`
   (création et expédition) et une pour chaque `intento`, avec des flèches indiquant laquelle pointe vers laquelle.
2. Écris la requête qui te dit, pour la 4002, dans quel état se trouve chaque canal. Commence par
   `SELECT canal, estado FROM operacion WHERE orden = '4002'` et ajoutes-y l'événement.
3. Quel numéro de contrôle porte le 850 de la 4001, et lequel portait-il dans la 06 ? D'où vient chacun, et pourquoi un numéro
   donné par la base ne se répète-t-il pas, même si deux commandes arrivent en même temps ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Enregistrer avant de répondre](../decisiones/18-guardar-antes-de-contestar.md)
- [Le numéro de contrôle du X12 est attribué par le routeur](../decisiones/19-el-numero-de-control-lo-asigna-el-router.md)

## Révision

1. Que conserve chacune des trois tables, `orden`, `operacion` et `intento` ?
2. Pourquoi le routeur enregistre-t-il la commande **avant** de répondre 202, et non après ?
3. À quoi sert la transaction dans `guardar-orden` ? Que se passerait-il sans elle si le second INSERT échouait ?
4. Avec l'ERP en panne, qu'a reçu la boutique et qu'est-il resté dans la base ? Qu'arrivait-il à cette même commande dans la 14 ?
5. **La question de clôture :** à partir d'aujourd'hui, le numéro de contrôle du X12 est attribué par le routeur. D'où venait-il dans la 06,
   et qu'est-ce qui a changé ? Compare avec ta réponse de la 12 sur l'endroit où vit ce qui concerne chaque commande.

**Suite :** dans la leçon 16 arrive le troisième événement : le transporteur signale qu'il a livré. Personne ne le lui demande : il appelle
de lui-même, par un webhook. Que doit-il se passer dans l'état et sur le SFTP ?
