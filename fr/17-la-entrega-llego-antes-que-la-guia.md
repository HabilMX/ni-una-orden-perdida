# Leçon 17 — La livraison est arrivée avant le numéro de suivi

> **Ce que vous allez obtenir :** qu'une livraison qui arrive **avant** son expédition ne se perde pas et ne parte pas à contretemps :
> elle reste garée dans la base et sort toute seule, une seule fois, dès que l'expédition arrive.

**Avant de commencer, il vous faut :** les leçons 15 et 16 terminées et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Dans la 16, tout est arrivé dans l'ordre : la commande a été créée, puis expédiée et enfin livrée. Mais l'entrepôt et le
transporteur ne se parlent pas, et parfois l'avis de livraison **arrive avant** le fichier d'expédition. Si le
routeur le distribue tel quel, l'API et l'ERP le rejettent : on ne peut pas livrer quelque chose qui, pour eux, n'est jamais parti. Et s'il le
jette, le client n'apparaît jamais comme servi. Aujourd'hui, vous le **garez** et vous le **libérez** quand il peut enfin sortir.

**L'image :** le parking d'un hôpital. Une ambulance de transfert arrive, mais le lit du patient
n'est pas encore prêt. On ne la renvoie pas, et on n'installe pas le patient dans une chambre occupée : elle se gare. Et dès que
quelqu'un prévient « le lit est prêt », l'ambulance passe. Personne ne sort toutes les cinq minutes inspecter le parking : ce
qui fait bouger l'ambulance, c'est l'avis.

---

## 1. Préparez votre dossier

```bash
cd lecciones/17-la-entrega-llego-antes-que-la-guia
cp -R inicio mio
```

Aujourd'hui, le routeur apporte des pièces nouvelles. Presque toutes sont déjà faites : votre travail est la dernière.

| Pièce | Où | L'écrivez-vous ? |
|---|---|---|
| La machine à états : quel événement du cycle de vie a besoin duquel, dans chaque canal | `mio/orden-router/reglas/maquina-de-estados.yaml` | Non, lisez-la |
| La décision : avec la machine et ce qui est enregistré, elle dit `procesa`, `estaciona` ou `invalido` | `direct:decidir`, avec son mapping dans `mappings/estado/decision/v1/` et ses cas de test de référence | Non, lisez-la |
| Le stationnement : à l'enregistrement d'un événement, chaque opération naît `pendiente` ou `estacionada` | `guardar-momento`, dans `estado.camel.yaml` | Non, il est fourni |
| **Le balayage** : il libère ce qui est garé et peut désormais sortir | `barrido.camel.yaml` | **Oui** |

Ouvrez la machine à états. Elle se lit ainsi : dans le canal `rest`, l'événement `entregada` a besoin que `embarcada` soit déjà
**acceptée dans ce même canal**. Cela va par canal parce que chaque destination suit son propre ordre : que l'API ait déjà
l'expédition ne dit rien de l'ERP.

Ouvrez aussi un cas de test de référence de la décision, `cases/entrega-sin-embarque/` : son `entrada.json` et son `esperado.json`.
C'est la même idée que dans la 03 : la règle se teste avec des exemples, pas avec de la foi.

## 2. Prédisez

Répondez sur papier :

1. La livraison de la 4002 arrive, et son expédition pas encore. Que doit-il rester dans la base ? Que doit recevoir
   chaque destination ?
2. L'expédition arrive ensuite. Qui s'aperçoit que la livraison peut désormais sortir ? Quand ?
3. Si le balayage tourne deux fois, la livraison pourrait-elle sortir deux fois ? Qu'est-ce qui l'en empêcherait ?

Regardez l'état actuel :

```bash
./verifica inicio
```

La livraison se gare bien, mais le balayage ne libère rien : même quand l'expédition arrive, la livraison reste
garée pour toujours. La 4002 n'apparaît jamais comme ENTREGADO.

## 3. Écrivez le balayage

Ouvrez `mio/orden-router/barrido.camel.yaml`. En haut se trouve `barrido-http`, la porte pour demander un balayage à la main avec
`POST /barrido` ; elle est déjà faite. Votre travail est la route `barrido`, avec quatre TODO :

