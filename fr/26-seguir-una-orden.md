# Leçon 26 — Suivre une commande

> **Ce que tu vas obtenir :** répondre à « qu'est-il arrivé à la commande 4006 ? » avec une seule requête : chaque canal, chaque tentative,
> ce qui est parti dans chacune, en entier, ce qu'a répondu le partenaire et si la commande est complète. Et servir cette réponse
> par HTTP, en cherchant par le numéro de commande.

**Avant de commencer, il te faut :** les leçons 15 et 25 terminées et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Cela fait onze leçons que tu enregistres des choses dans la base d'état : la commande (15), chaque opération et chaque tentative (15), les
conflits (18), les accusés de réception du partenaire (21), les alertes et ce qui est complet (25). Chaque pièce répond à une petite question.
Aujourd'hui, tu les réunis pour répondre à la grande, celle que pose un client ou quelqu'un du support : **« ma commande n'est pas arrivée, que lui
est-il arrivé ? »**. Et tu y réponds par la **clé métier**, le numéro de commande, qui est la seule chose qu'apporte celui qui demande.

**L'image :** le suivi d'un colis sur la page d'un transporteur. Tu saisis le numéro de suivi et tu vois l'histoire
complète : reçu en agence, en transit, tentative de livraison échouée parce qu'il n'y avait personne, livré. Tu ne vois pas seulement
l'état d'aujourd'hui : tu vois **chaque étape**, avec son heure. S'il disait seulement « livré », tu ne saurais pas qu'il y a eu une tentative échouée,
ni pourquoi il a mis deux jours.

---

## 1. Prépare ton dossier

```bash
cd lecciones/26-seguir-una-orden
cp -R inicio mio
```

Le routeur a deux nouveaux fichiers :

| Fichier | Ce que c'est | L'écris-tu ? |
|---|---|---|
| `orden-router/recorrido.camel.yaml` | Au démarrage, il déclare la vue avec ce que dit `recorrido.sql`. Et il sert `GET /ordenes/{orden}/recorrido` | Non, lis-le |
| `orden-router/recorrido.sql` | La vue `recorrido` de la base d'état | **Oui** |

Une **vue** est une requête enregistrée sous un nom. Elle se lit comme une table (`SELECT * FROM recorrido WHERE orden = '4006'`), mais elle ne conserve rien en propre : chaque fois que tu la lis, elle rassemble de nouveau les vraies tables. C'est pour cela
qu'elle ne devient jamais périmée.

Et il y a une nouvelle table depuis aujourd'hui, `payload`. Chaque tentative conserve dans `intento` son **empreinte** (`sha256`) et sa
**taille**, et le corps complet de ce qui est parti vit dans `payload`, une fois par empreinte. Si deux tentatives ont envoyé la
même chose, elles partagent la ligne.

Ouvre `infra/postgres/01-estado.sql` et repère les tables que tu vas réunir : `operacion`, `intento`, `payload`, `acuse`,
`alerta` et `completitud`.

## 2. Prédis

Réponds sur papier :

1. Un client dit que la 4006 n'est pas arrivée. Que consultes-tu d'abord : le journal du routeur, l'API, l'ERP ou la base
   d'état ? Pourquoi ?
2. L'API était en panne quand la 4006 est arrivée ; ensuite, quelqu'un a fait un rerun. Si la vue ne montre que la dernière
   tentative de chaque canal, quelle partie de l'histoire se perd ?
3. La 4007 contient 150 lignes de commande. Le journal du routeur la montre-t-il en entier ?

Regarde l'état actuel :

```bash
./verifica inicio
```

La vue existe et le `GET` répond, mais c'est un **tableau de bord du jour** : une ligne par opération, avec sa dernière tentative. L'histoire
du canal rest de la 4006 apparaît comme un seul `aceptada`, sans la première tentative dont on n'a pas su l'issue ni l'auteur du
rerun. Et la colonne `cuerpo` sort vide : elle ne dit pas ce qui est parti.

## 3. Construis le parcours

Ouvre `mio/orden-router/recorrido.sql`. C'est un `CREATE VIEW` avec un `SELECT` qui contient déjà les colonnes sous leur nom
final ; certaines sortent en `NULL` pour l'instant. Il a trois TODO :

