# Leçon 01 — Ma première route tourne

> **Ce que vous allez obtenir :** lancer une route Camel, prédire ce qu'elle fait avant de le voir et donner un nom à ses trois
> pièces. C'est la seule leçon où vous n'écrivez rien à partir de zéro : la route est déjà faite.

**Avant de commencer, il vous faut :** la leçon 00 terminée et Docker en marche. Rien de plus : vous n'installez ni Java ni Camel.

**Ce qu'est Camel, en une phrase :** un programme qui déplace de l'information d'un système à un autre en suivant les
instructions que vous lui écrivez. Ces instructions s'appellent une **route**. Aujourd'hui, la route déplace un fichier d'un dossier
à un autre, ce qu'il y a de plus simple. Dans les leçons suivantes, la même idée déplacera les commandes d'une
boutique entre des systèmes qui ne se connaissent pas.

⚠️ **Travaillez dans votre dossier personnel** (`~`, par exemple `~/practicas/`), jamais dans `/tmp`. Sur Mac avec colima,
Docker ne voit que les dossiers situés dans votre home. Si vous avez cloné le dépôt ailleurs, la route échoue avec
« Source file does not exist ».

---

## 1. Préparez votre dossier

Tout ce qui est à vous va dans `mio/`. C'est une copie de `inicio/` et git ne la suit pas : vous pouvez donc y casser ce que vous voulez.

```bash
cd lecciones/01-mi-primera-ruta   # entra a la carpeta de la lección
cp -R inicio mio                  # copia inicio/ completa (-R: con todo lo de adentro) y la llama mio/
cd mio                            # entra a tu copia
```

## 2. Lisez la route et prédisez

Ouvrez `primera-ruta.camel.yaml` avec votre éditeur. Elle est écrite en **YAML**, un format texte où l'indentation
(les espaces au début de chaque ligne) indique ce qui est contenu dans quoi, comme des dossiers dans des dossiers. Sans les
commentaires, cela donne :

```yaml
- route:
    id: primera-ruta
    from:
      uri: file:entrada
      parameters:
        include: ".*\\.json"
      steps:
        - log:
            message: "Llegó la orden del archivo ${header.CamelFileName}: ${body}"
        - to:
            uri: file:salida
```

**Avant de la lancer, écrivez vos réponses sur papier** (il n'y a pas de mauvaise réponse ; ce qui compte, c'est de les comparer
ensuite) :

1. Si vous mettez un fichier `orden-1001.json` dans le dossier `entrada/`, que va-t-il s'afficher à l'écran, selon vous ?
2. Où le fichier va-t-il finir ?
3. Que restera-t-il dans `entrada/` quand la route en aura fini avec lui ?

## 3. Lancez-la

Mettez une commande dans `entrada/` et démarrez la route :

```bash
mkdir -p entrada                                     # crea la carpeta entrada/ (si ya existe, no pasa nada)
cp ../../../datos/ordenes/orden-1001.json entrada/   # copia la orden de ejemplo; ../ sube una carpeta
../../../herramientas/camel run primera-ruta.camel.yaml
```

La commande est un fichier **JSON** : du texte avec des paires nom-valeur (`"orden": "1001"`). Ouvrez-le avant de lancer :
c'est ce qui va voyager.

La première fois prend un peu de temps, parce que Docker télécharge l'image de Camel. Quand vous verrez une ligne avec
`Started primera-ruta`, la route sera déjà à l'écoute. Quelques secondes plus tard apparaît l'annonce de la commande. Pour
l'arrêter, appuyez sur `Ctrl+C`.

## 4. Comparez avec votre prédiction

Reprenez vos trois réponses et regardez les dossiers :

```bash
ls salida/       # lista lo que hay en salida/
ls -a entrada/   # -a muestra también lo escondido (lo que empieza con punto)
```

- **L'écran :** le journal dit `Llegó la orden del archivo orden-1001.json:` puis affiche la commande complète. `${header.CamelFileName}`
  a été remplacé par le nom du fichier et `${body}` par son contenu.
- **Le fichier :** il est dans `salida/`, identique à ce qu'il était en entrant.
- **`entrada/` :** il paraît vide, mais `ls -a` montre un dossier caché, `.camel/`, et votre commande est dedans.
  **Camel n'efface pas ce qu'il lit :** il l'écarte pour ne pas le traiter une seconde fois. L'aviez-vous prédit ?

Si une réponse ne correspond pas, notez pourquoi, à votre avis, cela s'est produit. Cette note vaut plus que d'avoir vu juste.

## 5. Donnez un nom à ce que vous avez vu

