# Leçon 31 — Du laboratoire au projet, sur Kubernetes

> **Annexe facultative.** Le cours s'est terminé à la 30. Cette leçon et la 32 s'adressent à ceux qui travaillent avec Kubernetes ou
> veulent voir jusqu'où va ce qu'ils ont fait. Elles peuvent se **lire** sans être exécutées.
>
> **Ce que vous allez obtenir :** porter int-rest, le même que dans la 12, du dossier du laboratoire vers une image Camel
> Quarkus, le faire tourner dans un Kubernetes de pratique et faire en sorte que **ses répliques augmentent toutes seules** quand beaucoup de commandes arrivent,
> et diminuent quand il n'y en a plus.

**Avant de commencer, vérifiez votre machine.** Nous l'avons mesuré sur un Mac avec Apple M5 et Docker avec 6 CPU et 16 GiB : la 31 a demandé
environ 1.4 GiB de mémoire de plus que ce qui était déjà utilisé, 2.7 cœurs au pic et environ 4.4 GB d'images. La 32 en demande
nettement plus. Le détail, et la façon dont cela a été mesuré, se trouve dans
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).
Ce sont les chiffres d'une seule machine : sur la vôtre, ils peuvent être différents.

**Il vous faut en outre :** la leçon 12 terminée, et deux programmes que vous n'avez pas utilisés jusqu'ici :

| Programme | Pour quoi faire | Où il s'installe |
|---|---|---|
| `kind` | Un Kubernetes de pratique qui tourne dans Docker | [kind.sigs.k8s.io, « Installation »](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) |
| `kubectl` | Parler à ce Kubernetes | [kubernetes.io, « Install Tools »](https://kubernetes.io/docs/tasks/tools/) |

Cette leçon n'utilise **pas** l'environnement de la 00 : elle crée son propre cluster, sous le nom `ni-una-orden-perdida`, et le supprime
à la fin, que cela se passe bien ou mal. Si vous ne connaissez pas Kubernetes, lisez d'abord
[`conceptos/antes-de-empezar/08-kubernetes.md`](../conceptos/antes-de-empezar/08-kubernetes.md).

## La nouveauté du jour, en une phrase

Dans la 12, vous avez mis `replicas: 3` à la main : trois répliques en permanence, qu'il y ait beaucoup de travail ou aucun. Aujourd'hui, vous laissez cette décision à
Kubernetes, avec un **HPA** (*HorizontalPodAutoscaler*) : une pièce qui regarde combien de CPU utilisent les répliques d'int-rest et
en ajoute quand elles approchent de leur cible, ou en retire quand il y en a trop. Pour qu'il puisse mesurer, chaque réplique doit **réserver**
de la CPU : le HPA mesure en pourcentage de ce qui est réservé.

**L'image :** les caisses d'un supermarché. Quand il y a peu de monde, une seule est ouverte. Le gérant regarde les files et, quand chaque
caissière sert plus que sa part, il en ouvre une autre, jusqu'aux quatre dont il dispose. Quand le magasin se vide, il les
ferme une à une. Mais pour savoir si une caissière « est à 150 % », il faut d'abord dire quelle est la part de chacune. C'est cela que
l'on réserve.

---

## 1. Préparez votre dossier

```bash
cd lecciones/31-de-laboratorio-a-kubernetes
cp -R inicio mio
```

Avant d'écrire, lisez ce qui est déjà fait :

| Fichier | Ce que c'est | L'écrivez-vous ? |
|---|---|---|
| `int-rest/int-rest.camel.yaml` | La route d'int-rest de la 12, **sans une lettre de changée**, avec ses mappings et ses cas de test de référence | Non |
| `Dockerfile` | Du dossier à une image, par étapes : `camel export` en fait un projet Camel Quarkus, il est compilé et il en sort une image pour la JVM (et une autre native, pour la 32). Tout se passe dans des conteneurs : vous n'installez ni Java ni Maven | Non, lisez-le |
| `cluster/` | Le cluster kind, metrics-server (ce qui mesure la CPU de chaque réplique), l'API de commandes et un client qui envoie la charge | Non |
| `mio/k8s/int-rest.yaml` | Le Deployment (les répliques) et le Service (le nom fixe qui répartit entre elles) | **Oui, TODO 1 et 2** |
| `mio/k8s/hpa.yaml` | Le HPA | **Oui, TODO 3 et 4** |

Remarquez deux choses dans le Dockerfile et dans le Deployment. La configuration ne va pas dans un `application.properties` : ce qui se
décide à la compilation va dans le `pom.xml` et ce qui se décide à l'exécution va dans des variables d'environnement du Deployment
(`QUARKUS_SHUTDOWN_TIMEOUT`). Et l'image n'est téléchargée d'aucun registre : `kind load` la charge sur le nœud, c'est pour cela qu'il est écrit
`imagePullPolicy: Never`.

## 2. Prédisez

Répondez sur papier :

1. Si une réplique d'int-rest réserve 200 millicœurs de CPU (le cinquième d'un cœur) et en utilise 300, à quel
   pourcentage est-elle ? Avec une cible de 50 %, combien de répliques le HPA voudrait-il ?
