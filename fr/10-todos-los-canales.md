# Leçon 10 — Tous les canaux

> **Ce que tu vas obtenir :** que le routeur envoie la même commande, **en même temps**, aux trois destinations (l'API, l'ERP et le
> partenaire), et qu'il réponde à la boutique ce que chacune a dit. Et voir de tes propres yeux ce qui se passe quand l'une des
> trois est en panne.

**Avant de commencer, il te faut :** les leçons 05, 07 et 09 terminées et l'environnement de la 00 allumé.

⚠️ **Cette leçon ajoute une colonne à l'ERP** (`correlacion`, pour conserver le fil de la 09). Si ton environnement était déjà
démarré auparavant, recrée l'ERP une fois, depuis la racine du cours :

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V erp
```

`--force-recreate` le crée de nouveau et `-V` efface ses données : il démarre ainsi avec les nouvelles tables. Si tu l'oublies,
`verifica` te prévient et te donne cette même commande.

## La nouveauté du jour, en une phrase

Dans la 08, le routeur passait la commande à **un** service. Aujourd'hui, il y a quatre services et le routeur distribue **trois
copies** de la commande, une par destination, toutes en même temps :

```
                                        ┌→ int-rest → API de pedidos
  tienda → POST /ordenes → [router] ────┼→ int-bd   → ERP
                                        └→ int-edi  → SFTP del socio (850)
```

int-bd est ta leçon 05 derrière le routeur, et int-edi est ta leçon 07. Tu ne les réécris pas : ils sont fournis tout faits.

**L'image :** un avis que le siège envoie à trois agences. Il n'attend pas que la première réponde pour l'envoyer à la
deuxième : il l'envoie aux trois en même temps, puis rassemble les trois réponses.

Ce patron a un nom dans les EIP : **Multicast**. Une même commande part vers plusieurs destinations fixes.

---

## 1. Prépare ton dossier

```bash
cd lecciones/10-todos-los-canales
cp -R inicio mio
```

Maintenant, `mio/` contient quatre dossiers, un par service : `orden-router`, `int-rest`, `int-bd` et `int-edi`.

## 2. Lis le routeur

Ouvre `mio/orden-router/orden-router.camel.yaml`. En haut, tu connais déjà tout : le fil de la 09 et l'enveloppe `ce-*`. En bas,
il y a du nouveau : **trois routes de canal**, `canal-api`, `canal-erp` et `canal-socio`. Chacune appelle son service et
est faite pour laisser **une seule ligne** disant ce qui s'est passé, que son service réponde ou non :

| Si le service… | La ligne est… |
|---|---|
| répond, même par une erreur | `{"canal":"erp","codigo":200}`, avec le code HTTP qu'il a donné |
| ne répond même pas (il est en panne) | `{"canal":"erp","error":"UnknownHostException"}`, avec le nom de l'erreur |

Pour cela, elles utilisent `doTry` et `doCatch` : la route **tente** l'appel et, s'il échoue, **attrape** l'erreur et la transforme en ligne.

Pourquoi tant de soin ? Parce qu'en testant cette leçon, nous avons vu que, sans les routes de canal, quand un service était
en panne, le routeur renvoyait à la boutique **la commande complète**, avec le nom et le courriel du client. Une réponse
d'erreur ne doit jamais contenir de données personnelles.

Aujourd'hui, la route principale n'appelle que l'un des trois :

```yaml
- to:
    uri: direct:canal-api
