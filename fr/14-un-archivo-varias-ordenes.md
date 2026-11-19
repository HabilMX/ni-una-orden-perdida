# Leçon 14 — Un fichier, plusieurs commandes

> **Ce que tu vas obtenir :** découper le fichier que l'entrepôt dépose avec plusieurs expéditions, de sorte que chaque commande reçoive
> son propre 856 et son statut EMBARCADO, et qu'une mauvaise expédition n'arrête pas les autres.

**Avant de commencer, il te faut :** la leçon 13 terminée et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Dans la 13, l'entrepôt déposait un fichier par expédition. Dans la vie réelle, ce n'est presque jamais le cas : l'entrepôt rassemble ce qui est parti
dans la matinée et dépose **un seul fichier, un lot**, avec cinq expéditions dedans. Aujourd'hui, tu le découpes en ses parties, une par
commande. Le patron s'appelle *Splitter*.

**L'image :** la caisse qui arrive au bureau avec le courrier de tout l'étage. Personne ne remet la caisse entière
au premier bureau : on l'ouvre, on sort chaque enveloppe et on la porte à son destinataire. Et si une enveloppe porte un nom que
personne ne connaît, on l'écarte et on distribue les autres.

---

## 1. Prépare ton dossier

```bash
cd lecciones/14-un-archivo-varias-ordenes
cp -R inicio mio
```

Ouvre le bon lot, `datos/embarques/lote-bodega-0001.json`. En haut, il contient les données du lot (numéro, entrepôt,
date de génération), puis une liste, `embarques`, de cinq éléments. Chaque élément est une expédition comme celle de la
13 : commande, transporteur, numéro de suivi et lignes de commande.

Le mauvais lot est `lote-bodega-0002.json` : cinq expéditions, dont la troisième concerne la commande **9999**, que la boutique n'a jamais
créée. Le routeur ne l'acceptera pas.

## 2. Prédis

Avant d'ouvrir la route, réponds sur papier :

1. Le lot 0001 contient cinq expéditions. Combien de 856 attends-tu sur le SFTP du partenaire ?
2. Dans le lot 0002, la troisième expédition échoue. Qu'arrive-t-il à la quatrième et à la cinquième ? Et à la première et à la deuxième,
   qui sont déjà passées ?
3. À la fin du lot 0002, où le fichier devrait-il aller : dans `.camel/`, dans `.error/` ou dans aucun des deux ?

Regarde l'état actuel :

```bash
./verifica inicio
```

Aujourd'hui, la scrutation envoie au routeur **le lot complet comme s'il s'agissait d'une expédition**. Le routeur cherche une seule commande dans
le corps, ne la trouve pas et le rejette. Les coches de l'API, de l'ERP et du partenaire sortent en rouge.

## 3. Ce que tu vas écrire

Ouvre `mio/sondeo-bodega/sondeo-bodega.camel.yaml`. Le `from` porte déjà les quatre précautions de la 13 ; seul a changé
l'`include`, qui prend maintenant les `lote-bodega-<algo>.json`. Ton travail se trouve dans les trois TODO des étapes :

| TODO | Ce que tu fais | Pour quoi faire |
|---|---|---|
| 1 | Enveloppe les étapes dans un `split` avec l'expression JSONPath `$.embarques[*]` | Un message pour chaque expédition de la liste |
| 2 | Écris `stopOnException: false` dans le `split` | Qu'une mauvaise expédition n'arrête pas les autres |
| 3 | Déplace à l'intérieur du `split` les étapes qui conservent la commande et appellent le routeur, et ajoute un `marshal` vers JSON avant l'appel | Que chaque partie voyage seule vers le routeur |

À l'intérieur du `split`, les étapes vont dans leur propre `steps:`, avec plus d'indentation. Voici le squelette ; les étapes
de l'intérieur sont celles qui sont déjà dans le fichier :

```yaml
- split:
    jsonpath:
      expression: "$.embarques[*]"
    stopOnException: false
    steps:
      - setVariable: …   # la orden de esta parte
      - marshal:
          json: {}
      - …                # quitar encabezados, Content-Type, llamar al router, anotar
```

Deux nouveautés :

- **Chaque partie arrive sous forme de données, pas de texte.** JSONPath te remet chaque expédition comme une map (des clés et des
  valeurs). C'est pour cela que la commande ne s'extrait plus avec `$.orden` mais avec `${body[orden]}`, et qu'avant de l'envoyer au routeur
  il faut la retransformer en texte JSON avec `marshal`. Sans le `marshal`, le routeur reçoit quelque chose qui n'est pas du JSON.
- **`stopOnException: false` est déjà la valeur par défaut.** Tu l'écris quand même : celui qui lit la route voit qu'on y a pensé, pas
  qu'on l'a oublié.

## 4. Casses-en une exprès

Avec ton `split` terminé, passe `stopOnException` à `true` et lance `./verifica`. Avant cela, prédis : combien des
quatre bonnes expéditions du lot 0002 arrivent ?

