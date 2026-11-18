# Leçon 23 — Retraiter ce qui a échoué

> **Ce que vous allez obtenir :** une porte dans le routeur, `POST /rerun`, pour qu'une personne renvoie **seulement**
> l'étape qui est restée bloquée, sans rien dupliquer dans les destinations et en laissant écrit qui l'a demandé, pourquoi et avec quelle
> version c'est parti.

**Avant de commencer, il vous faut :** les leçons 22 et 18 terminées, et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Dans la 20, vous avez laissé en `conciliacion_pendiente` ce dont on n'a pas pu connaître l'issue, et dans la 22, en
`pendiente`, ce que le coupe-circuit n'a pas envoyé. Les deux attendent la même chose : que quelqu'un corrige la cause et le renvoie. Jusqu'à aujourd'hui, ce
quelqu'un n'avait aucun moyen de le faire. Le **rerun** est cet outil : une tentative de plus de la même opération, demandée par une personne
et notée comme telle.

**L'image :** le comptoir des réclamations d'un transporteur. Vous apportez votre numéro de suivi et vous dites « ce colis est resté à
l'entrepôt à cause de la tempête ». On ne vous renvoie pas tout ce que vous avez expédié ce jour-là : on cherche **ce** colis, on vérifie qu'il est vraiment
bloqué et on le réexpédie. Dans le système, il reste noté que c'est le comptoir qui l'a renvoyé, à votre demande, à cause de
la tempête. Si vous apportez le numéro de suivi d'un colis déjà livré, on vous dit non.

---

## 1. Préparez votre dossier

```bash
cd lecciones/23-reprocesar-lo-que-fallo
cp -R inicio mio
```

Le routeur a un nouveau fichier, `orden-router/rerun.camel.yaml`, avec une seule route : `rerun`, qui écoute sur
`POST /rerun`. Elle reçoit ceci :

```json
{"orden": "4001", "momento": "creada", "canal": "edi",
 "pedido_por": "ana.soporte", "motivo": "el SFTP del socio estuvo caído"}
```

Ouvrez-le et lisez le commentaire du haut. La version d'`inicio/` **fonctionne, mais ne suffit pas**. Elle marque l'opération
comme `pendiente` et la remet dans la distribution. Cela pose trois problèmes :

| Problème | Pourquoi cela compte |
|---|---|
| Elle part comme une tentative `auto`, sans nom ni motif | Demain, personne ne sait qu'une personne l'a demandée, ni pourquoi |
| Elle rouvre ce qui s'était déjà bien passé, et le renvoie | Un rerun de quelque chose d'accepté rappelle la destination |
| Elle répond 202 « devuelta al reparto » | Celui qui l'a demandé ne sait pas comment cela s'est passé |

Et il y a quelque chose que vous n'avez **pas** à construire : la table `intento` a déjà les colonnes `tipo`, `pedido_por` et
`motivo`, et l'`INSERT` de la route `intentar` (dans `reparto.camel.yaml`) les remplit déjà avec trois variables. Si elles
n'existent pas, `tipo` reste à `auto`.

## 2. Prédisez

Répondez sur papier :

1. Le SFTP du partenaire revient. Si vous retraitez **la commande complète** (les trois canaux) de la 4001, qu'arrive-t-il à l'API
   et à l'ERP, qui l'avaient déjà ?
2. L'API a tardé et le canal a coupé : la 4005 est restée sans que l'on sache comment cela s'est passé. Vous faites le rerun. Une autre commande est-elle créée dans l'API ?
   (Indice : la 02 et la 18.)
3. Quelqu'un demande un rerun d'une opération qui est déjà `aceptada`. Que devrait répondre le routeur ?

Regardez l'état actuel :

```bash
./verifica inicio
```

Les retraitements partent, et c'est pour cela que plusieurs coches passent. Mais ils partent comme des tentatives `auto`, le rerun de ce qui était déjà
accepté rappelle la destination au lieu de refuser, et un rerun sans motif est accepté quand même.

