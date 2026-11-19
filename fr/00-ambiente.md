# Leçon 00 — Environnement prêt

> **Ce que tu vas obtenir :** démarrer d'une seule commande les quatre systèmes avec lesquels tu vas intégrer pendant tout
> le cours, et vérifier que chacun répond. Tu ne programmes encore rien : tu prépares l'atelier.

## L'histoire en une phrase

Tu vas connecter une **boutique en ligne** aux systèmes dont elle a besoin pour vendre : une **API de commandes**, l'**ERP**
de l'entreprise et un **partenaire commercial** qui reçoit des fichiers. Ces systèmes existent déjà et ne sont pas les tiens : ton travail est
que l'information voyage entre eux sans se perdre. Dans cette leçon, tu les allumes.

## Avant de commencer, il te faut

| Quoi | Pour quoi faire | Comment vérifier que tu l'as |
|---|---|---|
| Un terminal | Écrire les commandes | Sur Mac, l'application *Terminal* ; sous Windows, *Ubuntu* dans WSL ; sous Linux, celui qui est fourni |
| **git** | Télécharger le dépôt du cours | `git --version` répond par un numéro |
| **Docker** avec Compose | Faire tourner chaque système dans son propre conteneur, sans l'installer sur ta machine | `docker compose version` répond par un numéro |
| De la mémoire libre pour Docker | Les quatre systèmes et Camel tournent en même temps | `docker compose up --wait` se termine sans que ta machine se fige |

Tu n'installes ni Java, ni Maven, ni Camel, ni aucune base de données. **Tout tourne dans Docker.**

Si l'un de ces mots est nouveau pour toi (terminal, git, conteneur), lis d'abord les fiches de
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/). Elles sont courtes.

⚠️ **Travaille dans ton dossier personnel** (`~`), jamais dans `/tmp`. Sur Mac avec colima, Docker ne voit que les dossiers
de ton home.

---

## 1. Télécharge le cours

```bash
cd ~                                  # ve a tu carpeta personal
git clone https://github.com/HabilMX/ni-una-orden-perdida.git   # baja una copia a tu máquina
cd ni-una-orden-perdida               # entra a la carpeta que se creó
```

## 2. Prédis

Ouvre `infra/compose.yaml` avec ton éditeur. C'est la liste des systèmes que tu vas démarrer. Sans rien lancer, réponds
sur papier :

