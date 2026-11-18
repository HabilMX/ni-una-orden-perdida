# Leçon 07 — Vers le partenaire

> **Ce que vous allez obtenir :** déposer le 850 de la 06 dans la boîte aux lettres du partenaire commercial avec quatre précautions : qu'il ne prenne pas un
> fichier à moitié écrit, que la même commande porte toujours le même nom, qu'un second envoi n'efface pas le premier et que la route ne se connecte qu'au serveur dont la clé figure dans `known_hosts`.

**Avant de commencer, il vous faut :** la leçon 06 terminée et l'environnement de la 00 allumé :

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## La nouveauté du jour, en une phrase

Le partenaire n'a pas d'API : il a une **boîte aux lettres SFTP**, un dossier sur son serveur dans lequel vous déposez des fichiers et d'où
il les lit **quand il veut**. Il ne vous prévient pas quand il lit et ne vous répond pas. C'est pour cela que ce que vous y laissez doit être
complet et ne peut pas gêner ce qui s'y trouvait déjà.

**SFTP, le minimum :** un programme pour copier des fichiers vers un autre ordinateur par le réseau, de façon sûre. Il demande un utilisateur
et un mot de passe (ou une clé), et avant de se connecter il vérifie que le serveur est bien celui qu'il prétend être. Cette vérification
est l'étape 2.

---

## 1. Préparez votre dossier

```bash
cd lecciones/07-al-socio
cp -R inicio mio
cd mio
```

La route utilise le mapping et le gabarit du 850 de la 06, déjà présents dans `mio/`.

## 2. Faites confiance au partenaire

La première fois que vous parlez à un serveur, vous devez savoir que c'est **celui de votre partenaire** et non un autre qui se fait passer pour
lui. Chaque serveur a une **clé publique**, comme une empreinte digitale. Vous la notez une fois dans un fichier appelé
`known_hosts`, et dès lors la route ne se connecte que si le serveur présente cette même clé.

```bash
../../../herramientas/confiar-en-socio
```

Dans la vie réelle, le partenaire vous envoie sa clé par un autre canal (un courriel, un portail) et vous la notez à la main. Ici, le
script la prend directement dans le conteneur du partenaire, qui tient lieu d'administrateur.

⚠️ Si vous éteignez l'environnement avec `down` et que vous le redémarrez, le serveur du partenaire a une clé toute neuve : relancez le
script. `known_hosts` est à vous et git ne le suit pas.

## 3. Lisez la route

Ouvrez `orden-al-socio.camel.yaml`. C'est la route de la 06 avec une autre station de sortie :

```yaml
uri: sftp://socio@socio-sftp:22/entrada
```

| Partie | Ce que c'est |
|---|---|
| `socio@` | L'utilisateur avec lequel vous entrez dans la boîte aux lettres |
| `socio-sftp:22` | Le serveur du partenaire et son port |
| `/entrada` | Le dossier où le partenaire relève ce qu'on lui envoie |

En dessous viennent ses paramètres :

| Paramètre | Ce qu'il fait |
|---|---|
| `knownHostsFile: known_hosts` et `strictHostKeyChecking: "yes"` | Ne se connecte que si la clé du serveur est celle que vous avez notée à l'étape 2 |
| `fileName: "850-${variable.orden}.edi"` | Le nom final. La commande 1001 s'appelle toujours `850-1001.edi` |

Ce nom est dit **déterministe** : il ne dépend que de la commande. S'il portait l'heure ou un nombre au hasard,
la même commande envoyée deux fois laisserait deux fichiers, et le partenaire la préparerait deux fois.

## 4. Prédisez

Répondez sur papier :

1. Si vous envoyez la commande 1001 deux fois, combien de fichiers attendez-vous dans la boîte aux lettres du partenaire ?
2. Si, la seconde fois, la commande porte **une autre quantité** (5 tasses au lieu de 2), que contient le fichier à la fin ?
3. Si quelqu'un remplace le serveur du partenaire par un autre qui a une autre clé, que fait la route, selon vous ?

## 5. Lancez-la deux fois

Mettez la commande 1001 dans `entrada/` et lancez la route. `--max-messages=1` l'arrête toute seule après une commande :

```bash
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
```

Demandez à la boîte aux lettres du partenaire ce qu'elle contient :

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp ls /home/socio/entrada
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Envoyez maintenant la **même commande avec une autre quantité**, `orden-1001-cambiada.json`, et regardez de nouveau la boîte aux lettres :