La coche du lot 0002 sort en rouge : seules deux arrivent, la 4001 et la 4002. Le `split` avance dans l'ordre ; quand la
troisième (la 9999) échoue, il s'arrête, et la 4004 et la 4005 restent sans avis, alors qu'elles sont bien sorties de l'entrepôt. À cause d'une erreur
sur une commande qui n'existe même pas, deux clients ne savent pas où est leur colis. Reviens à `false`.

## 5. Vérifie

```bash
./verifica
```

`verifica` crée les commandes 4001 à 4005, dépose le lot 0001 dans la boîte aux lettres et contrôle ; puis il nettoie les destinations et dépose
le lot 0002. Elle est terminée quand tu vois les sept coches vertes :

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Lote 0001: las 5 órdenes quedaron EMBARCADO en la API y en el ERP
✅ Socio: 5 archivos 856, uno por orden, y los 5 pasan el lector X12
✅ Buzón: el lote 0001 se apartó a .camel/
✅ Lote 0002: los otros 4 embarques pasaron (4001, 4002, 4004 y 4005 EMBARCADO en la API y el ERP)
✅ Sondeo: anotó en su bitácora que el embarque de la 9999 no pasó
✅ Buzón: el lote 0002 se apartó a .error/ para que alguien vea el embarque malo
```

Regarde les deux dernières : le lot 0002 a fini dans `.error/` **alors que quatre de ses cinq expéditions sont passées**. Le
`split` a continué avec les autres, mais à la fin il se souvient qu'il y a eu une erreur, et la scrutation traite le fichier comme en échec.
Le fichier dans `.error/` dit « regarde celui-ci » ; le journal dit lequel :

```bash
DONDE=mio docker compose -f compose.yaml logs sondeo-bodega | grep "no aceptó"
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/sondeo-bodega solucion/sondeo-bodega` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `solo 0 de 5 órdenes quedaron EMBARCADO` | Les étapes qui appellent le routeur sont restées hors du `split`, ou le `split` manque. Vérifie l'indentation : elles doivent se trouver dans son `steps:` |
| Le journal dit que le routeur n'a accepté aucune expédition | Il manque le `marshal` vers JSON : le routeur reçoit une map, pas du texte |
| `el malo detuvo a los demás` | `stopOnException` est resté à `true` |
| `Al ERP le falta la tabla embarque` | Recrée l'ERP comme l'indique la 13 |

---

## Ce que cela enseigne au-delà du cours

**Les lots sont la règle, pas l'exception.** Les banques, les entrepôts et les vieux systèmes remettent leur travail dans de gros
fichiers, une fois par jour ou toutes les heures. Découper le fichier et traiter chaque partie comme si elle était arrivée seule est la
première chose que fait une intégration avec eux.

**Un lot n'est pas une transaction.** Dans la 05, tu as appris le « tout ou rien » à l'intérieur de l'ERP. Ici, c'est l'inverse : chaque expédition
concerne un client différent, et que l'une échoue n'est pas une raison pour ne pas prévenir les quatre autres. Ce qui convient, tout ou
rien ou une par une, dépend de ce que les parties dépendent ou non les unes des autres.

## Exercice : le lot avec son erreur

Avec ta version terminée :

1. Ouvre `buzon/.error/lote-bodega-0002.json`. Si tu étais la personne qui s'occupe de l'entrepôt, comment saurais-tu laquelle des cinq
   expéditions revoir ? Le fichier te suffit-il, ou as-tu besoin du journal ?
2. Relance `./verifica` sans rien changer. Un 856 ou un EMBARCADO a-t-il été dupliqué ? Pourquoi non ? (Indice : le
   verifica nettoie les destinations avant chaque lot, et l'API reconnaît la clé de chaque expédition.)
3. Imagine que l'entrepôt corrige la 9999 et renvoie le lot 0002 complet. Qu'arriverait-il aux quatre
   commandes qui étaient déjà EMBARCADO ? Écris ta réponse : le sujet « quand deux messages sont-ils le même ? » est celui de la
   leçon 18.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Un fichier de l'entrepôt se découpe en ses expéditions, et une mauvaise n'arrête pas les autres](../decisiones/17-partir-el-archivo.md)

## Révision

1. Que fait un Splitter, et pourquoi est-il nécessaire avec un lot de l'entrepôt ?
2. Pourquoi, à l'intérieur du `split`, la commande se lit-elle avec `${body[orden]}` et non avec `$.orden` ?
3. À quoi sert le `marshal` avant d'appeler le routeur ?
4. Que fait `stopOnException: false` ? Pourquoi l'écrit-on si c'est déjà la valeur par défaut ?
5. **La question de clôture :** le lot 0002 a fini dans `.error/` alors que quatre expéditions sont bien passées. Est-ce un
   problème ? Que te dit ce fichier, et que te dit le journal ?

**Suite :** dans la leçon 15, la question de clôture de la 12 revient : si le routeur a des répliques, où vit ce qui concerne
chaque commande ? Aujourd'hui, le routeur ne se souvient de rien ; demain, il conserve chaque commande, chaque canal et chaque tentative dans sa propre base.