| TODO | Ce que vous écrivez | Pour quoi faire |
|---|---|---|
| 1 | Un `to` avec `sql:SELECT id, orden, momento, canal FROM operacion WHERE estado = 'estacionada' ORDER BY id` | Ramener ce qui est garé, dans l'ordre d'arrivée |
| 2 | Un `split` sur `${body}` ; à l'intérieur, quatre variables (`operacion`, `orden`, `momento`, `canal`, à partir de `${body[id]}`, `${body[orden]}`…) et un appel à `direct:decidir` | Décider de nouveau pour chacune avec ce qu'il y a aujourd'hui dans la base |
| 3 | Si `${variable.decision} == 'procesa'` : `UPDATE operacion SET estado = 'pendiente' WHERE id = … AND estado = 'estacionada'` | La passer en attente, **seulement si elle est toujours garée** |
| 4 | Si `${header.CamelSqlUpdateCount} == 1` : vers `seda:repartir` avec `pattern: InOnly` | Mettre la distribution en file, **seulement si c'est vous qui l'avez changée** |

Pour le « si… », utilisez un `filter` : il ne laisse passer le message que lorsque la condition est remplie. Le TODO 4 se place dans le
`filter` du TODO 3.

Le `AND estado = 'estacionada'` du TODO 3 est le cœur de la leçon. Imaginez deux balayages en même temps : les deux
lisent la livraison garée et les deux décident « procesa ». Le premier la change : la base répond « j'ai changé 1 ligne ». Le
second arrive trop tard et, comme elle n'est plus garée, la base répond « j'en ai changé 0 ». Seul celui qui en a changé 1 la met en file. **C'est la
base qui décide qui gagne**, pas la chance.

Et qui appelle le balayage ? Ouvrez `reparto.camel.yaml` et cherchez `direct:barrido`, presque à la fin : il est appelé **chaque fois
qu'un canal accepte un événement**, parce que c'est la seule chose qui puisse en débloquer un autre. Il n'y a pas d'horloge qui le déclenche
de temps en temps.

## 4. Cassez-en une exprès

Avec votre balayage terminé, mettez en commentaire dans `reparto.camel.yaml` l'étape qui appelle `direct:barrido`. Démarrez les
services, créez la 4002, envoyez sa livraison, puis déposez le lot de l'entrepôt, qui contient son expédition :

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4002.json
curl -s -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' -d @../../datos/webhooks/entrega-4002.json
cp ../../datos/embarques/lote-bodega-0001.json buzon/
```

Le lot contient des expéditions d'autres commandes que vous n'avez pas créées aujourd'hui ; le routeur les rejette avec un 404, et c'est normal. Attendez quelques
secondes et interrogez la base :

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT momento, canal, estado FROM operacion WHERE orden = '4002' ORDER BY id"
```

Avant cela, prédisez : dans quel état se trouve la livraison ?

L'expédition est déjà `aceptada` dans les trois canaux, mais la livraison reste `estacionada`. Personne n'a prévenu que quelque chose
avait changé. Demandez maintenant le balayage à la main et interrogez de nouveau :

```bash
curl -s -XPOST localhost:8080/barrido
```

Cette fois, elle sort. Voilà ce que fait l'événement : prévenir au bon instant. Sans lui, quelqu'un devrait le demander, ou une
horloge devrait inspecter la base de temps en temps même s'il ne s'est rien passé. Remettez l'appel et éteignez :

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Vérifiez

```bash
./verifica
```

`verifica` crée la 4002, envoie sa livraison **avant** son expédition, contrôle qu'elle s'est garée, dépose l'expédition dans
la boîte aux lettres et, à la fin, demande un balayage à la main pour vérifier que rien ne sort deux fois. Elle est terminée quand vous voyez les
dix coches vertes :

```
✅ Arrancaron el router (con su máquina de estados y el barrido), los tres canales y el sondeo
✅ La 4002 está creada: CREADO en la API y REGISTRADO en el ERP
✅ La entrega de la 4002 llegó antes que su embarque y el router contestó 202 «recibida»
✅ Estado: las tres operaciones de la entrega quedaron estacionadas, sin ningún intento
✅ Destinos: a nadie le llegó la entrega a destiempo (la API sigue en CREADO, el ERP sin entrega, sin 214)
✅ Estado: llegó el embarque, el barrido soltó la entrega y los seis quedaron aceptados; la orden va en «entregada»
✅ API: la 4002 está ENTREGADO, con su guía
✅ ERP: la 4002 está ENTREGADO y su fila de entrega tiene la guía
✅ Socio: el 214 de la 4002 está en su SFTP y el lector X12 no le encontró fallas
✅ Una sola vez: un intento por canal para la entrega y un solo 201 en la API, aunque el barrido corrió otra vez a mano
```