## 3. Une tentative manuelle, avec un nom et un motif

Ouvrez `mio/orden-router/rerun.camel.yaml`. Après les cinq variables extraites du corps, il y a quatre TODO, et
les quatre remplacent ce qui se trouve en dessous : l'`UPDATE` vers `pendiente`, l'envoi à `seda:repartir` et la réponse 202.

### TODO 1 : sans nom ou sans motif, non

D'abord deux en-têtes, pour que la réponse sorte propre : `removeHeaders` avec `pattern: "*"` et un `setHeader` de
`Content-Type` à `application/json`. Puis un `filter` qui ne laisse passer **que ce qui est faux** : s'il manque `orden`,
`momento`, `canal`, `pedido_por` ou `motivo`, il répond 400 et s'arrête.

```yaml
- filter:
    expression:
      simple:
        expression: >-
          ${variable.orden} == null || ${variable.orden} == '' || …
          ${variable.pedido_por} == null || ${variable.pedido_por} == '' ||
          ${variable.motivo} == null || ${variable.motivo} == ''
    steps:
      - setHeader:
          name: CamelHttpResponseCode
          expression:
            constant:
              expression: "400"
              resultType: java.lang.Integer
      - setBody:
          expression:
            constant:
              expression: '{"resultado":"no reprocesado","motivo":"faltan datos: orden, momento, canal, pedido_por y motivo"}'
      - stop: {}
```

Le `stop` termine la route à cet endroit : ce qui vient en dessous ne s'exécute pas.

### TODO 2 : seulement ce qui a échoué

Lisez l'opération **dans la base**, pas dans le corps. Copiez la requête de la route `repartir` de `reparto.camel.yaml` et
changez-y deux choses : qu'elle filtre aussi sur `p.canal = :#${variable.canal}` au lieu de `p.estado = 'pendiente'`,
et qu'elle renvoie aussi `p.estado`. Elle va avec `outputType: SelectOne` : le corps est donc **une** ligne (ou
`null`).

Ensuite, un `choice` à deux branches qui répondent et s'arrêtent, comme le `filter` du TODO 1 :

| Si | Répond | Pourquoi |
|---|---|---|
| `${body} == null` | 404 | Cette opération n'existe pas |
| `${body[estado]} in 'aceptada,confirmada,estacionada'` | 409 | Ce qui est accepté et ce qui est confirmé s'est bien passé ; ce qui est garé est libéré par le balayage (la 17) |

Tout le reste (`pendiente`, `no_se_sabe`, `conciliacion_pendiente`, `rechazada`) continue vers le TODO 3. Avant de continuer,
conservez l'état dans une variable `antes` : vous le renverrez dans la réponse à la fin.

### TODO 3 : une tentative, ici même

C'est la difficulté d'aujourd'hui. Le retraitement n'est **pas** mis en file : on fait une tentative à cet instant, avec les mêmes
routes que celles qu'utilise la distribution.

1. Un `setVariable` `tipo` avec `constant` `manual`. Avec cela, l'`INSERT` d'`intentar` note qui et pourquoi (les
   variables `pedido_por` et `motivo` existent déjà).
2. Un `to` vers `direct:preparar`, avec la ligne dans le corps. Il construit l'enveloppe, l'empreinte et le numéro de contrôle comme le
   fait la distribution.
