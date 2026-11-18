# Leçon 00 — Environnement prêt

> **Ce que vous allez obtenir :** démarrer d'une seule commande les quatre systèmes avec lesquels vous allez intégrer pendant tout
> le cours, et vérifier que chacun répond. Vous ne programmez encore rien : vous préparez l'atelier.

## L'histoire en une phrase

Vous allez connecter une **boutique en ligne** aux systèmes dont elle a besoin pour vendre : une **API de commandes**, l'**ERP**
de l'entreprise et un **partenaire commercial** qui reçoit des fichiers. Ces systèmes existent déjà et ne sont pas les vôtres : votre travail est
que l'information voyage entre eux sans se perdre. Dans cette leçon, vous les allumez.

## Avant de commencer, il vous faut

| Quoi | Pour quoi faire | Comment vérifier que vous l'avez |
|---|---|---|
| Un terminal | Écrire les commandes | Sur Mac, l'application *Terminal* ; sous Windows, *Ubuntu* dans WSL ; sous Linux, celui qui est fourni |
| **git** | Télécharger le dépôt du cours | `git --version` répond par un numéro |
| **Docker** avec Compose | Faire tourner chaque système dans son propre conteneur, sans l'installer sur votre machine | `docker compose version` répond par un numéro |
| De la mémoire libre pour Docker | Les quatre systèmes et Camel tournent en même temps | `docker compose up --wait` se termine sans que votre machine se fige |

Vous n'installez ni Java, ni Maven, ni Camel, ni aucune base de données. **Tout tourne dans Docker.**

Si l'un de ces mots est nouveau pour vous (terminal, git, conteneur), lisez d'abord les fiches de
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/). Elles sont courtes.

⚠️ **Travaillez dans votre dossier personnel** (`~`), jamais dans `/tmp`. Sur Mac avec colima, Docker ne voit que les dossiers
de votre home.

---

## 1. Téléchargez le cours

```bash
cd ~                                  # ve a tu carpeta personal
git clone https://github.com/HabilMX/ni-una-orden-perdida.git   # baja una copia a tu máquina
cd ni-una-orden-perdida               # entra a la carpeta que se creó
```

## 2. Prédisez

Ouvrez `infra/compose.yaml` avec votre éditeur. C'est la liste des systèmes que vous allez démarrer. Sans rien lancer, répondez
sur papier :