| TODO | Ce que tu changes | Pour quoi faire |
|---|---|---|
| 1 | Le `LEFT JOIN LATERAL (… LIMIT 1) u` par un `LEFT JOIN intento i ON i.operacion = p.id`, et les colonnes `u.` par `i.` | Une ligne pour **chaque** tentative, pas seulement la dernière |
| 2 | Un `LEFT JOIN payload d ON d.sha256 = i.sha256`, et `cuerpo` vient de `d.cuerpo` | Ce qui est parti dans chaque tentative, en entier |
| 3 | Trois colonnes qui sont aujourd'hui `NULL` : `acuses`, `alertas` et `completa` | Ce qui est venu après l'envoi |

Pour le TODO 3, deux des colonnes sont une **sous-requête** qui réunit plusieurs lignes en un texte avec `string_agg` :

```sql
(SELECT string_agg(a.codigo, ',' ORDER BY a.id) FROM acuse a WHERE a.operacion = p.id) AS acuses,
```

Elle donne, par exemple, `A` ou `R` : ce que le partenaire a dit de cette opération. Celle de `alertas` est identique, sur la table
`alerta`, pour la même commande, le même événement du cycle de vie et le même canal, avec `l.tipo || ' en ' || l.estado`. Et `completa`
vient d'un `LEFT JOIN completitud c ON c.orden = p.orden AND c.momento = p.momento`, avec `c.completa`.

Pourquoi `LEFT JOIN` et non `JOIN` ? Parce qu'une opération qui n'a pas encore de tentatives, ou une tentative sans accusé, ou un
événement qui n'est pas encore complété, **doit sortir aussi**. Avec `JOIN`, ils disparaîtraient, et ce qui manque est justement ce
qui compte le plus quand on suit une commande.

Et pourquoi des sous-requêtes pour les accusés et les alertes, et non d'autres `JOIN` ? Parce qu'un `JOIN` contre une table qui a plusieurs lignes par
opération **multiplie** les lignes : chaque tentative sortirait une fois par accusé. La sous-requête les réunit dans une seule
cellule.

Laisse les noms des colonnes tels qu'ils sont : le `GET` les utilise.

## 4. Casses-en une exprès

Avec ta vue terminée, retire le `LEFT JOIN completitud` et laisse `completa` en `CAST(NULL AS timestamptz)`. Lance
`./verifica`.

Avant cela, prédis : la 4007 a été complétée. Que dit le parcours ?

Que non. La dernière coche sort en rouge : la vue dit que **ni** la 4007 **ni** la 4006 ne sont complètes. Rien n'a échoué :
la vue ne regardait tout simplement pas cette table, et une cellule vide se lit comme « cela ne s'est pas passé ». **Un parcours qui ne montre pas quelque chose
ne prouve pas que cela ne s'est pas passé.** C'est pour cela qu'il se construit avec des `LEFT JOIN` contre tout ce que la base sait. Remets la ligne.

## 5. Vérifie

```bash
./verifica
```

`verifica` éteint l'API, envoie la 4006 (qui demande un produit que le partenaire ne connaît pas) et la 4007 (grosse, avec 150
lignes de commande) ; il rallume l'API, fait un rerun du canal rest pour les deux et demande le parcours. Elle est terminée
quand tu vois les huit coches vertes :

```
✅ Arrancaron el router (con su GET /ordenes/{orden}/recorrido), los tres canales y el sondeo
✅ El router declaró la vista recorrido al arrancar
✅ GET /ordenes/4006/recorrido: (…) filas, una por cada intento de la 4006
✅ El canal rest de la 4006 cuenta su historia: (…)
✅ El canal del socio de la 4006: rechazada, con su 997 de rechazo (R) en el recorrido
✅ Cada intento del recorrido trae lo que salió en él (cuerpo)
✅ La 4007 (grande): su cuerpo está entero en el recorrido, (…) bytes con su huella y sus 150 renglones
✅ El recorrido dice que la 4007 quedó COMPLETA y la 4006 no
```

Demande toi-même le parcours :