2. Si la réplique ne réserve **pas** de CPU, par rapport à quoi le HPA calcule-t-il le pourcentage ?
3. La charge dure deux minutes puis s'arrête. Quand le HPA retire-t-il les répliques en trop, selon vous ?

Regardez l'état actuel (cela prend quelques minutes la première fois : il télécharge des images et construit) :

```bash
./verifica inicio
```

Les premières coches sortent vertes : le cluster est créé, int-rest est construit avec `camel export` et la commande 1001 arrive à
l'API du cluster. Puis celle de la CPU réservée sort en rouge, et Kubernetes **rejette** le HPA : il lui manque le maximum de
répliques.

## 3. Réservation, limite et cible

### TODO 1 et 2 : ce que réserve chaque réplique, et sa limite

Dans `mio/k8s/int-rest.yaml`, à l'intérieur du conteneur, ajoutez `resources` :

```yaml
resources:
  requests:            # lo que la copia RESERVA en el nodo
    cpu: 200m
    memory: 256Mi
  limits:              # el techo
    cpu: "1"
    memory: 512Mi
```

| | Ce que cela signifie | En cas de dépassement |
|---|---|---|
| `requests` | Kubernetes ne place la réplique que sur un nœud qui a cela de libre, et le HPA mesure par rapport à cela | — |
| `limits.cpu` | On ne lui donne pas plus d'un cœur | Elle est bridée |
| `limits.memory` | On ne lui donne pas plus de 512 MiB | Kubernetes redémarre la réplique |

`200m` se lit « 200 millicœurs » : un cœur vaut `1000m`. Avec `200m` réservés, une réplique qui utilise `100m` est à 50 %.

### TODO 3 et 4 : le HPA

Dans `mio/k8s/hpa.yaml`, sous `scaleTargetRef` :

```yaml
minReplicas: 1
maxReplicas: 4
metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 50
```

Toutes les 15 secondes (la période que Kubernetes applique par défaut ; elle peut se configurer), le HPA fait ce calcul, et
le maintient entre le minimum et le maximum :

```
copias que quiere = copias que hay × (CPU promedio ÷ meta), redondeado hacia arriba
ejemplo: 1 copia al 150 % con meta de 50 % → quiere 3
```

Le maximum n'est pas un détail : c'est ce qui empêche une grosse charge de dévorer le nœud entier. Et le `behavior.scaleDown`
que contient déjà le fichier fait qu'il redescend au bout de 30 secondes sans charge, pour que vous le voyiez dans le cours. Par défaut, il attend
5 minutes, pour ne pas monter et descendre à tout bout de champ.

## 4. Cassez-en une exprès

Avec vos fichiers terminés, retirez `cpu: 200m` de `requests` (laissez la mémoire) et lancez `./verifica`.

