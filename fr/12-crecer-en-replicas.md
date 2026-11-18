# Leçon 12 — Grandir en répliques

> **Ce que vous allez obtenir :** faire tourner **trois répliques** d'int-bd sans toucher aux autres services, mesurer comment
> les commandes se répartissent entre elles, et découvrir pourquoi, au début, elles ne se répartissent pas.

**Avant de commencer, il vous faut :** la leçon 11 terminée et l'environnement de la 00 allumé (avec l'ERP recréé dans la
10).

## La nouveauté du jour, en une phrase

Le Buen Fin arrive, le grand week-end de promotions au Mexique, et la boutique envoie trois fois plus de commandes. Le goulot d'étranglement est int-bd, qui écrit dans l'ERP.
Aujourd'hui, **vous faites passer à l'échelle un seul service** : au lieu d'une réplique d'int-bd, vous en faites tourner trois, et le routeur continue de les appeler par le
même nom, `int-bd`.

**L'image :** une agence bancaire qui ouvre deux guichets de plus. Les clients continuent de faire la même file,
et la banque place quelqu'un qui les envoie vers un guichet libre. Aujourd'hui, vous allez voir ce qui se passe quand ce quelqu'un ne fait pas
bien son travail.

Une **réplique** est une copie identique du même service : la même image, le même dossier, les mêmes routes.
int-bd peut être répliqué sans qu'une commande soit enregistrée deux fois parce qu'**il ne garde rien en mémoire** : tout ce qui compte reste dans l'ERP.

---

## 1. Préparez votre dossier

```bash
cd lecciones/12-crecer-en-replicas
cp -R inicio mio
```

Aujourd'hui, **vous ne touchez à aucune route**. Tout le travail tient dans un seul fichier, `mio/replicas.yaml`. Compose le réunit avec le
`compose.yaml` de la leçon, auquel on ne touche pas : Compose les fusionne champ par champ : un
`command` du second fichier remplace celui du premier, et dans `environment` chaque variable s'ajoute ou
remplace celle du même nom.

```bash
DONDE=mio docker compose -f compose.yaml -f mio/replicas.yaml up -d
```

C'est la même idée que le `franquicias.yaml` de la 11 : changer la façon dont un service tourne sans changer le service.

## 2. Prédisez

Aujourd'hui, vous utilisez un nouveau lot : `datos/ordenes/lote-30/`, le lot de 30 commandes, toutes de boutiques en propre (le partenaire n'en
reçoit aucune). Répondez sur papier :

1. Si vous faites tourner trois répliques d'int-bd et envoyez les 30 commandes, combien chaque réplique en enregistre-t-elle ?
2. Qui décide vers quelle réplique va chaque commande : le routeur, Camel, Docker ou l'ERP ?

Vérifiez l'état actuel :

```bash
./verifica inicio
```

Deux marques rouges apparaissent : `int-bd corre en 1 copia(s) y se esperaban 3`, et `Con una sola copia no hay nada que repartir`. Le verifica imprime la répartition, et aujourd'hui c'est `int-bd-1=30` : une seule réplique fait tout.

## 3. Demandez trois répliques

Dans `mio/replicas.yaml`, le TODO 1. Demandez trois répliques d'int-bd avec `deploy` → `replicas` :

```yaml
services:
  int-bd:
    deploy:
      replicas: 3
```

Lancez le verifica :

```bash
./verifica
```

Regardez la ligne `Reparto:`. Ce que nous avons vu en la testant :

```
   Reparto: practica-retail-l12-int-bd-1=0 practica-retail-l12-int-bd-2=0 practica-retail-l12-int-bd-3=30
❌ 2 de 3 copias no registraron nada: el router sigue usando la misma conexión.
```

Les trois répliques sont en marche, mais **une fait tout et deux ne font rien**. Aviez-vous trouvé le 10/10/10 ? Presque personne ne le
trouve, parce que cela paraît évident. Avant de continuer, **écrivez votre explication** : pourquoi ne se sont-elles pas réparties ?

## 4. Pourquoi elles ne se répartissent pas

Il y a deux raisons, et elles vont ensemble.

