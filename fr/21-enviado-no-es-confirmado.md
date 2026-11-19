# Leçon 21 — Envoyé n'est pas confirmé

> **Ce que tu vas obtenir :** que le canal du partenaire ne soit pas tenu pour terminé quand le 850 est posé sur son SFTP, mais quand
> le partenaire répond par son accusé de réception, le **997**. Cet accusé arrive plus tard, dans un autre fichier et sans le numéro de commande : ton
> travail est de savoir à quel envoi il répond.

**Avant de commencer, il te faut :** les leçons 20, 09 (le fil de corrélation) et 06 (lire du X12) terminées, et
l'environnement de la 00 allumé. Cette leçon utilise un nouveau service de l'environnement, `socio-bot`, qui joue le rôle du système du
partenaire et répond par les 997. S'il n'est pas en marche :

```bash
docker compose -f infra/compose.yaml up -d --wait
```

## La nouveauté du jour, en une phrase

Avec l'API et l'ERP, la réponse arrive dans le même appel : tu demandes, on te répond, et tu sais. Avec le partenaire, non. Le
routeur dépose le 850 dans un dossier du SFTP et l'appel s'arrête là. **Que le fichier soit là dit que tu l'as
déposé, pas que le partenaire l'a lu ni qu'il l'a compris.** Le partenaire répond quand son système traite le fichier :
quelques secondes plus tard dans ce cours, des heures dans la vie réelle. Et il répond par un autre chemin, en déposant un 997 dans son dossier
`salida/`.

**L'image :** tu déposes une enveloppe dans la boîte aux lettres d'une entreprise. La boîte ne te dit pas si quelqu'un l'a ouverte. Quelques jours plus tard,
tu reçois par courrier une lettre qui dit « Nous avons reçu votre document portant la référence 100000002 et il est complet ». La lettre ne
mentionne ni ton nom ni le sujet : elle mentionne **la référence que tu as mise sur l'enveloppe**. Si tu n'as pas noté ce que
tu as envoyé sous cette référence, tu ne sais pas ce que l'on te confirme.

---

## 1. Prépare ton dossier

```bash
cd lecciones/21-enviado-no-es-confirmado
cp -R inicio mio
```

Le routeur a un nouveau fichier, `orden-router/acuses.camel.yaml`. En haut se trouve la route `sondeo-acuses`, déjà faite :
elle lit chaque `997-<algo>.edi` du dossier `salida/` du partenaire, le traduit avec son mapping JSONata et le passe à
`correlacionar-acuse`, qui est ta partie.

Avant d'écrire, **lis un 997**. Ouvre
`inicio/orden-router/mappings/edi/997/v1/cases/aceptacion/entrada.json`. C'est une seule ligne avec des `\n` entre
les segments ; une fois séparé, cela donne :

```
ISA*00*          *00*          *ZZ*SOCIO01        *ZZ*TIENDANORTE    *261113*0100*U*00401*700000001*0*P*>~
GS*FA*SOCIO01*TIENDANORTE*20261113*0100*700000001*X*004010~
ST*997*0001~
AK1*PO*100000002~
AK2*850*0001~
AK5*A~
AK9*A*1*1*1~
SE*6*0001~
GE*1*700000001~
IEA*1*700000001~
```

| Segment | Ce qu'il dit | À qui appartient le numéro ? |
|---|---|---|
| `ISA … *700000001*` | L'enveloppe du 997, avec son numéro de contrôle | **Au partenaire.** C'est lui qui l'a mis en envoyant son accusé |
| `AK1*PO*100000002` | « J'accuse réception du groupe de bons de commande (`PO`) numéro 100000002 » | **À toi.** C'est le GS06 que tu as envoyé dans le 850 |
| `AK2*850*0001` | « Dans ce groupe, le document 850 numéro 0001 » | **À toi.** C'est le ST02 du 850 |
| `AK5*A` | Le document est accepté (`A`) ou rejeté (`R`) | — |

