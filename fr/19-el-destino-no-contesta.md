# Leçon 19 — La destination ne répond pas

> **Ce que tu vas obtenir :** que, lorsque l'API ne répond pas, le routeur réessaie tout seul, avec une pause chaque fois plus longue,
> jusqu'à ce que la commande arrive. Et que, pendant ce temps, l'expédition de cette commande ne passe pas devant : l'API doit recevoir
> d'abord la commande, puis son expédition.

**Avant de commencer, il te faut :** la leçon 18 terminée et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Dans la 15, le routeur a appris à enregistrer avant de répondre, et dans la 18 à reconnaître un message en double. Aujourd'hui, c'est
le contraire de la 18 : le message **n'arrive pas**, parce que la destination ne répond pas. Le routeur ne peut pas savoir si l'API
a reçu la commande ; ce qu'il peut faire, c'est réessayer. Et comme il sait déjà reconnaître les doublons (l'API aussi : tu
l'as vu dans la 02 avec la clé), réessayer ne duplique rien.

**L'image :** appeler quelqu'un qui ne décroche pas. Tu ne l'appelles pas toutes les secondes pendant une heure : tu l'appelles, tu attends une
minute, tu rappelles, tu attends deux minutes, puis quatre. Tu lui laisses le temps de sortir de réunion. Et tu ne lui racontes pas la
deuxième chose avant la première : si l'appel servait à le prévenir que son colis est parti, tu ne lui envoies pas avant un message
« l'as-tu reçu ? ».

---

## 1. Prépare ton dossier

```bash
cd lecciones/19-el-destino-no-contesta
cp -R inicio mio
```

Aujourd'hui, tout le travail tient dans un nouveau fichier du routeur, `orden-router/reintentos.camel.yaml`. Ouvre-le et lis le
commentaire du haut en entier. Il contient un schéma :

```
intento 1 ✗ ─ 1 s ─ intento 2 ✗ ─ 2 s ─ intento 3 ✗ ─ 4 s ─ intento 4 ✓     (más o menos 15 % al azar)
```

Et trois règles qui sont déjà dans le code :

| Règle | Ce qu'elle signifie | Où elle vit |
|---|---|---|
| **Seul le routeur retente** | Les canaux (int-rest, int-bd, int-edi) répondent ce qui leur est arrivé, et c'est tout | Les canaux n'ont pas de nouvelles tentatives |
| **On ne répète que ce qui est resté « on ne sait pas »** | Pas de réponse, ou une erreur 5xx. Un 4xx de cette API (son 422) est un « non » de la destination et ne se répète pas (dans la 20, tu verras que tous les 4xx ne sont pas un « non ») | `direct:intentar` lève une `IOException` dans ce seul cas |
| **Chaque tentative laisse sa ligne** | Avant d'appeler le canal, `intentar` écrit dans la table `intento` ; ensuite, il note comment cela s'est passé | `reparto.camel.yaml`, depuis la 15 |

## 2. Prédis

Réponds sur papier :

1. L'API est éteinte quand la commande 4001 arrive, et elle revient quelques secondes plus tard. Entre-temps, l'entrepôt dépose
   l'expédition de la 4001. Dans quel ordre la commande et l'expédition devraient-elles arriver à l'API ? Et si elles arrivaient à l'envers ?
2. Si le routeur retente toutes les 100 millisecondes, qu'arrive-t-il à une API qui est en train de démarrer ?
3. L'ERP et le partenaire, eux, étaient en marche. Combien de fois devraient-ils recevoir la 4001 ?

Regarde l'état actuel :

```bash
./verifica inicio
```

La boutique reçoit son 202, parce que le routeur a enregistré. Mais le canal rest essaie **une seule fois** : l'API était
éteinte, la tentative reste en `no_se_sabe` et personne ne la retente. L'API revient quelques secondes plus tard et la
4001 ne lui parvient jamais.

## 3. Retente avec un délai croissant

Ouvre `mio/orden-router/reintentos.camel.yaml`. En haut se trouve un `routeConfiguration` avec un `onException` qui attrape
l'`IOException` que lève `intentar`. Aujourd'hui, il se contente de noter dans le journal qu'il a renoncé. Ton travail est de lui dire **combien de fois
et avec quelle pause** retenter, avec une `redeliveryPolicy` à l'intérieur du `onException` :

| TODO | Ce que tu écris | Pour quoi faire |
|---|---|---|
| 1 | `maximumRedeliveries: 5` et `redeliveryDelay: 1000` | Jusqu'à cinq nouvelles tentatives ; la première, au bout d'une seconde |
| 2 | `useExponentialBackOff: true` et `backOffMultiplier: 2` | Que chaque pause soit le double de la précédente : 1, 2, 4, 8, 16 secondes |
| 3 | `useCollisionAvoidance: true` et `collisionAvoidanceFactor: 0.15` | Que chaque pause varie de 15 % au hasard |
| 4 | `retryAttemptedLogLevel: WARN` | Que chaque nouvelle tentative se voie dans le journal |

La `redeliveryPolicy` se place à la même hauteur que `handled` et `steps`, à l'intérieur du `onException`. Voici le
squelette :

```yaml
- onException:
    exception:
      - java.io.IOException
    redeliveryPolicy:
      maximumRedeliveries: 5
      …
    handled:
      constant:
        expression: "true"
    steps:
      - log: …
```

Remarque deux choses :

- **La variation aléatoire (jitter) du TODO 3 a l'air d'un caprice et n'en est pas un.** Imagine trois répliques du routeur (la 12) qui perdent l'API au
  même moment. Sans variation aléatoire, les trois retentent à la seconde exacte, puis à deux, puis à quatre : l'API
  revient et reçoit trois coups ensemble à chaque fois. Avec 15 % de variation, les coups s'espacent.
- **La nouvelle tentative se fait « sur place ».** La file `seda:repartir` a un seul consommateur, et la pause l'arrête. Ce qui
  suit dans la file, comme l'expédition de la 4001, attend son tour. C'est ce qui préserve l'ordre.

## 4. Casses-en une exprès

Avec tes nouvelles tentatives terminées, passe `maximumRedeliveries` à `1` et lance `./verifica`.

Avant cela, prédis : le verifica laisse l'API éteinte environ six secondes. Avec une seule nouvelle tentative au bout d'une seconde, la commande arrive-t-elle
à l'API ?

Elle n'arrive pas. La première tentative échoue, la nouvelle tentative au bout d'une seconde aussi, et le routeur renonce alors que l'API est encore
éteinte. La coche des tentatives de rest et celle de l'API sortent en rouge. L'opération reste en `no_se_sabe`, et c'est
correct : le routeur **n'invente pas** qu'il l'a livrée. Mais cela montre le coût d'en faire trop peu : cinq nouvelles tentatives avec un
délai croissant couvrent une trentaine de secondes de panne ; une seule, une seconde. Reviens à `5`.

## 5. Vérifie

```bash
./verifica
```

`verifica` éteint l'API, envoie la 4001, dépose son expédition dans la boîte aux lettres, attend quelques secondes et rallume
l'API. Elle est terminée quand tu vois les six coches vertes :

```
✅ Arrancaron el router (con sus reintentos), los tres canales y el sondeo
✅ Con la API apagada, la tienda recibió su 202: lo suyo quedó guardado
✅ Estado: el canal rest reintentó en el lugar: (…) intentos; esperas (…); desenlaces (…)
✅ Estado: el ERP y el socio no se repitieron: aceptados al primer intento (bd=aceptada/1 edi=aceptada/1)
✅ API: recibió la orden y después su embarque, y la 4001 quedó EMBARCADO
✅ ERP: la 4001 está EMBARCADO
```

Lis la troisième coche calmement. Elle te dit combien de tentatives il y a eu, combien de temps il a attendu entre l'une et l'autre et comment chacune s'est
terminée. Les attentes changent à chaque exécution à cause de la variation aléatoire, mais elles croissent toujours, et la dernière tentative est la seule
`aceptada`.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/reintentos.camel.yaml solucion/orden-router/reintentos.camel.yaml` te dit en quoi elles
diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `esperaba 3 o más intentos de rest` et il y en a 1 | Il manque la `redeliveryPolicy`, ou elle est restée hors du `onException` |
| `esperaba 3 o más intentos de rest` et les attentes ne croissent pas | Il manque `useExponentialBackOff: true` |
| `esperaba la orden antes que el embarque` et la commande « n'est pas arrivée » | Trop peu de tentatives ou des pauses trop courtes : le routeur a renoncé avant le retour de l'API |
| `El ERP y el socio quedaron` avec plus d'une tentative | Tu as mis des nouvelles tentatives ailleurs aussi : elles ne vont que dans `reintentos.camel.yaml` |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

Si le verifica s'interrompt au milieu, l'API se rallume toute seule à la sortie. Si tu la vois malgré tout éteinte :
`docker compose -f ../../infra/compose.yaml start api-pedidos`.

---

## Ce que cela enseigne au-delà du cours

**Retenter n'est sûr que si la destination reconnaît les doublons.** Le routeur ne sait pas si la première tentative est arrivée.
Si elle est arrivée et qu'il retente, l'API reçoit la commande deux fois, et ce qui empêche le doublon est sa clé d'idempotence (la
02). Des nouvelles tentatives sans idempotence sont des doublons sous un autre nom.

**Le délai croissant avec variation aléatoire est la pratique habituelle**, et il a un nom : *exponential backoff with jitter*. Les
grands fournisseurs de cloud le recommandent pour tout client de leurs services. Avec une attente fixe, soit tu donnes trop de temps
à celui qui est déjà revenu, soit tu frappes celui qui est encore en train de démarrer.

**Un seul retente.** Si le routeur retente cinq fois et le canal cinq fois aussi, un seul envoi peut devenir
trente-six appels. On choisit un endroit, et le reste répond ce qui lui est arrivé.

**L'ordre a un prix.** Retenter sur place arrête la file : tant que l'API ne répond pas, rien de ce qui
suit n'avance. Ici, cela en vaut la peine, parce qu'une expédition avant sa commande est une erreur. Quand une destination en panne
retient trop les autres, il faut un autre outil : c'est la leçon 22.

## Exercice : le journal des tentatives

Avec ta version terminée, lance `./verifica` et entre dans la base :

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. Consulte les tentatives du canal rest pour la création de la 4001, avec leur heure :
   `SELECT i.n, i.inicio, i.desenlace FROM intento i JOIN operacion p ON p.id = i.operacion WHERE p.orden = '4001' AND p.momento = 'creada' AND p.canal = 'rest' ORDER BY i.n;`
   Soustrais à la main l'heure de chaque tentative de celle de la précédente. Cela ressemble-t-il à 1, 2, 4 et 8 secondes ? Pourquoi ne sont-elles pas
   exactes ?
2. Additionne les cinq pauses de la politique (1 + 2 + 4 + 8 + 16). Si l'API mettait une minute à revenir, qu'arriverait-il à
   la commande ? Dans quel état resterait-elle, et que faudrait-il pour qu'elle arrive ? (La leçon 23 le résout.)
3. Explique avec tes mots pourquoi l'expédition de la 4001 est arrivée **après** la commande, alors que l'entrepôt l'a déposée
   pendant que l'API était éteinte.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [On retente sur place, seulement dans le routeur, avec un délai croissant](../decisiones/23-reintentar-en-el-lugar.md)

## Révision

1. Quelles réponses de la destination se retentent et lesquelles non ? Pourquoi pas le 422 de cette API ?
2. À quoi sert que chaque pause soit le double de la précédente ?
3. À quoi servent les 15 % de variation aléatoire ? Explique-le avec trois répliques du routeur.
4. Pourquoi retenter n'a-t-il pas dupliqué la commande dans l'API ?
5. **La question de clôture :** retenter « sur place » préserve l'ordre. Pourquoi, et qu'est-ce que cela coûte ? Prends l'expédition de
   la 4001 comme exemple.

**Suite :** dans la leçon 20, l'API répond bien, mais elle ne dit pas toujours « oui ». Parfois elle dit « non », et parfois elle tarde
tellement que l'appel est coupé. A-t-elle écrit ou non ?
