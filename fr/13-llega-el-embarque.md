# Leçon 13 — L'expédition arrive

> **Ce que vous allez obtenir :** recevoir le fichier que dépose l'entrepôt quand une commande part, sans le prendre à moitié écrit, sans
> le prendre deux fois et sans perdre celui qui arrive abîmé. Ce fichier devient un 856 pour le partenaire et le statut
> EMBARCADO dans l'API et dans l'ERP.

**Avant de commencer, il vous faut :** la leçon 12 terminée et l'environnement de la 00 allumé. Cette leçon utilise deux
nouvelles tables de l'ERP, `embarque` et `entrega`. Si votre ERP date d'avant, recréez-le une fois :

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate -V
```

## La nouveauté du jour, en une phrase

Jusqu'ici, la commande **arrivait** à votre intégration : la boutique appelait le routeur. Avec l'expédition, c'est l'inverse :
l'entrepôt n'appelle personne, il **dépose un fichier dans un dossier** et s'en va. Quelqu'un doit aller voir s'il y a du nouveau.
Cela s'appelle scruter (*Polling Consumer*), et aujourd'hui vous le configurez.

**L'image :** la boîte aux lettres d'un immeuble. Le facteur dépose les lettres et ne sonne pas. Le concierge descend de temps en temps
vérifier, n'emporte que les enveloppes fermées (pas la lettre que quelqu'un est encore en train d'écrire sur la table), écarte ce qu'il
a déjà remis et met de côté ce qui est arrivé abîmé. Et il y a **un seul concierge** : s'ils étaient trois, deux pourraient monter la
même lettre.

Voici la partie 2 : la commande est déjà créée ; maintenant elle sort de l'entrepôt (aujourd'hui), se découpe en plusieurs (14), son
état est conservé (15) et elle arrive chez le client (16 et 17).

---

## 1. Préparez votre dossier

```bash
cd lecciones/13-llega-el-embarque
cp -R inicio mio
```

Aujourd'hui, il y a un nouveau service, `sondeo-bodega`. Regardez le dossier `buzon/` de la leçon, qui, dans le `compose.yaml`, est
monté à l'intérieur du conteneur de scrutation. Tout votre travail tient dans **un seul fichier** :
`mio/sondeo-bodega/sondeo-bodega.camel.yaml`, et à l'intérieur, uniquement dans les paramètres du `from`.

Ouvrez aussi une expédition d'exemple, `datos/embarques/embarque-4001.json` : elle contient la commande, le transporteur, le
**numéro de suivi** (le numéro avec lequel le transporteur suit le colis) et les lignes de commande qui sont parties.

## 2. Prédisez

Répondez sur papier avant de lancer quoi que ce soit :

1. Dans la 12, vous avez appris à faire tourner trois répliques d'un service. Si trois répliques surveillaient le même dossier, que pourrait-il
   arriver à un fichier ?
2. L'entrepôt écrit le fichier petit à petit. Que se passe-t-il si la scrutation le prend alors qu'il en est à la moitié ?
3. Un fichier abîmé arrive, qui n'est pas du JSON valide. Que devrait-il lui arriver : être effacé, rester où il est ou
   être écarté ?

Regardez l'état actuel :

```bash
./verifica inicio
```

Plusieurs marques sortent en rouge. Lisez-les : chacune vous dit quelle précaution manque à la scrutation. La scrutation de `inicio/` lit
tout ce qu'il y a dans `buzon/` et n'écarte rien (`noop: true` laisse chaque fichier où il était).

## 3. Ce qui est déjà fait : les canaux se réutilisent

Avant d'écrire, regardez ce que vous n'avez pas à écrire. Ouvrez `mio/orden-router/orden-router.camel.yaml` et cherchez
`platform-http:/embarques`. C'est la porte par laquelle la scrutation remet chaque expédition au routeur. Plus bas se trouve le
même `multicast` que dans la 10, avec les trois mêmes canaux : `direct:canal-api`, `direct:canal-erp` et
`direct:canal-socio`. La seule différence est l'**étape** : avant, chaque canal appelait `/integrar` ; maintenant, il appelle
`/embarque`.

| Canal | Service | Ce qu'il fait de l'expédition |
|---|---|---|
| API | int-rest | Indique à l'API que la commande a été expédiée, comme un événement avec sa clé (`orden-4001-embarcada`) |
| ERP | int-bd | Écrit la ligne de `embarque` avec le numéro de suivi et passe la commande à EMBARCADO |
| Partenaire | int-edi | Construit un **856**, l'avis d'expédition de X12, et le dépose sur le SFTP avec les précautions de la 07 |

Et la règle de la 11 tient toujours : seules les franchises reçoivent de l'EDI. C'est pour cela qu'aujourd'hui les commandes viennent des boutiques T-501 et
T-502.

## 4. Les quatre précautions de la scrutation

Le composant `file` de Camel sait déjà scruter. Votre travail est de lui dire **quelles précautions prendre**, avec quatre paramètres.
Chaque TODO du fichier vous dit lequel :

| TODO | Paramètre | La précaution | Dans la boîte aux lettres de l'immeuble |
|---|---|---|---|
| 1 | `include` | Ne prendre que les fichiers terminés : `embarque-<algo>.json`. Le `.tmp` ne correspond pas | Seulement les enveloppes fermées |
| 2 | `readLock` avec `changed` | Attendre que le fichier cesse de grossir avant de le prendre | Attendre que le facteur lâche la lettre |
| 3 | `move` et `moveFailed` (et retirer `noop`) | Écarter ce que le routeur a reçu dans `.camel/` et ce qui a échoué dans `.error/` | Ce qui est remis d'un côté, ce qui est abîmé de l'autre |
| 4 | `delay` | À quelle fréquence vérifier le dossier : toutes les 2 secondes, c'est-à-dire 2000 millisecondes | À quelle fréquence descend le concierge |

Deux indices :

- `include` est une **expression régulière** sur le nom du fichier. Le point de `.json` doit être échappé, et en
  YAML entre guillemets doubles la barre oblique inverse s'écrit deux fois : `\\.`
- Pour le nom du fichier écarté, utilisez `${file:name}` : ainsi `embarque-4001.json` devient
  `.camel/embarque-4001.json`.

Remarquez ce que vous ne faites **pas** : vous n'écrivez pas de boucle, vous ne lisez pas le dossier vous-même, vous n'effacez rien. Les quatre précautions sont
de la configuration, et le composant fait le reste.

## 5. Cassez-en une exprès

Avec votre scrutation terminée, retirez la ligne de `moveFailed` et lancez `./verifica`. Avant cela, prédisez : où va le
fichier abîmé si vous ne lui dites pas où l'envoyer ?

`Buzón: el embarque roto no está en .error/` sort en rouge. Le fichier reste dans `buzon/`, et dans le journal de la scrutation
vous le verrez échouer encore et encore : sans `moveFailed`, Camel le laisse où il était et le retente au
tour suivant. Un fichier abîmé que l'on retente pour toujours ne se perd pas, mais il ne prévient pas non plus : il remplit le journal et
cache les erreurs des autres. Remettez la ligne.

## 6. Vérifiez

```bash
./verifica
```

`verifica` crée les commandes 4001 et 4002 dans les trois destinations, puis dépose trois choses dans la boîte aux lettres : l'expédition de la
4001, terminée ; celle de la 4002, encore en `.tmp` ; et une abîmée. Elle est terminée quand vous voyez les dix coches vertes :

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Antes del embarque: la 4001 está CREADO en la API y REGISTRADO en el ERP
✅ API: la 4001 está EMBARCADO con la guía PAQX770004001
✅ API: recibió el embarque de la 4001 una sola vez
✅ ERP: la 4001 está EMBARCADO y su fila de embarque tiene la guía
✅ Socio: el 856 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 856 lleva la guía (REF*CN) y la orden (PRF)
✅ Buzón: el embarque de la 4001 se apartó a .camel/
✅ Buzón: el embarque roto se apartó a .error/ (no se perdió ni se reintenta sin fin)
✅ Buzón: el .tmp que la bodega aún escribía no se tocó (la 4002 sigue CREADO)
```

