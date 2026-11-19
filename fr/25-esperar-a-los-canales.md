# Leçon 25 — Attendre les canaux

> **Ce que tu vas obtenir :** savoir quand une commande est **complète** : quand chaque canal qui la concerne est arrivé à ce que l'on
> attend de lui, y compris le 997 du partenaire. Et qu'un canal qui tarde trop fasse l'objet d'une **alerte** sans que la commande soit tenue pour
> close, pour que ce qui arrive en retard la complète encore, même si le routeur a redémarré entre-temps.

**Avant de commencer, il te faut :** les leçons 21 et 22 terminées et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Depuis la 10, le routeur **disperse** : il envoie chaque événement du cycle de vie de la commande à ses canaux. Il ne **rassemble** jamais : il n'y a aucun
endroit qui dise « cette commande est terminée ». Et dans la 21, tu as vu que « terminer » ne veut pas dire la même chose pour tous : l'API et l'ERP
terminent quand ils acceptent, le partenaire quand il envoie son 997. Disperser puis rassembler les réponses est un patron qui a un
nom, *Scatter-Gather*, et la partie qui rassemble s'appelle *Aggregator*. Aujourd'hui, tu le construis, avec une idée qui vient de
la 15 : **ce qu'il faut rassembler est déjà dans la base.**

**L'image :** organiser le dîner du réveillon avec trois cousins qui apportent chacun quelque chose. La tante ne tient pas le dîner pour prêt quand
chaque cousin a dit « j'arrive » : elle le tient pour prêt quand les plats sont sur la table. Si à neuf heures celui du dessert n'est pas
arrivé, la tante ne raye pas le dessert pour déclarer le dîner complet : elle l'appelle pour demander. Et si le dessert arrive à
neuf heures et demie, le dîner est complet à ce moment-là. Si la tante sort un moment dans la cour, elle ne perd pas le compte : la table est toujours
là, avec ce qui est déjà arrivé.

---

## 1. Prépare ton dossier

```bash
cd lecciones/25-esperar-a-los-canales
cp -R inicio mio
```

Ouvre d'abord `mio/orden-router/reglas/maquina-de-estados.yaml` et cherche la section `completa`. On y trouve ce que l'on
attend de chaque canal et combien de temps on l'attend :