```bash
cp ../../../datos/ordenes/orden-1001-cambiada.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Cherchez le premier `PO1`. Il dit `PO1*1*5*…` : **le second envoi a effacé le premier sans prévenir**. Il y a un seul fichier, comme
vous l'aviez prédit à la question 1, mais ce n'est plus la commande que le partenaire avait. S'il l'avait déjà lue, il a maintenant deux
versions différentes de la commande 1001 et ne sait pas laquelle vaut.

## 6. Ajoutez les deux précautions qui manquent

Dans la route, il y a un commentaire **TE TOCA** (« à vous »). Deux paramètres de plus de la station `sftp:` y prennent place :

**Nom temporaire.** Envoyer un fichier prend du temps. Si le partenaire lit son dossier juste au milieu, il emporte un fichier
tronqué. La solution est de l'écrire sous un autre nom et de le renommer à la fin, ce qui est instantané :

```yaml
tempFileName: "${file:onlyname.noext}.tmp"
```

Pendant l'écriture, il s'appelle `850-1001.tmp` ; quand c'est terminé, il est renommé en `850-1001.edi`. Si le partenaire ne relève que
les fichiers `.edi`, il est très difficile qu'il en prenne un incomplet : le renommage est la dernière chose qui se passe. Ce n'est pas une garantie
totale (un partenaire qui relève n'importe quel fichier, ou un dossier partagé mal configuré, la mettent en défaut), et c'est pour cela que, dans la
leçon 21, la confirmation de la bonne arrivée est donnée par le 997, pas par le fichier.

**Ne pas écraser.** Par défaut, si le fichier existe déjà, Camel le remplace. Vous voulez le contraire : que cela échoue et que quelqu'un
décide.

```yaml
fileExist: Fail
```

Soignez l'indentation : les deux se placent au même niveau que `fileName`.

Videz la boîte aux lettres et refaites l'étape 5 :

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp rm /home/socio/entrada/850-1001.edi
```

Maintenant, le second envoi ne passe pas. Dans le terminal de la route apparaît :

```
File already exist: entrada/850-1001.edi. Cannot write new file.
```

Et la commande modifiée **est restée dans votre dossier `entrada/`**, sans être écartée dans `.camel/` comme celles qui passent bien. Elle n'est pas
perdue : elle attend que quelqu'un décide quoi en faire. Vous résoudrez cela dans la leçon 18.

## 7. Changez la clé

Maintenant, la question 3. Sauvegardez votre `known_hosts`, mettez une fausse clé et envoyez une commande :

```bash
cp known_hosts known_hosts.bueno
echo "socio-sftp ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHVuYWxsYXZlZGVvdHJvc2Vydmlkb3JxdWVub2Vz" > known_hosts
cp ../../../datos/ordenes/orden-1003.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1 --max-seconds=40
```

Parmi les erreurs, vous trouverez :

```
HostKey has been changed: socio-sftp
```

La route **a refusé de se connecter** : le serveur a présenté une clé différente de celle qui était notée. Il se peut que le partenaire l'ait
changée (et il devrait vous en avertir par un autre canal), ou que quelqu'un se soit glissé au milieu en se faisant passer pour lui. Dans les deux
cas, ce qu'il faut faire est de ne rien lui envoyer. Remettez votre bonne clé :

```bash
mv known_hosts.bueno known_hosts
```

## 8. Vérifiez

```bash
cd ..
./verifica
```

⚠️ `verifica` **vide la boîte aux lettres du partenaire** avant de tester.

Elle est terminée quand vous voyez les sept coches vertes :

```
✅ Llegó 850-1001.edi al SFTP del socio
✅ No quedó ningún archivo .tmp
✅ Lo que recibió el socio es el 850 correcto
✅ Un segundo envío de la 1001 no pisó el archivo que ya estaba
✅ La ruta escribe con nombre temporal
✅ La ruta intentó conectar y el servidor no presentó la llave anotada
✅ Con otra llave en known_hosts, la ruta se negó a conectar
```

La cinquième est différente des autres : le `.tmp` vit quelques millisecondes et il n'y a aucun moyen de le voir de l'extérieur ;
`verifica` le contrôle donc dans votre route. La sixième cherche dans le journal le `HostKey has been changed` de l'étape 7 : elle démontre que la
route a bien tenté de se connecter et que c'est la clé qui l'a rejetée, pas une autre panne. Les autres sont confirmées par la boîte aux lettres du partenaire.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-al-socio.camel.yaml solucion/orden-al-socio.camel.yaml` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `PISÓ el archivo del socio` | Il manque `fileExist: Fail` |
| `no usa nombre temporal` | Il manque `tempFileName` |
| `HostKey has been changed` à chaque exécution | Vous avez recréé l'environnement : relancez `confiar-en-socio` (étape 2) |
| `No pude hablar con el SFTP del socio` | Allumez l'environnement : `docker compose -f infra/compose.yaml up -d --wait` |

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Quatre précautions pour déposer un fichier chez le partenaire](../decisiones/08-entrega-segura-al-sftp.md)

## Révision

1. Pourquoi le fichier s'écrit-il sous un nom temporaire pour être renommé à la fin ?
2. Qu'est-ce qui rend le nom `850-1001.edi` déterministe, et que se passerait-il s'il portait l'heure ?
3. Sans `fileExist: Fail`, qu'est-il arrivé au premier envoi ? Pourquoi est-ce pire qu'une erreur ?
4. À quoi sert `known_hosts`, et que fait la route si le serveur présente une autre clé ?
5. La commande qui n'a pas pu être envoyée est restée dans `entrada/`. Pourquoi est-ce mieux que de l'effacer ?

**Suite :** vous avez maintenant les trois destinations : l'API, l'ERP et le partenaire. Dans la leçon 08, vous les réunissez : la commande entre
par un **routeur** qui la distribue entre des services qui s'appellent par leur nom.