Avant cela, prédisez : le HPA est bien écrit et Kubernetes l'accepte. Passe-t-il à l'échelle ?

Non. Le verifica dit qu'int-rest ne réserve pas de CPU, puis que le HPA n'arrive pas à la lire : si vous l'interrogez vous-même
(`kubectl get hpa`), la cible apparaît comme `<unknown>/50%`. Le HPA n'est pas cassé : il lui manque de quoi mesurer. **Un
pourcentage sans base n'est pas un nombre.** Remettez la ligne.

## 5. Vérifiez

```bash
./verifica
```

`verifica` crée le cluster, construit int-rest, le déploie avec **vos** fichiers, envoie la commande 1001 comme contrôle,
puis une charge de 150 commandes par seconde pendant deux minutes. Il compte les répliques **prêtes** du Deployment (pas ce que
dit la route), contrôle que le Service a réparti entre elles et que l'API a répondu à toutes les commandes. Ensuite, il attend qu'elles
redescendent. Sur la machine de `CAPACIDAD.md`, cela a pris environ 4 minutes ; la vôtre le dira. Elle est terminée quand vous voyez les coches vertes :

```
✅ Clúster kind «ni-una-orden-perdida» creado (Kubernetes …)
✅ int-rest salió de camel export a una imagen de Camel Quarkus, y está en el clúster junto con la API
✅ Kubernetes aceptó mio/k8s/int-rest.yaml (el Deployment y el Service)
✅ Control: la orden 1001 pasó por int-rest y la API la registró una vez (la ruta no cambió ni una letra)
✅ Cada copia de int-rest reserva CPU: requests.cpu=200m
✅ Kubernetes aceptó mio/k8s/hpa.yaml
✅ El HPA escala a int-rest entre 1 y 4 copias, con meta de CPU de 50 %
✅ El HPA ya lee la CPU de int-rest: (…) % de lo reservado, sin carga
✅ Con carga, el HPA subió int-rest de 1 a (…) copias listas (máximo 4)
✅ (…) copias de int-rest atendieron órdenes de la carga: el Service repartió
✅ Las (…) órdenes de la carga llegaron a la API y cada una tuvo respuesta: ninguna se perdió mientras crecía
✅ Sin carga, int-rest volvió a 1 copia(s) en (…) s
```

Dans nos exécutions, le HPA est monté à 4 répliques dans l'une et à 3 dans une autre ; c'est pour cela que le verifica demande « plus que le minimum » et non
un nombre exact. Jusqu'où il monte dépend de votre machine.

Pendant que cela tourne, vous pouvez regarder le HPA travailler dans un autre terminal. Le verifica conserve la clé du cluster dans un fichier
temporaire, pour ne pas toucher à votre configuration de `kubectl` ; vous la demandez à kind :