```

`direct:` est un appel à une autre route **du même fichier**, sans passer par le réseau. Le TODO au-dessus dit ce qui manque.

## 3. Prédis

Réponds sur papier :

1. Avec le routeur tel qu'il est, si tu envoies la commande 1003, quelles destinations la reçoivent ?
2. Quand tu enverras aux trois : si l'ERP est en panne, l'API et le partenaire reçoivent-ils la commande ou non ?
3. Dans ce cas, quel code HTTP la boutique reçoit-elle, selon toi ?

Vérifie la première :

```bash
./verifica
```

Quatre marques rouges apparaissent. L'API a la commande ; l'ERP et le partenaire n'ont rien.

## 4. Écris le multicast

Remplace le `to: direct:canal-api` par un `multicast` qui appelle les trois canaux :

```yaml
- multicast:
    parallelProcessing: true
    aggregationStrategy: "#juntarRespuestas"
    steps:
      - to:
          uri: direct:canal-api
      # … los otros dos canales
```

| Paramètre | Ce qu'il fait |
|---|---|
| `parallelProcessing: true` | Les trois en même temps. Sans cela, l'un après l'autre : le partenaire attendrait que l'ERP ait fini |
| `aggregationStrategy: "#juntarRespuestas"` | Comment les trois réponses sont rassemblées. `juntarRespuestas` est un bean déjà déclaré en haut, qui les met dans une liste |

Après le multicast, le corps est cette liste de trois lignes. Réponds à la boutique avec un `setBody` :

```
{"orden":"${variable.orden}","canales":[${join(",")}]}
```

`${join(",")}` réunit les trois lignes de la liste en les séparant par des virgules. Utilise la notation canonique, comme dans les autres
`setBody` du fichier (`expression:` → `simple:` → `expression:`).

## 5. Démarre-le et envoie-lui une commande

Avant de démarrer à la main, **note la clé du partenaire dans le dossier d'int-edi** : c'est le service qui se connecte à son
SFTP, comme dans la 07.

```bash
(cd mio/int-edi && ../../../../herramientas/confiar-en-socio)
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

Attends les quatre `Started`. Dans un autre terminal, depuis la racine du cours :

```bash
curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1001.json
```

La boutique reçoit ce qu'a dit chaque canal :

```json
{"orden":"1001","canales":[{"canal":"api","codigo":201},{"canal":"erp","codigo":200},{"canal":"socio","codigo":200}]}
```

Les canaux travaillent en même temps, mais le routeur **attend que les trois aient terminé** avant de répondre, et la liste sort
dans l'ordre où ils sont écrits dans le YAML (api, erp, socio), pas dans celui où ils ont terminé. Regarde les journaux :
les trois lignes « contestó » du routeur sortent presque au même moment, et leur ordre, lui, peut changer d'une exécution à
l'autre.

Si le canal du partenaire dit `500`, regarde le journal d'int-edi. Si tu vois `reject HostKey`, tu as oublié de noter la clé.

## 6. Éteins un canal

Maintenant, la question 2. Avec tout en marche, éteins **seulement** int-bd et envoie une autre commande :

```bash
DONDE=mio docker compose -f compose.yaml stop int-bd
curl -s -w ' %{http_code}\n' -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -d @datos/ordenes/orden-1003.json
```

```
{"orden":"1003","canales":[{"canal":"api","codigo":…},{"canal":"erp","error":"UnknownHostException"},{"canal":"socio","codigo":200}]} 200
```

- L'API et le partenaire ont **bien** reçu la commande. Un canal en panne n'arrête pas les deux autres.
- La ligne de l'ERP dit `UnknownHostException` : le routeur a cherché `int-bd` par son nom et personne n'a répondu.
- La boutique a reçu un **200**, alors qu'une destination n'a pas la commande.

Si tu refais cette étape avec la même commande, le partenaire répond `500` : il a déjà ce 850 et ne l'écrase pas, comme dans la 07.

Revois ta prédiction 3. Et voici la question que laisse la leçon : **que faisons-nous de l'ERP, qui n'a pas la commande ?**
La lui renvoyons-nous ? Quand ? Et si l'API l'a déjà : la lui renvoyons-nous aussi ? Écris ta réponse : tu
la compareras avec ce que tu construiras dans les leçons 19 et 22.

Éteins tout :

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 7. Le fil dans les trois destinations

