# Leçon 32 — JVM contre natif, mesuré

> **Annexe facultative.** Elle peut se **lire** sans être exécutée.
>
> **Ce que vous allez obtenir :** construire le même int-rest de deux façons, pour la JVM et comme exécutable natif, et mesurer sur
> votre machine ce qui change : le temps que chacun met à se construire, à être prêt à servir, et la mémoire qu'il utilise. Et
> le mesurer **bien** : que chaque chiffre dise ce qu'il prétend mesurer.

**Avant de commencer, vérifiez votre machine.** Compiler en natif est ce qu'il y a de plus lourd dans tout le cours. Sur la machine où nous l'avons
mesuré (Mac avec Apple M5, Docker avec 6 CPU et 16 GiB), le compilateur est allé jusqu'à utiliser 4.6 GB de mémoire et 4.6 cœurs, et
la construction native a pris 202 secondes à partir de zéro. Le détail se trouve dans
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).

**Il vous faut en outre :** la leçon 31 lue (celle-ci utilise son `Dockerfile`) et, si vous ne connaissez pas le mode natif,
[`conceptos/antes-de-empezar/07-nativo.md`](../conceptos/antes-de-empezar/07-nativo.md). Vous n'avez besoin ni de kind ni de
l'environnement de la 00 : la leçon fournit son propre `compose.yaml` avec l'API, les deux int-rest et un client de charge.

## La nouveauté du jour, en une phrase

Jusqu'à aujourd'hui, chaque service du cours tournait sur la JVM. Camel Quarkus peut aussi se compiler **en natif** : un
exécutable qui démarre sans JVM. On dit beaucoup de choses sur sa rapidité de démarrage et la mémoire qu'il économise. Aujourd'hui, vous ne le croyez pas :
**vous le mesurez**, avec une méthode fixe, sur votre machine. La difficulté nouvelle n'est pas le natif : c'est de **bien mesurer**.

**L'image :** comparer deux voitures. Si vous mesurez l'une avec le moteur déjà chaud et l'autre à froid, ou si pour l'une vous comptez
à partir du démarrage du moteur et pour l'autre à partir du moment où le conducteur monte, le résultat ne dit rien des voitures. Pour
comparer, les deux passent par la même piste, avec la même charge et le même chronomètre.