| Canal | `llega_a` | `plazo_s` | Pourquoi |
|---|---|---|---|
| `rest` (l'API) | `aceptada` | 10 | Son « oui » arrive dans le même appel |
| `bd` (l'ERP) | `aceptada` | 10 | De même |
| `edi` (le partenaire) | `confirmada` | 20 | Son « oui » est le 997, et il arrive plus tard (la 21) |

Ouvre maintenant `mio/orden-router/juntar.camel.yaml`. En haut, il y a un schéma de la 4001 à mi-chemin : l'API et l'ERP sont déjà
arrivés, le partenaire pas encore. Et il y a une route, `vigilar`, déjà faite : une **horloge** (`timer`) qui, toutes les 2 secondes, lit
les règles et appelle deux routes :

| Route | Ce qu'elle fait | Où elle le note |
|---|---|---|
| `juntar` | Tient pour COMPLET chaque événement dont tous les canaux sont arrivés à leur `llega_a` | Table `completitud`, une ligne par commande et par événement |
| `vencidos` | Alerte sur chaque canal qui a dépassé son délai sans arriver | Table `alerta` et un `WARN` dans le journal |

Pourquoi une horloge, plutôt que d'attendre que quelque chose prévienne ? Parce que **ce qui n'arrive pas ne prévient pas**. Un 997 qui ne vient jamais ne
déclenche aucun événement. Quelqu'un doit regarder l'horloge.

Remarque ce qu'il n'y a pas : il n'y a pas d'agrégateur qui garde dans la mémoire du routeur quels canaux ont déjà répondu. Chaque
opération et son état vivent dans la table `operacion` depuis la 15. **Rassembler, c'est interroger la base.**

## 2. Prédis

Réponds sur papier :

1. L'API et l'ERP ont accepté la 4001, et le 850 est déposé. Le partenaire n'a pas envoyé le 997. La commande est-elle
   complète ?
2. La 3001 vient d'une boutique en propre : elle n'a pas d'EDI (la 11). Quand est-elle complète ?
3. 20 secondes passent et le 997 de la 4002 n'arrive pas. Que vaut-il mieux : tenir la commande pour complète pour qu'elle cesse de
   gêner, ou alerter et la laisser ouverte ? Que se passe-t-il dans chaque cas si le 997 arrive au bout de 30 secondes ?
4. Tu redémarres le routeur pendant que la 4002 attend son 997. Perd-on ce qui était déjà rassemblé ?

Regarde l'état actuel :

```bash
./verifica inicio
```

Cela se passe mal dès la première coche : la 4001 reste COMPLETA **avant** son 997, parce que la version d'`inicio/` tient pour
complet un événement dès qu'aucun canal n'est plus `pendiente`, et qu'un 850 déposé est `aceptada`. Et la 4002, à l'échéance,
est clôturée de force : elle reste complète sans que le partenaire ait répondu quoi que ce soit.

## 3. Rassemble et surveille

Dans `juntar.camel.yaml`, il y a deux TODO, un dans chaque route. Les deux sont une requête SQL déjà presque entière.

### TODO 1 : complète veut dire « chaque canal est arrivé à ce qui lui revient »

La requête de `juntar` joint chaque opération à la règle de son canal et regroupe par commande et par événement :

```sql
INSERT INTO completitud (orden, momento)
SELECT p.orden, p.momento
FROM operacion p JOIN jsonb_each(CAST(:#${variable.reglas} AS jsonb)) AS r(canal, regla) ON r.canal = p.canal
WHERE NOT EXISTS (SELECT 1 FROM completitud c WHERE c.orden = p.orden AND c.momento = p.momento)
GROUP BY p.orden, p.momento
HAVING bool_and(p.estado NOT IN ('pendiente', 'estacionada'))     -- ← esta condición
ON CONFLICT DO NOTHING
RETURNING orden, momento
```

Elle se lit ainsi :

| Pièce | Ce qu'elle fait |
|---|---|
| `jsonb_each(…) AS r(canal, regla)` | Convertit les règles en lignes : une par canal, avec son `llega_a` et son `plazo_s` |
| `JOIN … ON r.canal = p.canal` | Accole à chaque opération la règle de son canal |
| `GROUP BY p.orden, p.momento` | Rassemble les opérations de chaque événement de chaque commande |
| `HAVING bool_and(…)` | L'événement ne passe que si la condition est vraie **pour toutes** ses opérations |
| `ON CONFLICT DO NOTHING` | Un événement n'est complété qu'une seule fois |

Ton travail est la condition du `HAVING`. Aujourd'hui, elle dit « aucune n'est encore en attente ni garée ». Elle doit dire **« chacune
est dans un état de son `llega_a` »** :

```sql
HAVING bool_and(jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado)))
```

`r.regla -> 'llega_a'` est la liste de la règle, par exemple `["confirmada"]` ; `to_jsonb(p.estado)` est l'état de
l'opération comme valeur JSON ; `jsonb_contains` demande si le second figure dans la première.

Remarque une chose qui vient gratuitement : **la 3001 n'a pas d'opération `edi`**, parce qu'elle vient d'une boutique en propre. Comme elle n'a pas
de ligne, elle ne compte pas : personne n'attend un 997 qui ne la concerne pas.

### TODO 2 : un délai dépassé alerte et ne clôture pas

La requête de `vencidos` **écrit aujourd'hui dans `completitud`** : elle clôture l'événement à l'échéance. Change-la pour qu'elle écrive une
alerte dans `alerta`, une fois par canal :

| Changement | Ce que tu écris |
|---|---|
| Vers où | `INSERT INTO alerta (orden, momento, canal, tipo, estado, espera)` |
| Quoi | `SELECT p.orden, p.momento, p.canal, 'vencido', p.estado, r.regla ->> 'llega_a'` (sans le `DISTINCT`) |
| Lesquels | Ajoute au `WHERE` : `NOT jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado))`, pour n'alerter que sur les canaux qui ne sont **pas** arrivés |
| Une fois | Laisse le `ON CONFLICT DO NOTHING` : la table `alerta` a une `UNIQUE (orden, momento, canal, tipo)` |
| Pour le journal | Ajoute `RETURNING orden, momento, canal, estado, espera` et `outputType: SelectList` |

Et après la requête, un `split` sur `${body}` avec un `log` en `WARN` pour chaque nouvelle alerte, comme le `split` de
`juntar`. Le message commence par `VENCIDO orden ${body[orden]} momento ${body[momento]} canal ${body[canal]}`.

## 4. Casses-en une exprès

Une fois tout terminé, retire du `WHERE` de `vencidos` la condition `NOT jsonb_contains(…)`. Lance `./verifica`.

Avant cela, prédis : la 4002 attend son 997 plus de 20 secondes. L'API et l'ERP l'ont acceptée au bout de quelques secondes. Combien
d'alertes attends-tu ?

Trois au lieu d'une. Sans cette condition, l'horloge alerte sur **tout** canal qui a dépassé son délai, qu'il soit arrivé ou non :
l'API et l'ERP, qui ont déjà accepté, sortent eux aussi comme en dépassement. La coche de l'alerte de la 4002 sort en rouge. Une alerte
qui se déclenche sur ce qui va bien apprend aux gens à ignorer les alertes, et celle qui compte se perd alors. Remets la
condition.

## 5. Vérifie

```bash
./verifica
```

`verifica` envoie une commande de boutique en propre et une de franchise, attend le 997 ; puis il éteint le système du partenaire et
envoie la 4002, attend que le délai soit dépassé, **redémarre le routeur** et rallume le partenaire. Elle est terminée quand tu vois les
sept coches vertes :

```
✅ Arrancaron el router (con su reloj que junta y vigila), los tres canales y el sondeo
✅ La 4001: API y ERP aceptada y el 850 depositado, pero sin 997 todavía no está COMPLETA
✅ La 3001 (tienda propia): COMPLETA con la API y el ERP; el socio no le toca y no se le espera
✅ La 4001: llegó el 997, el socio quedó confirmada y la orden COMPLETA
✅ La 4002 sin 997 pasó su plazo: un solo aviso (vencido, en «aceptada», se espera confirmada) y sigue abierta
✅ Log: el aviso del vencido está en el log del router
✅ Tras reiniciar el router, el 997 tardío confirmó al socio y la 4002 quedó COMPLETA, con su único aviso como historia
```

Lis la dernière calmement. Le routeur a redémarré **en pleine attente**, et la 4002 s'est complétée quand même. Tu n'as rien écrit
pour cela : ce qui était en train d'être rassemblé n'a jamais vécu dans la mémoire du routeur, cela vivait dans la base. À son retour, l'horloge
a demandé de nouveau et a trouvé la même chose, plus le 997 qui venait d'arriver.

Regarde les deux tables :

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT * FROM completitud ORDER BY completa" -c "SELECT orden, canal, estado, espera, creada FROM alerta"
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/juntar.camel.yaml solucion/orden-router/juntar.camel.yaml` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `La 4001 con el socio en «aceptada» ya quedó COMPLETA` | Le `HAVING` garde la condition d'`inicio/` : remplace-la par le `jsonb_contains` |
| `La 3001 (tienda propia) no quedó COMPLETA` | Le `JOIN` avec les règles est absent, ou tu as remplacé `bool_and` par autre chose |
| `La 4002 sin 997: hay 0 avisos` et `completa=1` | `vencidos` écrit encore dans `completitud` : c'est dans `alerta` |
| `La 4002 sin 997: hay 3 avisos` | Il manque le `NOT jsonb_contains(…)` dans le `WHERE` de `vencidos` |
| `no está el WARN del vencido de la 4002` | Il manque le `RETURNING` avec `outputType: SelectList`, ou le `split` avec son `log` en `WARN` |
| `No está arriba socio-bot` | Démarre l'environnement : `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**Disperser est facile ; c'est rassembler qui demande du travail.** Envoyer quelque chose à plusieurs destinations, n'importe quel multicast le fait.
Savoir quand tous ont terminé, chacun avec sa propre idée de « j'ai terminé », c'est ce qui dit au métier que la commande
est désormais partout.

**Ce qui n'arrive pas ne prévient pas.** Un système qui ne fait que réagir à des événements n'apprend jamais ce qui manque. Il faut quelque chose
qui regarde l'horloge et qui demande.

**Alerter n'est pas clôturer.** Clôturer de force à l'échéance dit « terminé » de quelque chose qui ne l'est pas, et si ce qui manquait arrive
en retard, cela ne change plus rien. Alerter laisse une personne décider, et laisse ce qui arrive tard compter encore.

**Si l'état vit hors du processus, le redémarrage n'est pas un problème.** Nous l'avons dit dans la 15 comme une théorie ; aujourd'hui, tu l'as vu :
le routeur a redémarré en pleine attente et n'a rien perdu, sans une ligne de code de plus.

## Exercice : le dîner du réveillon

1. Avant de lancer quoi que ce soit, écris sur papier ta prédiction pour la question 4 ci-dessus : perd-on ce qui est rassemblé en
   redémarrant ? Après avoir lancé `./verifica`, compare-la avec ce qui s'est passé et écris où vivait ce qui était rassemblé.
2. Dans les règles, passe le `plazo_s` du partenaire à `5` et lance `./verifica`. Quelles coches changent, et pourquoi ? Qu'arriverait-il
   aux personnes qui traitent les alertes si le délai était aussi court dans la vie réelle ? Remets-le à `20`.
3. Imagine que le routeur ait trois répliques, chacune avec son horloge. Une commande serait-elle complétée trois fois ? Alerterait-on
   trois fois pour un délai dépassé ? (Indice : cherche les deux `UNIQUE` et le `ON CONFLICT`.)

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Rassembler, c'est interroger la base, et un délai dépassé alerte sans clôturer](../decisiones/29-juntar-es-preguntarle-a-la-base.md)

## Révision

1. Qu'est-ce qu'un Scatter-Gather ? Quelle partie du cours disperse et quelle partie rassemble ?
2. Qu'attend chaque canal pour être tenu pour terminé, et où est-ce écrit ?
3. Pourquoi faut-il une horloge pour rassembler ?
4. Pourquoi la 3001 est-elle complète sans 997 ?
5. **La question de clôture :** quand le délai du partenaire est dépassé, le routeur alerte et laisse la commande ouverte. Pourquoi la clôturer
   de force serait-il pire ? Sers-toi du 997 tardif de la 4002 dans ta réponse.

**Suite :** dans la leçon 26, un client dit que sa commande n'est pas arrivée. Tu as la base, le journal et les destinations. Que
consultes-tu d'abord, et comment reconstitues-tu le voyage complet d'une commande ?