**La connexion est réutilisée.** Ouvrir une connexion HTTP coûte du temps : le routeur en ouvre donc **une** vers `int-bd` et la
laisse ouverte (*keep-alive*). Toutes les commandes suivantes voyagent par cette même connexion, et une connexion va vers une seule
réplique. Docker ne répartit pas les requêtes : il répartit les **connexions**.

**La JVM se souvient de l'adresse.** Quand le routeur demande « où est `int-bd` ? », le DNS de Docker répond les
trois adresses, dans un ordre différent à chaque fois. Mais la JVM du routeur garde la première réponse et ne redemande
pas avant un bon moment.

| | Ce qu'elle fait aujourd'hui | Ce qu'il vous faut |
|---|---|---|
| La connexion | Vit pour toujours | Qu'elle expire vite, pour en ouvrir une autre |
| L'adresse d'`int-bd` | La JVM s'en souvient | Qu'elle l'oublie vite, pour redemander |

Avec les deux durées courtes, chaque nouvelle connexion redemande l'adresse et peut tomber sur une autre réplique.

## 5. Faites répartir le routeur

Le TODO 2, dans le même `mio/replicas.yaml`, à l'intérieur de `orden-router`. Il y a deux choses :

**a) Que la connexion vive au plus une seconde.** C'est l'option `connectionTimeToLive` du composant `http`, en
millisecondes. On la passe au routeur au démarrage :

```yaml
    command: ["run", ".", "--prop=camel.component.http.connectionTimeToLive=1000"]
```

**b) Que la JVM se souvienne de l'adresse une seconde.** Cela va dans une variable d'environnement que la JVM lit au démarrage :

```yaml
    environment:
      JDK_JAVA_OPTIONS: "-Dsun.net.inetaddr.ttl=1"
```

Avant de mettre la b, faites l'essai : **mettez seulement la a** et lancez `./verifica`. Quand nous avons testé la leçon, avec la a seule, la
répartition est restée à `0/0/30`. La connexion expirait bien, mais la nouvelle connexion allait vers la même adresse que la JVM avait
gardée. Ajoutez maintenant la b et relancez.

## 6. Vérifiez

```bash
./verifica
```

`verifica` redémarre l'API, vide l'ERP, démarre les services avec votre `replicas.yaml` et envoie les 30 commandes avec une
pause entre chacune, pour que le routeur ait l'occasion d'ouvrir de nouvelles connexions. Elle est terminée quand vous voyez les six coches vertes :

```
✅ int-bd corre en 3 copias
✅ Arrancaron el router, int-rest, int-edi y las copias de int-bd
✅ ERP: tiene las 30 órdenes REGISTRADO, cada una una sola vez
✅ API: tiene las 30 órdenes (crecer int-bd no tocó a los demás)
   Reparto: practica-retail-l12-int-bd-1=12 practica-retail-l12-int-bd-2=12 practica-retail-l12-int-bd-3=6
✅ Entre todas las copias registraron 30 órdenes: ni una de más, ni una de menos
✅ (…) de 3 copias de int-bd registraron órdenes: el trabajo se repartió
```