| Ce qui est mesuré | Avec quoi | Pourquoi ainsi |
|---|---|---|
| Construire | L'horloge, avec `--no-cache` | Qu'aucun ne réutilise ce qui reste de la fois précédente |
| Ce que dit Quarkus | La ligne `started in …` de son journal | Pour la comparer à la suivante |
| `hasta sano` (jusqu'à sain) | Depuis que le conteneur est **demandé** jusqu'à ce que `/q/health/ready` réponde | C'est ce qu'attend celui qui a besoin que la réplique serve |
| `memoria en reposo` (mémoire au repos) | `docker stats`, une fois démarré, sans travail | Ce qu'il occupe en attendant |
| `memoria con carga` (mémoire sous charge) | `docker stats` chaque seconde **pendant** une charge de 100 commandes par seconde, 20 s ; la plus haute | Ce qu'il occupe en travaillant |
| Image | `docker image inspect` | Ce que pèse son téléchargement |

Les deux conteneurs ont la même limite (1 CPU et 512 MiB) et sont mesurés **un à la fois**, pour que l'un ne prenne pas de CPU
à l'autre.

---

## 1. Préparez votre dossier

```bash
cd lecciones/32-jvm-contra-nativo
cp -R inicio mio
```

Votre travail est un script : `mio/medir`. Il construit déjà les deux images, démarre chaque mode, lance la charge et écrit le
tableau dans `mio/cifras.md`. Il lui manque les trois fonctions qui **mesurent**. Ouvrez-le et lisez-le de haut en bas avant d'écrire.

Ouvrez aussi `compose.yaml` : les deux int-rest sortent du même `Dockerfile` de la 31, avec deux `target` différents
(`jvm` et `nativo`), et écoutent sur `8091` et `8092`.

## 2. Prédisez

Écrivez sur papier, **avant** de mesurer :

1. Combien de fois plus vite le natif démarre-t-il, selon vous ? Et combien de mémoire en moins utilise-t-il au repos ?
2. Lequel des deux met le plus de temps à **se construire**, selon vous ?
3. Quarkus dit dans son journal `started in 0.05s`. Est-ce le temps que met la réplique à pouvoir servir une commande ?

Regardez l'état actuel (il construit les deux images à partir de zéro ; sur la machine de `CAPACIDAD.md`, cela a pris environ 7 minutes) :

```bash
./verifica inicio
```

Les images se construisent, démarrent et livrent leur commande à l'API. Mais dans `cifras.md` manquent « `hasta sano` »,
« `memoria en reposo` » et « `memoria con carga` » : les fonctions qui mesurent répondent encore `—`.

## 3. Les trois mesures

### TODO 1 : la mémoire d'un conteneur

`docker stats` la donne ainsi : `145.8MiB / 512MiB`. Demandez une seule lecture, uniquement cette colonne, et gardez le premier
nombre, en MiB :

```bash
memoria() {
  docker stats --no-stream --format '{{.MemUsage}}' "$1" 2>/dev/null | awk '{
    v=$1; n=v+0
    if (v ~ /GiB/) n=n*1024; else if (v ~ /KiB/) n=n/1024
    printf "%.1f\n", n }'
}
```

`n=v+0` convertit `145.8MiB` en `145.8` : awk prend les chiffres du début. Le reste consiste à changer d'unité.

### TODO 2 : jusqu'à sain

Prenez l'heure **avant** de demander le conteneur et interrogez `/q/health/ready` toutes les 50 ms, pendant 60 secondes au plus :

```bash
hasta_sano() {
  local modo=$1 t0 p
  p=$(puerto "$modo"); t0=$(ms)
  C up -d --force-recreate "int-rest-$modo" >/dev/null 2>&1
  for _ in $(seq 1 1200); do
    curl -sf -o /dev/null --max-time 1 "localhost:$p/q/health/ready" && { echo $(( $(ms) - t0 )); return 0; }
    sleep 0.05
  done
  echo "—"
}
```

Pourquoi le `started in` du journal ne suffit-il pas ? Parce que c'est ce que Quarkus dit de lui-même : cela ne compte pas le temps que met
le conteneur à naître. Celui qui attend que la réplique serve attend les deux.

### TODO 3 : la mémoire sous charge

La charge tourne en arrière-plan. **Tant qu'**elle est en vie, relevez la mémoire chaque seconde et gardez la plus haute :

```bash
while kill -0 "$carga" 2>/dev/null; do
  m=$(memoria "$cont"); awk -v a="$m" -v b="$tope" 'BEGIN{exit !(a+0 > b+0)}' && tope=$m
  sleep 1
done
```

`kill -0` ne tue rien : il demande seulement si le processus est toujours en vie. Il va avant le `wait`.

## 4. Cassez-en une exprès

Avec votre `medir` terminé, changez le TODO 3 : effacez le `while` et, après le `wait`, faites **une** seule lecture
(`tope=$(memoria "$cont")`). Avant de le lancer, copiez votre `mio/cifras.md` ailleurs. Lancez `./verifica`.

Avant cela, prédisez : le verifica le détecte-t-il ?

Non : il sort en vert. « `memoria con carga` » est toujours un nombre, et le verifica demande seulement que c'en soit un. Comparez avec la copie
que vous avez gardée : le nouveau chiffre est la mémoire **après** la charge, et il peut sortir plus bas que le vrai. **Une mesure mal
faite n'échoue pas : elle renvoie un nombre et sonne comme un fait.** C'est pour cela que le verifica ne juge vos chiffres contre aucun seuil
(fiche 39), et que la méthode s'écrit à côté du tableau. Remettez le `while`.

## 5. Vérifiez

```bash
./verifica
```

`verifica` exécute votre `mio/medir`, qui construit les deux images à partir de zéro et mesure chacune. Puis il envoie une commande à
chaque image et contrôle dans le journal de l'API qu'elle est arrivée, vérifie que l'image native ne contient pas de JVM et contrôle que `cifras.md`
a été écrit **lors de cette exécution** avec les six chiffres de chaque mode. Elle est terminée quand vous voyez les coches vertes :

```
✅ La imagen jvm arrancó, contestó y su orden (…) llegó a la API
✅ La imagen nativo arrancó, contestó y su orden (…) llegó a la API
✅ Control: la imagen nativa no trae JVM; corre un ejecutable de (…) MB
✅ Cifras de jvm medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
✅ Cifras de nativo medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
```

Les chiffres sont **les vôtres**. Sur la machine où nous l'avons testé, la JVM a été saine en 1 700 ms environ avec environ 108 MiB au
repos, et le natif en 240 ms environ avec environ 16 MiB. En échange, le natif a mis plus de temps à se construire. Sur votre machine, ils seront
différents, et c'est très bien : comparez avec les vôtres, pas avec les nôtres.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et `diff mio/medir solucion/medir` vous dit en quoi elles
diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `El puerto … está ocupado` | Éteignez ce que vous avez laissé tourner et réessayez |
| `Tu mio/medir terminó con error` | Lancez `./mio/medir` seul : il vous dit à quelle étape il s'est arrêté |
| `No existe la imagen ni-una-orden-perdida/int-rest:nativo: no se construyó` | Lisez `mio/build-nativo.log`. S'il a manqué de mémoire, consultez `CAPACIDAD.md` : le `Dockerfile` permet de baisser `MEMORIA_NATIVO`, et cela prend plus de temps |
| `La imagen … no entregó su orden … a la API` | Les lignes qui suivent sont le journal d'int-rest : elles disent pourquoi |
| `Tu medir no escribió mio/cifras.md` ou `no se escribió en esta corrida` | `medir` s'est arrêté avant d'écrire le tableau : lancez-le seul |
| `A las cifras de … les falta (o no es un número): hasta sano` | Il manque le TODO 2, ou `/q/health/ready` n'a jamais répondu en 60 s |
| `… les falta …: memoria en reposo, memoria con carga` | Il manque le TODO 1 (et avec lui, le 3 ne mesure pas non plus) |

---

## Ce que cela enseigne au-delà du cours

**Un chiffre sans sa méthode ne se compare pas.** « Il démarre en 50 ms » peut être ce que le programme dit de lui-même ou ce
qu'attend celui qui l'utilise. Les deux sont vrais et ce n'est pas le même chiffre.

**Le natif échange un coût contre un autre.** Il démarre vite et utilise peu de mémoire, et il met plus de temps à se construire et demande une
grosse machine pour compiler. Il convient là où l'on démarre souvent beaucoup de répliques (comme les pics de la 31) ; dans un
service qui tourne des semaines sans redémarrer, la différence de démarrage ne se remarque presque pas.

**Les chiffres publiés viennent d'une autre machine.** Ceux d'un article, les nôtres et les vôtres viennent de machines différentes.
Ils servent à savoir à quoi s'attendre ; pour décider, on mesure sur la machine où cela va tourner.

**Que le test sorte en vert ne prouve pas que vous avez bien mesuré.** Le verifica peut contrôler qu'il y a un nombre, pas que le
nombre est le bon. C'est la méthode qui le soutient.

## Exercice : la même piste

1. Écrivez dans `mio/cifras.md`, sous le tableau, vos prédictions de l'étape 2 et ce sur quoi vous vous êtes trompé.
2. Lancez `./mio/medir` deux fois de suite. De combien « `hasta sano` » et « `memoria con carga` » changent-ils d'une exécution à l'autre ?
   Qu'est-ce que cela vous dit sur le fait de comparer une seule exécution ?
3. Sur papier : votre équipe a un service qui passe de 1 à 10 répliques lors des pics du matin, et un autre qui tourne
   sans redémarrer tout le mois. Avec **vos** chiffres, lequel compileriez-vous en natif et pourquoi ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [int-rest est exporté avec camel export vers Camel Quarkus, sans changer sa route](../decisiones/36-int-rest-exportado-con-camel-export.md)
- [Le natif se compile avec Mandrel dans un conteneur](../decisiones/38-el-nativo-se-compila-en-un-contenedor.md)
- [JVM contre natif se mesure sur votre machine, avec une méthode fixe et sans seuils](../decisiones/39-se-mide-en-tu-maquina-sin-umbrales.md)

## Révision

1. Qu'est-ce qu'un exécutable natif, et que lui manque-t-il que la JVM possède ?
2. Pourquoi « `hasta sano` » et « started in » sont-ils des chiffres différents ?
3. Pourquoi la mémoire sous charge se lit-elle chaque seconde pendant la charge, et non une fois à la fin ?
4. Pourquoi les deux conteneurs sont-ils mesurés un à la fois et avec la même limite ?
5. **La question de clôture :** quelqu'un vous dit « le natif démarre 30 fois plus vite ». Que lui demandez-vous avant de
   le croire ?

**Le cours se termine ici.** Si vous êtes arrivé jusqu'ici, une commande traverse désormais l'API, l'ERP et le partenaire sans se perdre, et
vous savez où regarder quand quelque chose échoue.
