# Leçon 16 — La livraison arrive

> **Ce que tu vas obtenir :** recevoir l'avis qu'envoie le transporteur quand il livre un colis, de sorte que la commande
> passe à ENTREGADO dans l'API et dans l'ERP, et que le partenaire reçoive son 214. Et distinguer un tel avis d'une requête.

**Avant de commencer, il te faut :** la leçon 15 terminée et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Tu as déjà deux événements du cycle de vie de la commande : elle est **créée** (la boutique appelle) et elle est **expédiée** (l'entrepôt dépose un fichier). Aujourd'hui
arrive le troisième : elle est **livrée**. Et il arrive d'une troisième façon : le transporteur **t'appelle de lui-même** quand il a livré, à
une adresse qui est la tienne. C'est un **webhook**, et ce qu'il apporte est un **événement** : il ne te demande rien, il te raconte que quelque chose s'est déjà
passé.

**L'image :** le message du transporteur sur ton téléphone, « Ton colis a été livré ». Il ne te demande de rien faire et
n'attend pas que tu lui répondais ce que tu vas faire. Il veut seulement savoir que le message t'est parvenu. Ce que tu feras ensuite (prévenir
la personne qui l'attendait, vérifier que c'est bien ton colis) te regarde.

---

## 1. Prépare ton dossier

```bash
cd lecciones/16-llega-la-entrega
cp -R inicio mio
```

Ouvre l'avis d'exemple, `datos/webhooks/entrega-4001.json`. Il contient l'événement (`entregado`), le transporteur, **le
numéro de suivi**, la commande, la date de livraison et la personne qui a reçu.

Tout ce qui concerne la sortie est déjà fait. Chaque canal a son `entrega.camel.yaml` :

| Canal | Ce qu'il fait de la livraison |
|---|---|
| int-rest | Indique à l'API que la commande a été livrée, comme un événement avec sa clé |
| int-bd | Enregistre la livraison dans l'ERP et passe la commande à ENTREGADO |
| int-edi | Construit un **214**, le statut d'expédition du transporteur de X12, avec le numéro de suivi dans le segment `B10`, et le dépose sur le SFTP |

La distribution de la 15 sait déjà envoyer l'événement `entregada`. **Il manque la porte.**

## 2. Prédis

Réponds sur papier :

1. Le transporteur signale qu'il a livré la 4001. Que doit-il rester dans la base d'état, et quoi dans chaque destination ?
2. Que répond le routeur au transporteur : ce qu'ont dit l'API, l'ERP et le partenaire, ou seulement « reçu » ? Pourquoi ?
3. Un avis arrive pour la commande 9999, que la boutique n'a jamais créée. Que devrait répondre le routeur ?

Regarde l'état actuel :

```bash
./verifica inicio
```

Le webhook répond **501**, ce qui en HTTP veut dire « cela, je ne sais pas le faire », et n'enregistre rien. La 4001 reste en
EMBARCADO pour toujours, alors que le client a déjà son colis.

## 3. Ouvre la porte

Ouvre `mio/orden-router/paqueteria.camel.yaml`. La route `webhook-paqueteria` écoute sur
`POST /webhooks/paqueteria`, et aujourd'hui elle ne répond que 501. Ton modèle est l'`embarque-router` de
`orden-router.camel.yaml` : il fait presque la même chose pour l'expédition. Lis-le avant de commencer.

| TODO | Ce que tu écris | Pour quoi faire |
|---|---|---|
| 1 | `convertBodyTo` vers `String` | Que l'avis soit enregistré tel qu'il est arrivé |
| 2 | Deux variables : `orden` (à partir de `$.orden`, avec `jsonpath`) et `momento` (`entregada`). Et appelle `direct:hilo` | Savoir de quelle commande il s'agit et de quel événement, avec son fil de la 09 |
| 3 | Appelle `direct:cargar-orden`. Avec un `choice` : si `${variable.tienda} == null`, vers `direct:no-existe` ; sinon, vers `direct:canales`, `direct:guardar-momento` et `direct:contestar-y-repartir` | Une commande que nous ne connaissons pas est rejetée ; une commande connue est enregistrée, on y répond et on la distribue |

Et efface les deux étapes qui répondent aujourd'hui 501.

Remarque que **tu n'écris rien de nouveau sur le fond** : `cargar-orden`, `canales`, `guardar-momento` et
`contestar-y-repartir` existent déjà depuis la 15. La livraison entre par une autre porte, mais à l'intérieur elle suit le même
chemin que la commande et l'expédition : **enregistrer, répondre, distribuer**.

## 4. Casses-en une exprès

Avec ton webhook terminé, retire le `choice` et ne garde que le contenu de la branche `otherwise` : comme si toute commande existait.
Démarre les services et envoie un avis pour une commande qui n'existe pas :

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -i -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' \
  -d '{"evento":"entregado","paqueteria":"PAQX","guia":"PAQX770009999","orden":"9999"}'
```

Avant cela, prédis : que répond le routeur ?

Il ne répond plus 404, il répond par une erreur du serveur. `guardar-momento` tente de créer les opérations d'une commande qui
n'est pas dans la table `orden`, la base l'en empêche et la transaction est annulée. Pense maintenant au transporteur : face à une
erreur du serveur, **un émetteur de webhooks renvoie l'avis**, et il continuera de l'envoyer. Le 404 dit la
vérité, « cette commande, je ne la connais pas », et l'émetteur cesse d'insister. Remets le `choice` et éteins :

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Vérifie

```bash
./verifica
```

`verifica` crée la 4001, l'expédie, joue le rôle du transporteur avec `entrega-4001.json` et, à la fin, envoie un avis pour la
9999. Elle est terminée quand tu vois les neuf coches vertes :

```
✅ Arrancaron el router (con el webhook de la paquetería), los tres canales y el sondeo
✅ Antes de la entrega: la 4001 está EMBARCADO en la API y en el ERP
✅ La paquetería avisó la entrega de la 4001 y el router contestó 202 «recibida»
✅ Estado: tres operaciones de la 4001 con momento=entregada, aceptadas, y la orden va en «entregada»
✅ API: la 4001 está ENTREGADO, con la guía con la que salió
✅ ERP: la 4001 está ENTREGADO y su fila de entrega tiene la guía
✅ Socio: el 214 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 214 lleva la guía en el B10 y el número de control de la base (…)
✅ Un aviso de la orden 9999, que no existe: el router contestó 404 y no guardó nada
```

Ouvre le 214 arrivé chez le partenaire, `mio/salida/214-4001.edi`, et cherche le numéro de suivi dans le segment `B10`.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/paqueteria.camel.yaml solucion/orden-router/paqueteria.camel.yaml` te dit en quoi elles
diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `Esperaba 202 del webhook y el router contestó` … 501 | Les deux étapes de `inicio/` qui répondent 501 sont restées : efface-les |
| `Un aviso de una orden que no existe contestó «500»` | Il manque le `choice`, ou la condition n'est pas `${variable.tienda} == null` |
| `las operaciones de la entrega quedaron «sin operaciones»` | La variable `momento` ne s'appelle pas ainsi ou ne vaut pas `entregada`, ou il manque `direct:guardar-momento` |
| La 4001 a été enregistrée, mais aucune destination ne l'a | Il manque `direct:contestar-y-repartir` à la fin |
| `El puerto 8080 está ocupado` | Les services de l'étape 4 sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**On ne répond pas de la même façon à un événement et à une requête.** À une requête (« crée cette commande »), on répond si cela a été possible.
À un événement (« j'ai déjà livré »), on répond seulement qu'il est arrivé : ce qui s'est passé s'est passé, et ne dépend pas de toi. C'est pour cela que le
routeur répond 202 et distribue ensuite.

**Celui qui envoie un webhook attend peu et retente.** Les émetteurs ne comptent comme remis que ce qui a reçu un 2xx à
temps, et renvoient le reste. Répondre dès que tu as enregistré ménage les deux aspects : cela ne fait pas attendre et ne provoque pas
de nouvelles tentatives inutiles.

**Le code de réponse est une instruction pour celui qui envoie.** 202 veut dire « je l'ai », 404 veut dire « n'insiste pas, cela
n'existe pas » et une erreur du serveur veut dire « réessaie plus tard ». Mal choisir le code change ce que fait l'autre système.

## Exercice : trois portes, un chemin

Avec ta version terminée :

1. Fais un tableau avec les trois événements : créée, expédiée et livrée. Pour chacun : qui l'envoie, par où il entre
   (HTTP de la boutique, fichier de l'entrepôt, webhook) et quelle route du routeur le reçoit. Qu'ont en commun les trois
   routes après l'entrée ?
2. Dans la base d'état, consulte les opérations de la 4001 pour les trois événements :
   `SELECT momento, canal, estado FROM operacion WHERE orden = '4001' ORDER BY id`. Combien de lignes y a-t-il, et pourquoi ?
3. Que se passerait-il si le transporteur signalait la livraison de la 4002 **avant** que son expédition n'arrive ? Écris ta
   réponse : c'est la leçon 17.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Le webhook du transporteur répond 202 dès qu'il a enregistré](../decisiones/20-el-webhook-contesta-202.md)

## Révision

1. Qu'est-ce qu'un webhook, et en quoi se distingue-t-il de la façon dont arrive la commande et de celle dont arrive l'expédition ?
2. Qu'est-ce qu'un 214, et que porte-t-il dans son segment `B10` ?
3. Pourquoi le routeur répond-il 202 au transporteur et non ce qu'ont dit l'API, l'ERP et le partenaire ?
4. Pourquoi répond-on par un 404, et non par une erreur du serveur, à un avis portant sur une commande qui n'existe pas ?
5. **La question de clôture :** « j'ai livré le colis » est un événement ; « crée cette commande » est une requête. Avec un exemple
   du cours pour chacun, explique en quoi ils se distinguent et comment cela change ce que répond le routeur.

**Suite :** dans la leçon 17, le monde se dérègle : la livraison de la 4002 arrive avant son numéro de suivi. Le routeur ne
peut pas encore la distribuer, mais il ne peut pas non plus la jeter. Qu'en fais-tu ?