Ouvre maintenant celui de rejet, `cases/rechazo/entrada.json`. Il contient deux segments de plus avant le `AK5*R` : `AK3*PO1*6**8`
dit que le problème se trouve dans le segment `PO1`, le sixième du document ; `AK4*7*234*7*SKU-9999` dit que l'élément 7
de ce segment contient `SKU-9999`, un produit que le partenaire ne connaît pas.

Et ouvre les deux `esperado.json` : c'est ce que le mapping extrait de chaque 997 et ce que ta route reçoit dans le corps.

```json
{"socio": {"control": "700000001"}, "grupo": {"tipo": "PO", "control": "100000002"},
 "documento": {"tipo": "850", "control": "0001"}, "acuse": "A", "errores": []}
```

## 2. Prédis

Réponds sur papier :

1. Tu as déposé le 850 de la 4001. Le partenaire l'a-t-il déjà reçu ? Comment le saurais-tu ?
2. Le 997 ne contient pas le numéro de commande. Avec quel numéro le rattacherais-tu à l'opération de la 4001 ? Cherche dans la table
   `operacion` de `infra/postgres/01-estado.sql` quelle colonne conserve ce numéro, et depuis quelle leçon.
3. Un 997 arrive qui cite un groupe que tu n'as jamais envoyé. Que devrait-il lui arriver ?

Regarde l'état actuel :

```bash
./verifica inicio
```

La première coche passe : le 850 est déposé et sans accusé. Ensuite, le partenaire répond, le routeur lit le 997… et n'en
fait rien. Le canal du partenaire reste en `aceptada` pour toujours, alors que le partenaire a déjà accusé réception, et personne n'apprend le rejet de la
4006.

## 3. Corréle l'accusé de réception

Ouvre `mio/orden-router/acuses.camel.yaml` et cherche la route `correlacionar-acuse`. Aujourd'hui, elle se contente d'écrire dans le journal. Elle a
quatre TODO, et tous sauf le dernier tiennent dans **une seule instruction SQL** :

| TODO | Ce qu'il fait | Pour quoi faire |
|---|---|---|
| 1 | Note l'accusé dans la table `acuse`, **qu'il corresponde ou non** à quelque chose | Qu'aucun accusé ne se perde |
| 2 | Cherche l'opération du partenaire qui attendait cet accusé | La corrélation : la seule difficulté d'aujourd'hui |
| 3 | La passe à `confirmada` (AK5 = A) ou `rechazada` (AK5 = R), et conserve dans la ligne de l'accusé à quelle opération il correspondait | Que le canal ne se termine qu'avec l'accusé |
| 4 | S'il ne correspond à aucune, le signale avec un `WARN` qui dise `<archivo> no corresponde` | Que quelqu'un voie l'accusé sans propriétaire |

La forme de l'instruction, nous te la donnons. D'abord un `UPDATE … RETURNING` à l'intérieur d'un `WITH`, qui change
l'opération et renvoie celle qui a changé ; ensuite l'`INSERT` de l'accusé, qui utilise ce qui a été renvoyé :

```yaml
- to:
    uri: >-
      sql:WITH op AS (
        UPDATE operacion SET estado = CASE WHEN CAST(:#${body[acuse]} AS text) = 'R' THEN 'rechazada' ELSE 'confirmada' END
        WHERE canal = 'edi' AND estado = 'aceptada'
          AND …                               -- TODO 2: tus condiciones
        RETURNING id, orden, momento, estado)
      INSERT INTO acuse (archivo, control_socio, grupo_tipo, grupo_control, documento_tipo, documento_control,
                         codigo, errores, operacion)
      SELECT :#${variable.archivo}, :#${body[socio][control]}, :#${body[grupo][tipo]}, :#${body[grupo][control]},
             :#${body[documento][tipo]}, :#${body[documento][control]}, :#${body[acuse]},
             CAST(:#${variable.leido} AS jsonb) -> 'errores', (SELECT id FROM op)
      RETURNING COALESCE((SELECT orden || ' ' || momento || ' → ' || estado FROM op), '') AS resultado
    variableReceive: correlacion997
    parameters:
      dataSource: "#estado"
      outputType: SelectOne
```