La route a trois pièces. Nommez-les d'abord par une image, puis par leur nom formel :

| Ce que vous voyez dans le YAML | L'image | Son nom dans les patrons d'intégration (EIP) |
|---|---|---|
| Toute la route : de `from` à `to`, en passant par `log` | **Le tuyau** | *Pipes and Filters* |
| `file:entrada` et `file:salida`, par où l'on entre et l'on sort | **Les stations** | *Message Endpoint* |
| La commande qui voyage, avec son nom de fichier et son contenu | **La lettre** | *Message* |

Les EIP (*Enterprise Integration Patterns*) sont des noms que n'importe quelle équipe d'intégration dans le monde reconnaît. Inutile
de les mémoriser aujourd'hui : chaque leçon en apporte un nouveau, au moment précis où vous l'utilisez.

**Exercice :** dans votre `mio/primera-ruta.camel.yaml`, écrivez au-dessus de chaque partie un commentaire avec son nom
(`# estación`, `# tubería`, `# carta`). Pour la lettre, il y a un détail : elle n'apparaît pas écrite dans le YAML. Pourquoi ?

## 6. Modifiez-la

Modifiez maintenant la route. Après chaque changement, remettez la commande dans `entrada/` et relancez :

1. **Changez le message du journal** pour qu'il dise `Recibí la orden ${header.CamelFileName}`. Avant de lancer : que
   vous attendez-vous à voir ?
2. **Changez la station de sortie** en `file:procesadas`. Quel nouveau dossier vous attendez-vous à voir ?
3. **Mettez un fichier qui ne soit pas un `.json`** dans `entrada/`, par exemple `nota.txt`. La route le prend-elle ? Quelle ligne du YAML
   en décide ?

## 7. Vérifiez

Depuis le dossier de la leçon, pas depuis `mio/` :

```bash
cd ..
./verifica
```

⚠️ `verifica` **vide puis remplit de nouveau** `mio/entrada/` et `mio/salida/` pour tester à partir de zéro. Il ne touche pas à votre route.

Elle est terminée quand vous voyez les cinq coches vertes :

```
✅ La ruta arrancó
✅ El log anunció la orden
✅ La orden llegó a salida/
✅ entrada/ quedó vacía
✅ Salió idéntica a como entró
```

Si vous avez changé la sortie en `procesadas/` à l'étape 6, remettez-la sur `salida/` avant de vérifier.

**Ça ne tourne pas ?** Essayez avec la nôtre : `./verifica solucion`. Si la nôtre sort en vert et pas la vôtre, comparez les deux
avec `diff mio/primera-ruta.camel.yaml solucion/primera-ruta.camel.yaml`. Utiliser la nôtre n'est pas tricher : c'est pour qu'une
erreur d'aujourd'hui ne vous arrête pas dans les leçons suivantes.

## 8. La carte du voyage

**Vous n'avez pas à comprendre cela aujourd'hui.** La carte de ce que vous allez construire est le sommaire du
[README du cours](../README.md) : il suit l'ordre dans lequel voyage la commande, depuis sa création jusqu'à ce qu'elle ne puisse plus se perdre. Chaque
pièce arrive dans sa leçon ; aujourd'hui, vous n'avez besoin que de la route, de l'endpoint et du message.

*Pourquoi Camel :* nous sommes partis de prémisses, pas de goûts. Nous cherchions quelque chose de léger, qui passe à l'échelle par service, qui se définisse par
configuration et dont la licence soit ouverte. À la fin du cours, vous mesurerez vous-même ce que l'on gagne et ce que l'on cède. Le
détail est dans [Pourquoi Camel](../conceptos/por-que-camel.md).

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Apache Camel comme moteur d'intégration](../decisiones/01-apache-camel-como-motor.md)
- [Des routes en YAML, sous leur forme canonique](../decisiones/02-rutas-en-yaml-canonico.md)

## Révision

Sans regarder le YAML, répondez :

1. Que fait `from`, et que fait `to` ?
2. Qu'est-ce que la lettre dans cette route, et pourquoi n'est-elle pas écrite dans le YAML ?
3. Que fait Camel d'un fichier qu'il a déjà lu ?
4. Si vous vouliez que la route ne prenne que les fichiers `.xml`, quelle ligne changeriez-vous ?
5. Vous avez mis `nota.txt` dans `entrada/`. La route l'a-t-elle pris ? Quelle partie du YAML en a décidé ?

**Suite :** dans la leçon 02, la station d'entrée cesse d'être un dossier : la commande arrive par HTTP et repart vers
une API.