Le fil de la 09 voyage dans l'enveloppe vers les trois canaux. Mais chaque destination le conserve différemment :

| Destination | Où reste le fil |
|---|---|
| API | Dans son journal : `correlacion=<id>` |
| ERP | Dans la nouvelle colonne `pedido_cabecera.correlacion` |
| Partenaire | **Nulle part dans le fichier.** int-edi le note dans **son propre** journal, et le 850 sort comme dans la 07 |

Pourquoi ne l'envoyons-nous pas au partenaire ? Parce qu'on n'envoie au partenaire que les champs que le format X12 convient avec lui. Le
fil est à nous, à notre intégration ; le glisser dans un champ que le partenaire n'a pas demandé ne se fait pas. Avec le partenaire, le fil
existe déjà : ce sont les **numéros de contrôle** du 850 que tu as vus dans la 06. Quand le partenaire répondra par son accusé de réception,
le 997, son `AK1` citera le `GS06` (celui du groupe) et son `AK2` le `ST02` (celui de la transaction). L'`ISA13`, celui de
l'enveloppe complète, est cité par un autre accusé, le `TA1`. Tu t'en serviras dans la leçon 21.

**Toutes les destinations ne peuvent pas conserver ton identifiant.** C'est pour cela que, plus loin, une base d'état reliera le fil au
numéro de contrôle.

## 8. Vérifie

```bash
./verifica
```

`verifica` redémarre l'API, vide l'ERP et la boîte aux lettres du partenaire, note la clé du partenaire dans `mio/int-edi/`, démarre les
quatre services et envoie la commande 1003 avec son propre fil. Elle est terminée quand tu vois les six coches vertes :

```
✅ Arrancaron el router y los tres canales
✅ La tienda mandó la 1003 y el router contestó lo que dijo cada canal: api=201 erp=200 socio=200
✅ API: tiene el pedido 1003, con el hilo tienda-…
✅ ERP: tiene el pedido 1003 REGISTRADO, con el hilo tienda-…
✅ Socio: tiene el 850 de la 1003 en su SFTP
✅ int-edi anotó el hilo (al socio no se le manda: con él, el hilo es el número de control del X12)
```

Trois des six sont confirmées par la destination elle-même : le registre de l'API, la table de l'ERP et la boîte aux lettres du partenaire.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` te dit en quoi elles
diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `Tu ERP no tiene la columna correlacion` | Recrée l'ERP avec la commande du début |
| Du rouge seulement sur l'ERP, ou seulement sur le partenaire | Ce canal n'est pas dans ton `multicast` |
| `El router no contestó que los tres canales la recibieron` et une seule ligne apparaît | Il manque le `setBody` avec `${join(",")}`, ou le multicast est resté un seul `to` |
| `El puerto 8080 está ocupado` | Les services sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Le fil de corrélation voyage avec la commande, mais ne sort pas de la maison](../decisiones/12-el-hilo-de-correlacion.md)
- [La commande part vers tous les canaux, et le routeur raconte ce qui s'est passé](../decisiones/13-multicast-que-no-inventa.md)

## Révision

1. Que fait un multicast, et qu'est-ce qui change avec `parallelProcessing: true` ?
2. À quoi sert le bean `juntarRespuestas`, et que fait `${join(",")}` ?
3. Pourquoi chaque canal laisse-t-il une ligne même si son service est en panne ? Qu'est-ce qui aurait fuité sinon ?
4. Avec l'ERP en panne, quelles destinations ont reçu la commande et quel code la boutique a-t-elle reçu ? Pourquoi ce code est-il un
   problème ?
5. Pourquoi n'envoie-t-on pas le fil au partenaire, et quel numéro joue ce rôle avec lui ?

**Suite :** dans la leçon 11, toutes les commandes ne vont pas à toutes les destinations : le routeur décide auxquelles, selon ce que
contient la commande.