3. Un `doTry` avec un `to` vers `direct:intentar`. Dans son `doCatch` de `java.io.IOException` (le « on n'a pas pu savoir » de la 20),
   un `UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}` : une tentative manuelle
   ne se retente pas toute seule, elle revient sous les yeux de celui qui l'a demandée.
4. Un `filter` : si `${variable.desenlace} == 'aceptada'`, un `to` vers `direct:barrido`, comme le fait la distribution. Quelque chose
   de garé pouvait attendre cette étape (la 17).

### TODO 4 : dites-lui comment cela s'est passé

Répondez 200 avec ce qui est resté dans les variables :

```yaml
- setBody:
    expression:
      simple:
        expression: '{"resultado":"reprocesado","operacion":${variable.operacion},"intento":${variable.intento},"antes":"${variable.antes}","desenlace":"${variable.desenlace}","codigo":${variable.codigo},"version":"${variable.version}"}'
```

Regardez le dernier champ. La tentative part avec **ce qui est déployé aujourd'hui** : le mapping et le gabarit en vigueur, pas
ceux du jour où elle a échoué. Si, entre la panne et le rerun, quelqu'un a corrigé le mapping, le rerun part corrigé, et la
version avec laquelle il est parti reste notée dans la tentative (`version_mapeo`) et dans la réponse.

## 4. Cassez-en une exprès

Une fois tout terminé, retirez la seconde branche du `choice` du TODO 2, celle du 409. Lancez `./verifica`.

Avant cela, prédisez : le verifica demande un rerun de la 4001, qui est déjà `aceptada` chez le partenaire. Que se passe-t-il dans la destination ?

Le routeur fait la tentative manuelle : il rappelle le canal du partenaire, et l'opération a une tentative de plus. La coche du rerun
de ce qui est accepté sort en rouge. La piste d'audit contient désormais un retraitement dont personne n'avait besoin, et dans une destination qui ne
reconnaîtrait pas les doublons il y aurait un doublon. **Le routeur ne demande pas à la
destination si elle l'a déjà : il le demande à sa propre base.** Remettez la branche.

## 5. Vérifiez

```bash
./verifica
```

`verifica` éteint le SFTP et envoie quatre commandes ; il met l'API en mode lent et envoie la 4005 ; puis il remet tout en place et
fait cinq reruns, un pour chaque étape bloquée. À la fin, il en demande un pour quelque chose d'accepté et un sans motif. Elle est terminée
quand vous voyez les onze coches vertes :

```
✅ Arrancaron el router (con su POST /rerun), los tres canales y el sondeo
✅ Con el SFTP caído, el canal del socio de la 4001 a la 4004 quedó pendiente (lo que se reprocesa)
✅ Con la API lenta, el canal rest de la 4005 no supo cómo le fue y quedó (…)
✅ Rerun: el canal del socio de la 4001 a la 4004 y el rest de la 4005 quedaron aceptados
✅ Cada rerun quedó como intento «manual», con quién lo pidió, por qué y la versión del mapeo con que salió
✅ Sin duplicados: un 850 por orden en el socio, y la API creó la 4005 una vez y al rerun contestó 200 «reenvío»
✅ Solo el paso que falló: la API y el ERP de la 4001 a la 4004 no recibieron intentos nuevos
✅ Rerun de lo que ya está aceptado: 409, sin intento nuevo y la operación sigue (…)
✅ Rerun sin motivo: 400 (sin quién y por qué, el rastro quedaría mudo)
✅ El ERP reconoce un pedido que ya tiene: 200 «ya registrado», sin renglones nuevos
✅ El 997 del socio confirmó los cuatro 850 reprocesados
```

Regardez la sixième. L'API **avait déjà** la 4005 : l'appel lent l'a enregistrée après que le canal a coupé, comme dans la 20.
Le rerun l'a renvoyée avec la même clé, et l'API a répondu 200 « reenvío », pas 201. C'est ce qui rend sûr le
rerun d'un « on ne sait pas ».

Faites vous-même un rerun à la main et regardez la réponse :

```bash
curl -s -XPOST localhost:8080/rerun -H 'Content-Type: application/json' \
  -d '{"orden":"4001","momento":"creada","canal":"edi","pedido_por":"yo","motivo":"probar"}'
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/rerun.camel.yaml solucion/orden-router/rerun.camel.yaml` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `Esperaba 5 intentos manuales` et il y en a 0 | Il manque le `setVariable` `tipo` à `manual`, ou vous avez mis la tentative en file au lieu d'appeler `direct:intentar` |
| `Se reprocesó de más: la API y el ERP … pasaron de … a … intentos` | Votre requête ne filtre pas par canal : elle prend les trois opérations de l'événement |
| `Rerun de la 4001 ya aceptada: contestó …` | Il manque la branche du 409, ou elle ne porte pas de `stop` |
| `Rerun sin motivo: esperaba 400` | Il manque `motivo` au `filter` du TODO 1, ou il lui manque le `stop` |
| `de las 5 operaciones reprocesadas, … quedaron aceptadas` | Il manque `direct:preparar` avant `direct:intentar` : sans lui, il n'y a ni enveloppe ni numéro de contrôle |
| `No pude poner la API en modo lento` | Votre API date d'avant : recréez-la comme dans la 20 |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**Un retraitement est une tentative de plus, pas une nouvelle opération.** Si elle était nouvelle, chaque destination la verrait comme une autre commande.
Comme c'est la même opération, elle part avec la même clé et le même numéro de contrôle, et chaque destination peut la reconnaître.

**On retraite l'étape, pas la commande.** Tout renvoyer « au cas où » remplit la piste d'audit de bruit et parie que
toutes les destinations supportent un doublon. On envoie ce qui a échoué et on refuse pour ce qui s'est bien passé.

**Qui et pourquoi, ce n'est pas de la bureaucratie.** Dans un mois, quelqu'un demandera pourquoi la 4005 a deux tentatives.
Sans `pedido_por` ni `motivo`, la réponse est « je ne sais pas » ; avec eux, « le support l'a demandé parce que l'API était lente ».

**Il part avec ce qui vaut aujourd'hui, et on le note.** Un rerun ne ressuscite pas le passé : il utilise le mapping en vigueur. C'est pour cela que chaque tentative conserve sa
version, et que l'on peut savoir ce qu'a reçu la destination à chaque fois.

## Exercice : le comptoir des réclamations

Avec votre version terminée, lancez `./verifica`, puis :

1. Consultez les tentatives du canal du partenaire pour la 4001 :
   `SELECT n, tipo, pedido_por, motivo, desenlace, version_mapeo FROM intento i JOIN operacion p ON p.id = i.operacion WHERE p.orden = '4001' AND p.canal = 'edi' ORDER BY n;`
   Racontez l'histoire de cette opération en trois phrases, avec ce que dit chaque ligne.
2. Demandez trois reruns à la main : un pour une commande qui n'existe pas (`9999`), un pour la 4001 dans le canal `rest` et un sans
   `pedido_por`. Notez ce que répond chacun et pourquoi.
3. Imaginez que demain vous corrigiez le mapping du partenaire en version 2 et que vous fassiez un rerun d'un 850 qui avait échoué hier.
   Avec quelle version part-il ? Où pouvez-vous le vérifier ? Pourquoi ne serait-il pas préférable qu'il parte avec la version d'hier ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Le retraitement à la main est une tentative de plus, avec un nom et un motif](../decisiones/27-el-rerun-es-un-intento-manual.md)

## Révision

1. Quels états d'une opération peuvent être retraités, et lesquels non ? Pourquoi pas non plus ce qui est garé ?
2. Pourquoi le rerun lit-il l'opération dans la base au lieu de se fier à ce que dit le corps ?
3. Qu'est-ce qui évite qu'un rerun de la 4005 crée une autre commande dans l'API ?
4. Pourquoi répond-on 400 sans `pedido_por` ou sans `motivo` ?
5. **La question de clôture :** le rerun part avec la configuration en vigueur et laisse sa version notée. Pourquoi avec celle
   d'aujourd'hui, et à quoi sert de la noter ?

**Suite :** dans la leçon 24, le problème n'est pas que la destination soit en panne, mais que ce soit vous qui la fassiez tomber. Avec trois répliques
d'int-bd, combien d'appels simultanés l'ERP reçoit-il ?