```bash
curl -s localhost:8080/ordenes/4006/recorrido | python3 -m json.tool | less
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/recorrido.sql solucion/orden-router/recorrido.sql` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `No existe la vista recorrido` | Ton `recorrido.sql` contient une erreur de SQL : le routeur le dit dans le journal, sous cette coche |
| `la vista tiene … y la 4006 tiene … intentos` | Le `LATERAL … LIMIT 1` est toujours là, ou le `JOIN` d'un accusé a multiplié les lignes |
| `El canal rest de la 4006 en el recorrido: «aceptada/manual»` sans ce qui précède | Tu ne vois que la dernière tentative : il manque le TODO 1 |
| `intentos del recorrido sin su cuerpo` | Il manque le `LEFT JOIN payload`, ou il joint sur une autre colonne : c'est `sha256` |
| `El canal del socio de la 4006 … (esperaba «rechazada R»)` | La colonne `acuses` est restée à `NULL` |
| `Completa en el recorrido` | Il manque le `LEFT JOIN completitud` |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

Si tu modifies `recorrido.sql` avec les services en marche, redémarre le routeur : la vue est déclarée au démarrage.

---

## Ce que cela enseigne au-delà du cours

**On cherche par ce qu'apporte celui qui demande.** Personne au support ne connaît l'`id` d'une opération ni le numéro de contrôle
d'un X12. Il connaît le numéro de commande. Le parcours se construit à partir de là.

**Le journal n'est pas le registre.** Le journal tronque les gros corps, se mêle à celui des autres commandes et disparaît avec le
conteneur. La base conserve chaque tentative avec ce qui est parti, en entier, tant que personne ne l'efface exprès.

**L'histoire vaut plus que l'état.** « Acceptée » dit comment cela s'est terminé ; l'histoire dit que d'abord on n'a pas su, que le
coupe-circuit ne l'a pas envoyée (la 22), que quelqu'un du poste de contrôle a fait un rerun et que l'API a répondu 201. Dans le
verifica, cela se voit ainsi : `no_se_sabe/auto no_enviado/auto aceptada/manual`. Avec cela, on
explique pourquoi elle a tardé.

**Une vue ne devient jamais périmée.** Elle ne copie pas les données : elle les rassemble de nouveau à chaque fois. C'est la façon la moins coûteuse d'avoir
une réponse fraîche sans une seconde source de vérité.

## Exercice : la commande à problème

Avec ta version terminée, lance `./verifica`, puis :

1. Demande le parcours de la 4006 et, **avec lui seul**, écris un paragraphe pour le client qui dit que sa commande « n'est pas
   arrivée ». Est-elle arrivée à l'API ? À l'ERP ? Au partenaire ? Que s'est-il passé avec le partenaire, sur quelle ligne de commande et avec quel produit ? (Indice :
   le corps de la tentative du partenaire et le 997 de la 21.)
2. Cherche la 4007 dans le journal du routeur : `DONDE=mio docker compose -f compose.yaml logs orden-router | grep 4007`. Vois-tu
   ses 150 lignes de commande ? Compare avec la colonne `tamano` du parcours.
3. Écris la requête que tu donnerais à quelqu'un du support pour voir **seulement les canaux qui ne se sont pas bien terminés** pour une
   commande, avec leur dernière issue. Commence par `SELECT canal, estado_canal, …  FROM recorrido WHERE orden = '4006'`.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Le parcours est une vue](../decisiones/30-el-recorrido-es-una-vista.md)

## Révision

1. Qu'est-ce qu'une vue, et pourquoi ne devient-elle jamais périmée ?
2. Pourquoi le parcours se cherche-t-il par le numéro de commande ?
3. Que conserve la table `payload`, et comment se joint-elle à chaque tentative ?
4. Pourquoi `LEFT JOIN` et non `JOIN` ? Pourquoi des sous-requêtes pour les accusés ?
5. **La question de clôture :** un client dit que sa commande n'est pas arrivée. Que consultes-tu d'abord, et pourquoi le parcours et
   non le journal ?

**Suite :** dans la leçon 27, regarde de nouveau le parcours de la 4008. Il contient le nom, l'adresse, le téléphone et le
courriel d'une personne. Qui devrait pouvoir les voir ?