1. Combien de services y a-t-il, et comment s'appelle chacun ?
2. Quel numéro de port chacun utilise-t-il sur ta machine ? *(Indice : cherche `ports:`. Le numéro de gauche est celui de ta
   machine ; celui de droite, celui de l'intérieur du conteneur.)*
3. Lequel, selon toi, mettra le plus de temps à démarrer la première fois, et pourquoi ?

## 3. Démarre-le

Depuis le dossier racine du cours :

```bash
docker compose -f infra/compose.yaml up -d --wait
```

- `-f infra/compose.yaml` indique à Docker quelle liste de services utiliser.
- `up` les démarre.
- `-d` les laisse tourner en arrière-plan : tu récupères ainsi ton terminal.
- `--wait` attend que tous se déclarent sains.

**La première fois prend plusieurs minutes**, surtout à cause de l'API de commandes : Docker la construit à partir de son code avant
de la démarrer. Tu sauras que c'est terminé quand `--wait` te rendra le terminal et que `docker compose ps` affichera `healthy` pour
chaque système. Les fois suivantes, c'est beaucoup plus rapide.

## 4. Compare avec ta prédiction

```bash
docker compose -f infra/compose.yaml ps
```

Tu devrais voir quatre services, tous en `healthy` (sain) :

| Service | Ce que c'est dans l'histoire | Sur ta machine | Ce que c'est, techniquement |
|---|---|---|---|
| `api-pedidos` | Le système de commandes de l'entreprise, auquel on s'adresse par internet | `localhost:8081` | Une API REST écrite avec Quarkus. Elle garde tout en mémoire : au redémarrage, elle repart vide |
| `erp` | L'**ERP** : le système central où l'entreprise enregistre ce qu'elle vend | `localhost:13306` | Une base MariaDB avec deux tables (en-tête et détail) et une **procédure stockée** (`registrar_pedido`) : un programme enregistré dans la base |
| `socio-sftp` | La boîte aux lettres du **partenaire commercial**, où on lui dépose des fichiers | `localhost:2222` | Un serveur **SFTP**, pour copier des fichiers de façon sûre, avec les dossiers `entrada/` et `salida/` |
| `estado` | Le carnet où ton intégration note où en est chaque commande | `localhost:15432` | Une base PostgreSQL avec trois tables. Tu n'y touches pas avant la leçon 15 |

Avais-tu deviné lequel prenait le plus de temps ?

## 5. Vérifie

```bash
cd lecciones/00-ambiente
./verifica
```

Elle est terminée quand tu vois les neuf coches vertes :

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

Regarde la septième. `verifica` demande à l'ERP d'enregistrer une commande qui n'existe pas, et **s'attend à ce que l'ERP refuse**. La neuvième fait la même chose avec l'API : elle lui envoie une commande sans
sa clé et s'attend à ce qu'elle la rejette.
Les systèmes de ce cours ne disent pas oui à tout, comme les vrais : ils valident, rejettent et répondent parfois en retard.
Si une destination acceptait n'importe quoi, ton intégration semblerait fonctionner alors même qu'elle serait fausse.

## 6. Éteins-le et rallume-le

```bash
docker compose -f infra/compose.yaml down        # apaga y quita los contenedores
docker compose -f infra/compose.yaml up -d --wait
```

En éteignant avec `down`, ce qu'il y avait à l'intérieur est effacé : l'environnement repart propre à chaque fois. Au début de chaque leçon,
tu l'allumeras ainsi.

## Si quelque chose échoue

| Ce que tu vois | Ce qui se passe | Ce que tu fais |
|---|---|---|
| `port is already allocated` | Quelque chose utilise déjà ce port. C'est pour cela que le cours utilise 15432 et 13306 au lieu des ports habituels (5432 et 3306), mais un autre programme peut occuper 8081 ou 2222 | Arrête ce programme, ou demande sur le canal comment changer le port |
| `Cannot connect to the Docker daemon` | Docker ne tourne pas | Ouvre Docker Desktop, ou sur Mac `colima start` |
| Un service reste en `starting` | Il manque de mémoire ou il est encore en construction | Attends une minute de plus ; si rien ne change, `docker compose -f infra/compose.yaml logs <servicio>` |
| `Source file does not exist` | Tu as cloné le dépôt hors de ton home (Mac avec colima) | Clone-le dans `~` |

## Une note sur les mots de passe

Dans `infra/compose.yaml`, les mots de passe sont écrits en clair (`practica`). Ici c'est possible parce qu'il s'agit d'un environnement
de pratique sur ta machine. **Dans un système réel, cela ne se fait jamais :** les mots de passe arrivent par des variables
d'environnement ou par un gestionnaire de secrets, et ne sont pas conservés dans le dépôt.

---

## Pourquoi on a fait ainsi

Chaque décision du cours a sa fiche : ce que nous avons décidé, pourquoi, ce que nous avons écarté et ce qui nous ferait
changer d'avis. Elles se trouvent dans [`decisiones/`](../decisiones/), et chaque leçon te donne le lien vers la sienne. Aujourd'hui, il n'y en a encore
aucune : l'environnement est le socle, pas une décision de conception.

## Révision

1. Que fait chacune des quatre pièces dans l'histoire de la boutique ?
2. Que signifient `up`, `-d` et `--wait` ?
3. Pourquoi est-il bon que l'ERP rejette une commande qui n'existe pas ?
4. Pourquoi la base d'état utilise-t-elle le port 15432 sur ta machine et non le 5432 habituel ? Que se passerait-il si elle utilisait le 5432
   alors que tu as déjà un PostgreSQL installé ?
5. Qu'arrive-t-il aux données de l'ERP quand tu éteins l'environnement avec `down` et que tu le rallumes ? Pourquoi
   est-ce commode dans un cours pratique ?

**Suite :** dans la leçon 01, tu lances ta première route Camel. Elle n'utilise pas encore ces systèmes : elle déplace un fichier d'un
dossier à un autre.