Si l'`UPDATE` ne trouve rien, `op` reste vide : l'accusé est noté quand même, avec `operacion` à `NULL`, et `resultado`
sort comme un texte vide. C'est ce qu'utilise le TODO 4.

**Ton travail, ce sont les conditions du TODO 2.** L'opération qui attend cet accusé est celle qui remplit les quatre :

| Condition | En SQL | Pourquoi |
|---|---|---|
| Son numéro de contrôle est celui du groupe que cite l'AK1 | `numero_control = CAST(:#${body[grupo][control]} AS text)` | C'est le numéro que **tu** as mis dans le GS06 depuis la 15 |
| Elle porte sur l'événement du cycle de vie qui correspond au document | `momento = CASE CAST(:#${body[documento][tipo]} AS text) WHEN '850' THEN 'creada' WHEN '856' THEN 'embarcada' WHEN '214' THEN 'entregada' END` | Un 850 accuse la création ; un 856, l'expédition |
| Le type du groupe concorde avec le document | `PO` va avec `850`, `SH` avec `856` et `QM` avec `214` | Un accusé mal construit ne doit rien confirmer |
| C'est le premier document du groupe | `CAST(:#${body[documento][control]} AS text) = '0001'` | Chacun de nos groupes porte un seul document, avec le ST02 `0001` |

Écris la troisième toi-même, avec un `CASE` comme celui de la deuxième.

Pour le TODO 4, un `choice` : si `${variable.correlacion997} == ''`, un `log` en `WARN` avec le message
`El 997 ${variable.archivo} no corresponde a ninguna operación que esperara su acuse` ; sinon, un `log` normal qui
dise quelle commande il a confirmée.

Remarque ce que tu n'utilises **pas** : l'`ISA13` du 997, `${body[socio][control]}`. Il est conservé dans `control_socio`, mais il ne
sert pas à corréler : c'est le numéro que le partenaire a mis **sur son enveloppe**, pas le tien.

## 4. Casses-en une exprès

Une fois tout terminé, change la première condition pour qu'elle compare avec le numéro du partenaire :
`numero_control = CAST(:#${body[socio][control]} AS text)`. Lance `./verifica`.

Avant cela, prédis : qu'arrive-t-il à la 4001 quand son 997 arrive ?

Elle reste en `aceptada`. Le 997 est noté dans la table `acuse` sans opération, et dans le journal apparaît ton `WARN` « no
corresponde ». La coche de la confirmation sort en rouge. L'ISA du partenaire (`7000000…`) ne coïncidera jamais avec ton
numéro (`1000000…`) : ce sont deux numérotations de deux systèmes différents. Si tu ne l'avais pas noté comme accusé sans propriétaire,
il se serait perdu en silence. Remets le `grupo`.

## 5. Vérifie

```bash
./verifica
```

`verifica` envoie la 4001 et attend son accusé ; puis la 4006, qui demande un produit que le partenaire ne connaît pas ; et, à la fin,
il dépose sur le SFTP un 997 qui cite un groupe que nous n'avons jamais envoyé. Elle est terminée quand tu vois les sept coches vertes :

```
✅ Arrancaron el router (con el sondeo de los acuses), los tres canales y el sondeo de la bodega
✅ El 850 de la 4001 quedó depositado («aceptada») y todavía no hay acuse: enviado no es confirmado
✅ Llegó el 997 de aceptación: el canal pasó a «confirmada», correlacionado por el GS06 (…) y el ST02, no por el ISA del socio
✅ El 997 que leyó el router pasa el lector X12, y su AK1 cita el grupo (…)
✅ La API y el ERP no esperan un acuse: se quedan en «aceptada» (bd=aceptada rest=aceptada)
✅ La 4006: el 997 de rechazo dejó el canal en «rechazada», con el error que señaló el socio (PO1, SKU-9999)
✅ Un 997 que cita un grupo que no mandamos quedó anotado sin operación, con su WARN, y no confirmó nada más
```

