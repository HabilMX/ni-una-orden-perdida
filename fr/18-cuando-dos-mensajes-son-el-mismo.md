# Leçon 18 — Quand deux messages sont-ils le même ?

> **Ce que vous allez obtenir :** que le routeur reconnaisse un avis qu'il a déjà reçu, même s'il arrive écrit différemment, et ne
> le distribue pas une nouvelle fois. Et qu'il distingue ce doublon d'un avis qui porte la même clé avec un autre contenu : cela, ce n'est
> pas un doublon, c'est un conflit, et on le signale.

**Avant de commencer, il vous faut :** les leçons 15 et 16 terminées et l'environnement de la 00 allumé. Cette leçon
utilise une nouvelle table de la base d'état, `conflicto`. Si votre base date d'avant, recréez-la une fois :

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V estado
```

## La nouveauté du jour, en une phrase

Dans la 16, vous avez appris que celui qui envoie un webhook **retente** s'il ne reçoit pas son 2xx à temps. Cela a une
conséquence : tôt ou tard, le même avis arrive deux fois. Aujourd'hui, le routeur apprend à le reconnaître. C'est ce qu'on
appelle un *Idempotent Consumer* : recevoir deux fois la même chose laisse le même résultat que la recevoir une fois.

**L'image :** la loge du gardien à l'entrée d'un immeuble, avec son registre des visites. La dame du 302 arrive à
10:00 et on l'inscrit. À 10:05, elle revient parce qu'elle a oublié son badge : on ne l'inscrit pas une seconde fois, elle est déjà dans le registre. Mais
si quelqu'un arrive en disant être la dame du 302 et ne ressemble pas à celle de la photo, le gardien ne la laisse pas passer et ne
l'inscrit pas par-dessus : il appelle la personne qui habite là. Le premier cas est un **doublon** ; le second, un **conflit**.

---

## 1. Préparez votre dossier

```bash
cd lecciones/18-cuando-dos-mensajes-son-el-mismo
cp -R inicio mio
```

Le routeur a un nouveau fichier, `orden-router/reconocer.camel.yaml`. Ouvrez-le et lisez le commentaire du haut. Il dit
avec quoi l'on reconnaît un message :

| Notion | Ce que c'est | Dans le cours |
|---|---|---|
| **Clé** | Ce qui dit **de quoi** parle le message | `orden-<orden>-<momento>`, par exemple `orden-4001-entregada`. Chaque canal la porte une fois : c'est la `UNIQUE (orden, momento, canal)` de `operacion` |
| **Empreinte** | Ce qui dit **ce que contient** le message | Le sha256 du corps, une fois normalisé. Deux corps de même contenu ont la même empreinte |

Avec ces deux pièces, on obtient trois cas :

| Cas | Clé | Empreinte | Ce que répond le routeur | Ce qu'il fait |
|---|---|---|---|---|
| **nouveau** | Il n'y a pas d'opérations avec cette clé | — | 202 « `recibida` » (reçue) | Il enregistre et distribue, comme jusqu'à la 17 |
| **doublon** | Elle existe déjà | La même | 202 « `repetido` » (doublon) | Rien : il l'avait déjà |
| **conflit** | Elle existe déjà | Une autre | 409 « `conflicto` » (conflit) | Il le note dans la table `conflicto`, le signale dans le journal et n'écrase pas ce qui est enregistré |

En bas du fichier se trouve la route `recibir-momento`, qui utilise déjà cette décision : elle lit `${variable.visto}` et répond ou
enregistre selon le cas. Votre travail est la route qui remplit cette variable, `reconocer`.

## 2. Prédisez

Répondez sur papier :

1. Le transporteur signale deux fois la livraison de la 4001, avec le même contenu. Combien de 214 attendez-vous sur le SFTP du
   partenaire ? Combien d'événements dans l'API ?
2. Le second avis contient la même chose, mais sur une seule ligne et avec les champs dans un autre ordre. Est-ce le même message ?
3. Un troisième avis de la 4001 arrive avec la même clé et **un autre numéro de suivi**. Que devrait-il arriver au numéro de suivi qu'ont déjà
   l'API et l'ERP ?

Regardez l'état actuel :

```bash
./verifica inicio
```

Le premier avis passe. Le second **répond par une erreur** : la route de `inicio/` voit tout comme nouveau, tente d'enregistrer les
opérations une seconde fois et la `UNIQUE` de `operacion` l'arrête. Rien n'a été dupliqué, mais le transporteur reçoit une erreur
pour quelque chose que nous avions bel et bien, et comme vous l'avez appris dans la 16, un émetteur qui reçoit une erreur **renvoie**. Et l'avis
avec un autre numéro de suivi ne se distingue pas non plus : c'est une erreur de plus.

## 3. Reconnaissez ce qui arrive

Ouvrez `mio/orden-router/reconocer.camel.yaml` et cherchez la route `reconocer`. Aujourd'hui, elle se contente de mettre `visto` à `nuevo`. Elle a
quatre TODO :

| TODO | Ce que vous écrivez | Pour quoi faire |
|---|---|---|
| 1 | Un `to` avec `sql:` qui renvoie deux colonnes : `nueva`, l'empreinte de ce qui est arrivé, et `guardada`, l'empreinte de la première opération avec cette commande et cet événement du cycle de vie | Comparer ce qui est arrivé avec ce que vous avez déjà, en une seule requête |
| 2 | Un `setVariable` `huella` avec la nouvelle empreinte | `guardar-momento` l'écrit dans chaque opération, pour comparer la prochaine fois |
| 3 | Un `choice` à trois branches qui met `visto` à `nuevo`, `repetido` ou `conflicto` | La décision que lit `recibir-momento` |
| 4 | Dans la branche du conflit : un `INSERT INTO conflicto` et un `log` en `WARN` qui commence par `CONFLICTO orden-<orden>-<momento>` | Que le conflit soit écrit et que quelqu'un le voie |

Quatre indices :

- La requête du TODO 1 est presque complète dans le commentaire. Elle porte `dataSource: "#estado"`, `outputType: SelectOne`
  et `variableReceive: huellas`. Avec cela, `${variable.huellas[nueva]}` et `${variable.huellas[guardada]}` sont les deux
  empreintes.
- Pour `guardada`, une sous-requête : `(SELECT huella FROM operacion WHERE orden = … AND momento = … ORDER BY id LIMIT 1)`. S'il n'y a pas d'opérations, elle renvoie `NULL`.
- Le `choice` compare ainsi : `${variable.huellas[guardada]} == null` est nouveau ; `${variable.huellas[guardada]} == ${variable.huella}` est un doublon ; le reste (`otherwise`) est un conflit.
- La table `conflicto` se trouve dans `infra/postgres/01-estado.sql`. Ses colonnes sont `llave`, `orden`, `momento`,
  `huella_guardada`, `huella_nueva`, `cuerpo` (en `jsonb`, comme dans la 15) et `correlacion`.

Regardez la ligne de l'empreinte :

```sql
encode(sha256(convert_to(CAST(CAST(:#${body} AS jsonb) AS text), 'UTF8')), 'hex')
```

Elle se lit de l'intérieur vers l'extérieur : le corps est converti en `jsonb`, le `jsonb` devient du texte, le texte devient
des octets, on calcule le sha256 des octets et le résultat s'écrit en hexadécimal. L'étape qui compte est la
première. **PostgreSQL conserve le `jsonb` sans les espaces superflus et avec les clés dans un ordre qui lui est propre** : deux
corps de même contenu, écrits différemment, sortent donc identiques une fois redevenus texte. Sans ce `CAST`, l'empreinte serait
celle du texte tel qu'il est arrivé.

## 4. Cassez-en une exprès

Avec votre `reconocer` terminé, retirez le `CAST(… AS jsonb)` de l'intérieur : laissez `convert_to(:#${body}, 'UTF8')`. Lancez
`./verifica`.

Avant cela, prédisez : le second avis du verifica contient le même contenu sur une seule ligne et avec les champs à l'envers.
Comment allez-vous le reconnaître ?

La coche des deux avis sort en rouge : le routeur a répondu 409 au second. L'empreinte a été calculée sur le texte, et le texte a changé alors que le
contenu, non. **Vous avez pris un renvoi pour une modification**, et quelqu'un devra examiner un conflit qui n'existe pas.
Remettez le `CAST`.

## 5. Vérifiez

```bash
./verifica
```

`verifica` crée et expédie la 4001, joue le rôle du transporteur avec son avis de livraison, l'envoie une seconde fois écrit différemment et,
à la fin, en envoie un avec un autre numéro de suivi. Elle est terminée quand vous voyez les neuf coches vertes :

```
✅ Arrancaron el router (con su reconocedor de repetidos), los tres canales y el sondeo
✅ Antes de los avisos: la 4001 está EMBARCADO
✅ Los dos avisos contestaron 202, y el segundo se reconoció como repetido aunque viniera escrito distinto
✅ Estado: la entrega tiene 3 operaciones con su huella sha256 y 3 intentos, uno por canal
✅ Destinos: un solo 201 de la entrega en la API, un solo 214 en el socio y el ERP en ENTREGADO
✅ Un aviso con la misma llave y otra guía contestó 409 «conflicto»
✅ Estado: el conflicto quedó en su tabla, con la llave, lo que llegó y las dos huellas distintas
✅ Log: el router avisó el conflicto con un WARN
✅ Nada se pisó: la orden guarda la guía original, sigue habiendo 3 intentos y la API tiene la guía con la que salió
```

Regardez la table du conflit :

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT llave, cuerpo->>'guia' AS guia, huella_guardada <> huella_nueva AS distintas FROM conflicto"
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/reconocer.camel.yaml solucion/orden-router/reconocer.camel.yaml` vous dit en quoi elles
diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `Esperaba 202 y 202 «repetido»` et le second a répondu 500 | Votre `choice` n'est pas arrivé à mettre `repetido` : vérifiez que la requête porte `variableReceive: huellas` et que `guardada` cherche par commande **et** par événement |
| `Esperaba 202 y 202 «repetido»` et le second a répondu 409 | L'empreinte est calculée sur le texte : il manque le `CAST(:#${body} AS jsonb)` de l'intérieur |
| `esperaba 3 operaciones con huella` et il y a 0 empreinte | Il manque le TODO 2 : la variable `huella` n'existe pas quand `guardar-momento` l'écrit |
| `Esperaba 409 para el aviso con otra guía` | La branche du conflit ne met pas `visto` à `conflicto`, ou le `choice` la prend pour nouveau |
| `esperaba una fila en conflicto` | Il manque l'`INSERT INTO conflicto`, ou il ne porte pas `dataSource: "#estado"` |
| `no hay un WARN «CONFLICTO orden-4001-entregada»` | Le `log` ne porte pas `loggingLevel: WARN`, ou le message ne commence pas par `CONFLICTO orden-${variable.orden}-${variable.momento}` |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**Concevez comme si un message pouvait arriver plus d'une fois.** Dans beaucoup de flux avec nouvelles tentatives, comme celui-ci, on garantit
qu'il arrive « au moins une fois », pas « exactement une fois » : les nouvelles tentatives protègent contre la perte et, en échange, apportent
des doublons. Celui qui reçoit se charge de les reconnaître. C'est pour cela que presque toute API sérieuse de
paiement ou d'expédition demande une clé d'idempotence.

**La clé vient du métier, pas du hasard.** Un nombre au hasard, nouveau à chaque envoi, ferait passer chaque renvoi pour
un autre message. `orden-4001-entregada` est la même chaque fois que le transporteur renvoie, et c'est pour cela qu'elle sert.

**La clé et l'empreinte répondent à des questions différentes.** La clé dit si vous avez déjà vu quelque chose **à ce sujet** ; l'empreinte, si c'est **la
même chose**. Avec la clé seule, une modification passe pour un doublon et se perd. Avec l'empreinte seule, le même avis avec une autre
date d'envoi passerait pour nouveau.

**Un conflit n'est pas résolu par le programme.** Garder le premier ou le dernier en silence reviendrait à décider à la place de
quelqu'un. On le note, on le signale, et c'est une personne qui le résout.

## Exercice : le registre de la loge

Avec votre version terminée :

1. Écrivez sur papier la clé de ces trois messages : l'expédition de la 4002, la livraison de la 4002 et la livraison de la
   4003. Pourquoi un nombre au hasard que le routeur générerait à la réception ne convient-il pas comme clé ?
2. Calculez à la main, avec `psql`, l'empreinte de `{"orden":"4001","guia":"X"}` et de `{ "guia": "X", "orden": "4001" }`, avec et
   sans le `CAST` vers `jsonb`. Utilisez `SELECT encode(sha256(convert_to(CAST('…' AS jsonb)::text, 'UTF8')), 'hex')`. Combien
   d'empreintes différentes obtenez-vous dans chaque cas ?
3. Dans l'avis du transporteur, quel champ pourrait changer d'un renvoi à l'autre sans que change ce qui s'est passé ? (Indice :
   pensez au moment où l'avis a été **envoyé**, pas à celui de la livraison.) Qu'arriverait-il à l'empreinte ? Écrivez votre
   réponse : cette leçon ne la résout pas.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Un message se reconnaît à sa clé, et son contenu à son empreinte](../decisiones/22-la-llave-y-la-huella.md)

## Révision

1. Qu'est-ce qu'un Idempotent Consumer ? Dites-le avec l'exemple de la loge.
2. Quelle est la clé d'un événement du cycle de vie dans ce cours, et d'où vient-elle ?
3. Qu'est-ce que l'empreinte, et pourquoi se calcule-t-elle après avoir converti le corps en `jsonb` ?
4. Pourquoi répond-on 202 au doublon et non une erreur ? (Revenez à la 16.)
5. **La question de clôture :** un doublon et un conflit ont la même clé. Qu'est-ce qui les distingue, que répond le
   routeur à chacun et pourquoi le conflit n'écrase-t-il pas ce qui est enregistré ?

**Suite :** dans la leçon 19, le problème est inverse. Cela n'arrive pas deux fois : cela n'arrive pas. L'API ne répond pas, et le
routeur doit réessayer sans mettre en désordre les événements de la commande.