1. Combien de services y a-t-il, et comment s'appelle chacun ?
2. Quel numéro de port chacun utilise-t-il sur votre machine ? *(Indice : cherchez `ports:`. Le numéro de gauche est celui de votre
   machine ; celui de droite, celui de l'intérieur du conteneur.)*
3. Lequel, selon vous, mettra le plus de temps à démarrer la première fois, et pourquoi ?

## 3. Démarrez-le

Depuis le dossier racine du cours :

```bash
docker compose -f infra/compose.yaml up -d --wait
```

- `-f infra/compose.yaml` indique à Docker quelle liste de services utiliser.
- `up` les démarre.
- `-d` les laisse tourner en arrière-plan : vous récupérez ainsi votre terminal.
- `--wait` attend que tous se déclarent sains.

**La première fois prend plusieurs minutes**, surtout à cause de l'API de commandes : Docker la construit à partir de son code avant
de la démarrer. Vous saurez que c'est terminé quand `--wait` vous rendra le terminal et que `docker compose ps` affichera `healthy` pour
chaque système. Les fois suivantes, c'est beaucoup plus rapide.

## 4. Comparez avec votre prédiction

```bash
docker compose -f infra/compose.yaml ps
```

Vous devriez voir quatre services, tous en `healthy` (sain) :

| Service | Ce que c'est dans l'histoire | Sur votre machine | Ce que c'est, techniquement |
|---|---|---|---|
| `api-pedidos` | Le système de commandes de l'entreprise, auquel on s'adresse par internet | `localhost:8081` | Une API REST écrite avec Quarkus. Elle garde tout en mémoire : au redémarrage, elle repart vide |
| `erp` | L'**ERP** : le système central où l'entreprise enregistre ce qu'elle vend | `localhost:13306` | Une base MariaDB avec deux tables (en-tête et détail) et une **procédure stockée** (`registrar_pedido`) : un programme enregistré dans la base |
| `socio-sftp` | La boîte aux lettres du **partenaire commercial**, où on lui dépose des fichiers | `localhost:2222` | Un serveur **SFTP**, pour copier des fichiers de façon sûre, avec les dossiers `entrada/` et `salida/` |
| `estado` | Le carnet où votre intégration note où en est chaque commande | `localhost:15432` | Une base PostgreSQL avec trois tables. Vous n'y touchez pas avant la leçon 15 |

Aviez-vous deviné lequel prenait le plus de temps ?

## 5. Vérifiez

```bash
cd lecciones/00-ambiente
./verifica
```

Elle est terminée quand vous voyez les neuf coches vertes :

```
✅ estado está arriba
✅ erp está arriba
✅ socio-sftp está arriba
✅ api-pedidos está arriba
✅ La base de estado tiene sus tres tablas
✅ El ERP tiene su procedimiento registrar_pedido
✅ El ERP rechaza lo que no cuadra
✅ El SFTP del socio tiene entrada/ y salida/
✅ La API de pedidos contesta y exige su llave (400 sin ella)
```

Regardez la septième. `verifica` demande à l'ERP d'enregistrer une commande qui n'existe pas, et **s'attend à ce que l'ERP refuse**. La neuvième fait la même chose avec l'API : elle lui envoie une commande sans
sa clé et s'attend à ce qu'elle la rejette.
Les systèmes de ce cours ne disent pas oui à tout, comme les vrais : ils valident, rejettent et répondent parfois en retard.
Si une destination acceptait n'importe quoi, votre intégration semblerait fonctionner alors même qu'elle serait fausse.

## 6. Éteignez-le et rallumez-le

```bash
docker compose -f infra/compose.yaml down        # apaga y quita los contenedores
docker compose -f infra/compose.yaml up -d --wait
```

En éteignant avec `down`, ce qu'il y avait à l'intérieur est effacé : l'environnement repart propre à chaque fois. Au début de chaque leçon,
vous l'allumerez ainsi.

## Si quelque chose échoue

| Ce que vous voyez | Ce qui se passe | Ce que vous faites |
|---|---|---|
| `port is already allocated` | Quelque chose utilise déjà ce port. C'est pour cela que le cours utilise 15432 et 13306 au lieu des ports habituels (5432 et 3306), mais un autre programme peut occuper 8081 ou 2222 | Arrêtez ce programme, ou demandez sur le canal comment changer le port |
| `Cannot connect to the Docker daemon` | Docker ne tourne pas | Ouvrez Docker Desktop, ou sur Mac `colima start` |
| Un service reste en `starting` | Il manque de mémoire ou il est encore en construction | Attendez une minute de plus ; si rien ne change, `docker compose -f infra/compose.yaml logs <servicio>` |
| `Source file does not exist` | Vous avez cloné le dépôt hors de votre home (Mac avec colima) | Clonez-le dans `~` |

## Une note sur les mots de passe

Dans `infra/compose.yaml`, les mots de passe sont écrits en clair (`practica`). Ici c'est possible parce qu'il s'agit d'un environnement
de pratique sur votre machine. **Dans un système réel, cela ne se fait jamais :** les mots de passe arrivent par des variables
d'environnement ou par un gestionnaire de secrets, et ne sont pas conservés dans le dépôt.

---

## Pourquoi on a fait ainsi

Chaque décision du cours a sa fiche : ce que nous avons décidé, pourquoi, ce que nous avons écarté et ce qui nous ferait
changer d'avis. Elles se trouvent dans [`decisiones/`](../decisiones/), et chaque leçon vous donne le lien vers la sienne. Aujourd'hui, il n'y en a encore
aucune : l'environnement est le socle, pas une décision de conception.

## Révision

1. Que fait chacune des quatre pièces dans l'histoire de la boutique ?
2. Que signifient `up`, `-d` et `--wait` ?
3. Pourquoi est-il bon que l'ERP rejette une commande qui n'existe pas ?
4. Pourquoi la base d'état utilise-t-elle le port 15432 sur votre machine et non le 5432 habituel ? Que se passerait-il si elle utilisait le 5432
   alors que vous avez déjà un PostgreSQL installé ?
5. Qu'arrive-t-il aux données de l'ERP quand vous éteignez l'environnement avec `down` et que vous le rallumez ? Pourquoi
   est-ce commode dans un cours pratique ?

**Suite :** dans la leçon 01, vous lancez votre première route Camel. Elle n'utilise pas encore ces systèmes : elle déplace un fichier d'un
dossier à un autre.