La quatrième et la dernière sont celles qui comptent le plus. Ce qui est garé **n'a pas de tentatives** : cela n'est parti nulle part. Et le
balayage supplémentaire n'a rien répété.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/barrido.camel.yaml solucion/orden-router/barrido.camel.yaml` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `después del embarque, embarque y entrega quedaron` … avec la livraison `estacionada` | Il manque le TODO 3 ou le 4, ou le `filter` compare `decision` à un autre mot : c'est `'procesa'` |
| `${body[id]}` sort vide | Le `SELECT` ne ramène pas la colonne `id`, ou les variables sont restées hors du `split` |
| `Esperaba el barrido a mano en 200, 3 intentos de la entrega y un 201` et il y en a plus | L'`UPDATE` ne porte pas `AND estado = 'estacionada'`, ou vous n'avez pas filtré sur `CamelSqlUpdateCount` |
| Rien n'est mis en file alors que l'UPDATE a changé la ligne | Le `to` vers `seda:repartir` est resté hors du second `filter`, ou il manque `pattern: InOnly` |
| `El puerto 8080 está ocupado` | Les services de l'étape 4 sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**L'ordre dans lequel les choses arrivent n'est pas l'ordre dans lequel elles se sont passées.** Entre des systèmes qui ne se coordonnent pas, un avis
peut en devancer un autre. Le patron qui résout cela s'appelle *Resequencer* : conserver ce qui est arrivé dans le désordre et
le libérer dans le bon ordre.

**La règle de qui passe avant vit à un seul endroit.** Elle est dans un YAML qui se lit comme des données. Ajouter un nouvel
événement, un retour par exemple, c'est ajouter une ligne, pas une autre route.

**Quand deux processus se disputent la même chose, que la base décide.** L'`UPDATE … WHERE … AND estado = …` est une forme
de verrou : un seul peut faire passer la ligne d'un état à un autre. Vous retrouverez cette idée dans presque tout système
qui distribue du travail.

## Exercice : la règle comme données

Avec votre version terminée :

1. Dans la machine à états, que devriez-vous ajouter pour un nouvel événement, `devuelta`, qui aurait besoin de la livraison ?
   Écrivez-le. Devriez-vous toucher une route ?
2. Ouvrez les cinq cas de test de référence de la décision. Pour `entrega-con-embarque-pendiente`, pourquoi la décision est-elle
   de garer, alors que l'expédition **est déjà arrivée** ?
3. Dans la base, après le `verifica`, comptez les tentatives de la livraison de la 4002 :
   `SELECT o.canal, COUNT(i.id) FROM operacion o LEFT JOIN intento i ON i.operacion = o.id WHERE o.orden = '4002' AND o.momento = 'entregada' GROUP BY o.canal`.
   Combien en attendez-vous par canal, et pourquoi n'y en a-t-il pas deux alors que le balayage a tourné deux fois ?

---

## Ce qui fonctionne déjà

Avec cette leçon, vous refermez la partie 2. En plus de ce que fait la partie 1, une commande fait déjà ceci :

- elle est expédiée à partir d'un fichier de l'entrepôt, avec ou sans lot, sans qu'il soit pris à moitié écrit ni deux fois ;
- elle est enregistrée avant la réponse, avec une ligne par canal et une trace de chaque tentative ;
- elle est livrée à partir de l'avis du transporteur, qui reçoit un 202 dès que c'est enregistré ;
- et si la livraison arrive avant l'expédition, elle attend garée et sort une seule fois, dès qu'elle le peut.

Ce qu'elle ne fait pas encore : reconnaître un message qui arrive répété avec un autre contenu, ni retenter ce qui est resté en
`no_se_sabe`. C'est la partie 3.

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Ce qui arrive trop tôt se gare, on ne le rejette pas](../decisiones/21-estacionar-lo-que-llega-antes.md)

## Révision

1. Que veut dire qu'une opération soit `estacionada`, et en quoi se distingue-t-elle de `pendiente` ?
2. Que dit la machine à états sur la livraison, et pourquoi va-t-elle par canal ?
3. À quoi sert le `AND estado = 'estacionada'` dans l'UPDATE du balayage ?
4. Pourquoi le balayage contrôle-t-il `CamelSqlUpdateCount` avant de mettre en file ?
5. **La question de clôture :** le balayage est déclenché par un événement, le fait qu'un canal ait accepté un événement du cycle de vie, et non par un minuteur
   qui inspecte la base chaque minute. Que gagnez-vous avec l'événement ? Que perdriez-vous avec le minuteur, et dans quel cas
   vous en faudrait-il quand même un ?

**Suite :** dans la leçon 18 commence la partie 3, « Que rien n'échoue ». D'abord, une question qui paraît facile :
quand deux messages sont-ils le même ?