Regardez la preuve dans le dossier lui-même : `ls -A buzon buzon/.camel buzon/.error`. Et ouvrez le 856 arrivé chez le partenaire,
`mio/salida/856-4001.edi` : cherchez le numéro de suivi dans le segment `REF*CN` et la commande dans `PRF`.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/sondeo-bodega solucion/sondeo-bodega` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `el sondeo tomó el .tmp` | Le motif de `include` ne se termine pas par `\\.json` : avec `embarque-.*` tout court, `embarque-4002.json.tmp` entre aussi. Ou bien il manque l'`include` |
| `el embarque de la 4001 no está en .camel/` | `noop: true` est resté, ou le `move` ne porte pas `${file:name}` |
| `el embarque roto no está en .error/` | Il manque `moveFailed` |
| `le faltan las tablas embarque y entrega` | Votre ERP date d'avant cette leçon : recréez-le comme l'indique « Avant de commencer » |
| `El puerto 8080 está ocupado` | Vous avez laissé tourner les services d'une autre leçon : `docker compose … down` dans son dossier |

---

## Ce que cela enseigne au-delà du cours

**Beaucoup de systèmes n'appellent pas : ils déposent des fichiers.** Les entrepôts, les banques et les vieux systèmes remettent leur travail sous forme de fichiers
dans un dossier ou sur un SFTP, à l'heure où ils le peuvent. L'intégration s'adapte à ce que l'autre sait faire, pas
l'inverse.

**Celui qui scrute va seul.** La scrutation représente peu de travail, et le plus lourd (mapper, appeler les destinations) est fait par le routeur
et les canaux, qui, eux, peuvent grandir en répliques. C'est pour cela que le `compose.yaml` donne `replicas: 1` à la scrutation et laisse
le reste grandir de l'autre côté d'un appel HTTP.

**Écarter vaut mieux qu'effacer.** Avec `.camel/` et `.error/`, n'importe qui peut voir ce qui a été traité et ce qu'il faut
revoir, sans ouvrir un journal.

## Exercice : le deuxième événement du cycle de vie, sans nouvelles routes

Avec votre version terminée :

1. Dans le routeur, combien de routes de canal y a-t-il pour l'expédition et combien pour la création ? Qu'est-ce qui change entre un
   appel et l'autre ? (Indice : cherchez `${variable.paso}`.)
2. Passez le `delay` à 10000 et lancez `./verifica`. Combien de temps faut-il maintenant pour que l'EMBARCADO apparaisse ? Que gagnez-vous et que
   perdez-vous avec une scrutation plus lente ? Revenez à 2000.
3. Ouvrez le `compose.yaml` de la leçon. Quel service porte `replicas: 1` ? Que deviendrait l'expédition de la 4001
   si vous lui mettiez 3 ? Pensez à l'API, à l'ERP et au partenaire séparément : qui le remarquerait et qui non ? (Indice : la
   clé de l'API.)

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Un dossier, une seule scrutation](../decisiones/16-un-solo-sondeo-por-carpeta.md)

## Révision

1. Qu'est-ce que scruter, et pourquoi est-ce nécessaire avec l'entrepôt et pas avec la boutique ?
2. À quoi sert chacune des quatre précautions : `include`, `readLock`, `move` et `moveFailed`, et `delay` ?
3. Pourquoi le `.tmp` de la 4002 n'a-t-il pas été pris ? Que se passerait-il si l'entrepôt écrivait directement sous le nom final ?
4. Que fait un fichier abîmé sans `moveFailed`, et pourquoi est-ce un problème même s'il ne se perd pas ?
5. **La question de clôture :** pourquoi la scrutation tourne-t-elle en une seule réplique, alors que le routeur et les canaux peuvent
   tourner en plusieurs ? Que se passerait-il si toutes les répliques le faisaient ?

**Suite :** dans la leçon 14, l'entrepôt n'envoie plus un fichier par commande : il en envoie un avec tout ce qui est parti dans la
matinée. Un fichier, plusieurs commandes. Combien de 856 attendez-vous ?