Ouvre le 997 qui a confirmé la 4001 : le verifica le copie dans `mio/salida/997-<número>.edi`. Cherche ton numéro dans l'`AK1` et
celui du partenaire dans l'`ISA`.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/acuses.camel.yaml solucion/orden-router/acuses.camel.yaml` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `Esperaba la 4001 confirmada` et l'accusé est « nada » | Il manque l'`INSERT INTO acuse`, ou l'instruction ne porte pas `dataSource: "#estado"` |
| `Esperaba la 4001 confirmada` et il y a bien un accusé | L'`UPDATE` n'a pas trouvé l'opération : vérifie que tu compares avec `body[grupo][control]` et que le `CASE` de l'événement est complet |
| `La 4006: esperaba rechazada` et elle reste `aceptada` | Le `SET estado` ne lit pas `body[acuse]`, ou ta condition sur le type de groupe ne laisse pas passer `PO` avec `850` |
| `El 997 sin dueño` et il y a 0 accusé sans opération | L'accusé sans propriétaire n'a pas été noté : l'`INSERT` doit s'exécuter même si l'`UPDATE` ne change rien |
| `El 997 sin dueño` et le WARN manque | Le `choice` du TODO 4 ne compare pas avec `''`, ou le `log` ne porte pas `loggingLevel: WARN` |
| `No está arriba socio-bot` | Démarre l'environnement comme l'indique « Avant de commencer » |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**Déposer n'est pas livrer, et livrer ne veut pas dire que l'on a compris.** Un fichier sur un SFTP peut y rester sans que
personne le traite. En EDI, le 997 est la manière standard pour l'autre de dire « je l'ai reçu et j'ai pu le lire », ou « je l'ai reçu et
il est faux à tel segment ».

**Une réponse asynchrone se corrèle par ce que tu as envoyé.** Le 997 ne sait rien des commandes : il connaît le numéro de
contrôle que portait ton fichier. C'est pour cela que, depuis la 15, le routeur attribue un numéro unique par document et le conserve. Sans
ce numéro conservé, l'accusé n'aurait personne vers qui pointer.

**Chaque système numérote ce qui est à lui.** L'ISA du 997 appartient au partenaire ; le GS06 qu'il cite est le tien. Les confondre est l'erreur la plus
courante quand on corrèle, et elle n'échoue pas : simplement, elle ne trouve rien.

**Ce qui ne correspond pas se note et se signale.** Un accusé sans propriétaire peut être une erreur du partenaire, un vieil envoi ou un
test. Le jeter le cache ; le noter permet à quelqu'un d'enquêter.

## Exercice : deux accusés, deux numéros

1. Sur papier, avec les deux `entrada.json` des cas de test de référence, réponds pour chaque 997 : quel numéro de contrôle cite
   l'AK1 ? Quel est celui de l'ISA ? Accepte-t-il ou rejette-t-il ? S'il rejette, quel segment et quelle valeur désigne-t-il ?
2. Après avoir lancé `./verifica`, consulte la table des accusés :
   `SELECT archivo, control_socio, grupo_control, codigo, operacion FROM acuse ORDER BY id;`
   Laquelle des trois lignes n'a pas d'opération, et pourquoi ?
3. Des trois canaux du cours, lequel se termine en `aceptada` et lequel en `confirmada` ? Écris une phrase pour
   chacun, qui explique pourquoi c'est là sa fin.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Le 997 se corrèle par le groupe et le document, et lui seul confirme](../decisiones/25-el-997-se-correlaciona-por-grupo-y-documento.md)

## Révision

1. Qu'est-ce qu'un 997, et qui l'envoie ?
2. Que cite l'AK1 et que cite l'AK2 ?
3. Pourquoi l'ISA13 du 997 ne sert-il pas à corréler ?
4. Qu'arrive-t-il à un 997 qui ne correspond à aucune opération ? Pourquoi ne le jette-t-on pas ?
5. **La question de clôture :** « déposé » et « confirmé » ne sont pas la même chose. Quel canal du cours attend lequel, et
   pourquoi l'API et l'ERP n'attendent-ils pas d'accusé ?

**Suite :** dans la leçon 22, le SFTP du partenaire tombe, et avec les nouvelles tentatives de la 19 cela arrête aussi l'API
et l'ERP. Comment cesses-tu d'appeler une destination en panne sans arrêter les autres ?