```bash
kubectl --kubeconfig <(kind get kubeconfig --name ni-una-orden-perdida) get hpa,deploy -w
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et `diff -r mio solucion` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `No encuentro «kind»` ou `«kubectl»` | Installez-le avec le lien du tableau de « Avant de commencer » |
| `kind no pudo crear el clúster` | Vérifiez que Docker est en marche et a de la mémoire libre (voir `CAPACIDAD.md`) |
| `No se pudo construir int-rest` | La première construction télécharge des dépendances : vérifiez votre connexion. Les lignes qui suivent disent où elle s'est arrêtée |
| `Kubernetes rechazó mio/k8s/int-rest.yaml` | C'est une erreur de YAML ou de noms : le message de Kubernetes s'affiche en dessous |
| `int-rest no reserva CPU (resources.requests.cpu)` | Il manque le TODO 1, ou `resources` est resté hors du conteneur |
| `Kubernetes rechazó mio/k8s/hpa.yaml (TODO 3 y 4)` | Il manque `maxReplicas`, ou `metrics` est mal indenté |
| `El HPA debe escalar a int-rest … con un máximo mayor que el mínimo` | Vérifiez `minReplicas`, `maxReplicas` et que la cible est de `type: Utilization` |
| `El HPA no logra leer la CPU de int-rest` | Presque toujours, il manque `requests.cpu`. Kubernetes dit pourquoi dans le même message |
| `Con carga, int-rest se quedó en 1 copia(s)` | La cible n'est pas un pourcentage de ce qui est réservé, ou elle est si haute qu'elle n'est jamais atteinte |
| `Sin carga, int-rest sigue en … copias` | Vous avez changé le `behavior.scaleDown` : remettez-le comme il était |

---

## Ce que cela enseigne au-delà du cours

**La même route, un autre endroit.** int-rest est passé du dossier du laboratoire à une image Camel Quarkus sur
Kubernetes sans que son YAML change. Ce qui a changé, c'est la façon dont il est empaqueté et qui décide du nombre de répliques.

**Passer à l'échelle n'a besoin que d'une mesure et d'une base.** Le HPA ne sait rien des commandes : il connaît la CPU rapportée à ce qui est réservé. Si
la réservation est fausse, la décision de passer à l'échelle l'est aussi.

**Le maximum protège le voisin.** Sans `maxReplicas`, une grosse charge ajoute des répliques jusqu'à ce que le nœud ne tienne plus. Et
rappelez-vous la 24 : si int-bd passait à l'échelle tout seul, le plafond vers l'ERP devrait être pensé avec le **maximum** de répliques, pas avec
celles d'aujourd'hui.

**Passer à l'échelle sans perdre se conçoit, et se teste.** Dans cette exécution, toutes les commandes de la charge ont reçu une réponse
pendant que de nouvelles répliques apparaissaient : le Service n'envoie pas de nouveau travail à une réplique qui ne se dit pas encore prête.
Quand une réplique est retirée, Kubernetes lui accorde une fenêtre de grâce et Quarkus tente de terminer ce qu'elle avait en cours, mais le verifica
ne teste pas cet instant avec des commandes en l'air. Pour être sûr que rien n'est coupé à la descente, il faut le tester avec
de la charge, avec les durées réelles de chaque service.

## Exercice : les caisses du supermarché

1. Sur papier : avec `requests.cpu: 200m`, une cible de 50 % et un maximum de 4, combien de répliques le HPA veut-il si 2 répliques sont à
   180 % ? Et si elles sont à 20 % ?
2. Passez la cible à 80 % et lancez `./verifica`. Est-il monté à moins de répliques ? Combien de temps a-t-il mis à redescendre ?
3. Si demain vous vouliez faire passer int-bd à l'échelle de cette façon, que devriez-vous recalculer de la leçon 24, et avec quel nombre : le minimum
   ou le maximum de répliques ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Un Kubernetes de pratique avec kind, avec des versions fixes, et un cluster qui est supprimé à la fin](../decisiones/35-kubernetes-de-practica-con-kind.md)
- [C'est int-rest qui va sur Kubernetes, exporté avec camel export vers Camel Quarkus, sans changer sa route](../decisiones/36-int-rest-exportado-con-camel-export.md)
- [Le HPA passe à l'échelle selon la CPU, mesurée en pourcentage de ce que chaque réplique réserve](../decisiones/37-el-hpa-escala-por-cpu-reservada.md)

## Révision

1. Que fait un HPA, et que mesure-t-il ?
2. Quelle différence y a-t-il entre `requests` et `limits` ?
3. À quoi sert `maxReplicas` ?
4. Pourquoi le verifica compte-t-il les répliques prêtes du Deployment et non ce que dit la route ?
5. **La question de clôture :** le HPA dit `<unknown>/50%`. Expliquez pourquoi, et ce qui manque au Deployment.

**Suite :** dans la leçon 32, le même int-rest est aussi compilé en natif, et vous mesurez sur votre machine ce qui change :
le temps qu'il met à se construire, à démarrer, et la mémoire qu'il utilise.