**Votre répartition sera différente**, et elle change à chaque exécution : en la testant, nous avons vu 12/12/6 et 6/11/13. Celui qui choisit la réplique,
c'est le réseau, et le réseau ne répartit pas exactement. C'est pour cela que le verifica ne demande pas 10/10/10 : il demande qu'**au moins deux répliques reçoivent
du travail** et qu'**aucune commande ne se perde ni ne se répète**. Il peut vous arriver qu'une réplique reste sans commande lors d'une
exécution : si les deux autres se sont partagé les 30, c'est bon. Chaque réplique compte sa part dans son propre journal : la
répartition est donc dite par les répliques, pas par le routeur.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et `diff mio/replicas.yaml solucion/replicas.yaml` vous dit
en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `int-bd corre en 1 copia(s)` | Il manque le `deploy` → `replicas: 3`, ou il s'est retrouvé sous un autre service. Vérifiez l'indentation |
| `2 de 3 copias no registraron nada` | Il vous manque la a, la b ou les deux (étape 5) |
| `No pude levantar los servicios. Revisa que mio/replicas.yaml sea YAML válido` | Une erreur d'indentation ou de guillemets ; le verifica vous montre les dernières lignes de l'erreur |
| Vous avez mis `CAMEL_COMPONENT_HTTP_CONNECTION_TIME_TO_LIVE` dans `environment` et rien ne se répartit | Dans cette version, cette variable n'a pas d'effet (nous l'avons mesuré : 0/29/0). Utilisez le `--prop` de l'étape 5 |
| `Tu ERP no tiene la columna correlacion` | Recréez l'ERP comme l'indique la 10 |

---

## Ce que cela enseigne au-delà du cours

**On répartit par connexion, et une connexion éternelle ne se répartit pas.** Dans Docker, c'est le DNS qui répartit. Dans Kubernetes, un
Service choisit lui aussi une réplique pour chaque nouvelle connexion : le même problème apparaît donc de la même façon. Celui qui
répartit change ; la règle, non.

**La seconde est là pour que vous le voyiez ici**, ce n'est pas un chiffre pour un système réel. La durée de vie d'une connexion dépend
du trafic et de la fréquence à laquelle les répliques changent. La fermer après chaque commande répartit aussi, mais
vous payez le coût d'ouverture d'une connexion à chaque appel.

## Exercice : grandir sans toucher à personne

Avec votre version terminée, répondez uniquement d'après ce que vous voyez à l'écran :

1. Remplacez `replicas: 3` par `replicas: 5` et lancez `./verifica`. Quelle coche passe au rouge, et pourquoi ? (Indice : le
   verifica en attend 3.) Combien de commandes chacune des cinq a-t-elle enregistrées ?
2. Revenez à 3. Quels fichiers avez-vous touchés pour passer d'une réplique à trois ? Avez-vous touché une route, un mapping ou le
   `compose.yaml` de la leçon ?
3. Pourquoi int-bd peut-il être répliqué trois fois sans qu'une commande soit enregistrée deux fois ? Que se passerait-il si chaque réplique
   gardait dans sa mémoire les commandes qu'elle a déjà vues ?

---

## Ce qui fonctionne déjà

Avec cette leçon, vous refermez la partie 1. Une commande que la boutique crée fait déjà tout ceci :

- elle arrive au routeur par HTTP et le routeur lui répond ce qu'a dit chaque destination ;
- elle arrive à l'API une seule fois, même si elle est renvoyée, grâce à sa clé ;
- elle entre dans l'ERP complète ou n'y entre pas, et int-bd peut passer à plusieurs répliques ;
- elle devient un 850 qui arrive chez le partenaire sans être écrasé ni rester à moitié écrit, seulement si la boutique est la sienne ;
- elle porte un fil qui la suit à travers tous les services ;
- et si une destination tombe, les autres la reçoivent quand même.

Ce qu'elle ne fait pas encore : se souvenir d'où en est chaque commande. Cela commence dans la partie 2.

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Trois répliques ne répartissent pas toutes seules : la répartition se fait par connexion](../decisiones/15-repartir-por-conexion.md)
- [Une seule image ; chaque service est un dossier](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)

## Révision

1. Qu'est-ce qu'une réplique, et pourquoi int-bd peut-il être répliqué sans qu'une commande soit enregistrée deux fois ?
2. Avec `replicas: 3` et rien d'autre, comment les commandes se sont-elles réparties ? Pourquoi ?
3. Que fait `connectionTimeToLive`, et que fait `sun.net.inetaddr.ttl` ? Pourquoi une seule ne suffit-elle pas ?
4. Pourquoi le verifica ne demande-t-il pas 10/10/10, et que demande-t-il à la place ?
5. **La question de clôture :** si le routeur avait des répliques, où vivrait ce qui concerne chaque commande ? Écrivez votre réponse :
   vous la comparerez avec la leçon 15.

**Suite :** dans la leçon 13 : l'expédition arrive. L'entrepôt dépose un fichier, et maintenant que
vous savez faire tourner des répliques, la question est autre : si plusieurs répliques lisent le même dossier, qui prend le fichier ?
