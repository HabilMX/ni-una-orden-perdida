# Leçon 27 — Ce que la piste d'audit ne doit pas montrer

> **Ce que tu vas obtenir :** que la piste d'audit d'une commande (ce que la base conserve pour diagnostiquer et ce que montre le
> parcours de la 26) ne montre pas qui est la cliente : ni son nom, ni son courriel, ni son téléphone, ni sa rue. Et que,
> masquée, elle serve encore à comprendre ce qui s'est passé. Vers la destination, la commande continue de partir complète.

**Avant de commencer, il te faut :** la leçon 26 terminée et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Dans la 26, tu as construit le parcours : quiconque peut le consulter voit chaque tentative avec ce qui est parti, en entier. Cela
inclut la personne qui a acheté. Aujourd'hui, la commande de test est la 4008, qui contient le nom, le courriel, le téléphone et l'adresse
d'une cliente (inventés, comme tout dans ce cours). La personne du support a besoin de savoir ce qui est arrivé à la commande ;
elle n'a **pas** besoin de savoir où habite la cliente. Aujourd'hui, tu **masques** les données personnelles avant qu'elles n'entrent dans la piste d'audit.

**L'image :** le relevé de ta carte dans l'application de la banque. Il te montre `**** **** **** 0428` : assez
pour que tu saches de laquelle il s'agit, pas assez pour que quelqu'un s'en serve. La banque, elle, a le numéro complet, et l'utilise pour
débiter ; ce qu'elle masque, c'est **ce qui est montré**.

---

## 1. Prépare ton dossier

```bash
cd lecciones/27-lo-que-el-rastro-no-debe-mostrar
cp -R inicio mio
```

Ouvre `datos/ordenes/tramo-2/orden-4008.json` et pointe au crayon chaque donnée qui dit **qui est** la cliente. Ouvre
ensuite `mio/orden-router/reparto.camel.yaml` et cherche dans la route `intentar` le commentaire « `Desde la 27` ». Avant d'être enregistré
dans `payload`, le corps passe par un nouveau mapping :

```
lo que sale ──→ al canal (completo)
            └─→ mappings/rastro/mascara/v1 ──→ variable rastro ──→ payload (tapado)
```

| Pièce | Ce qu'elle conserve |
|---|---|
| Le corps qui part vers le canal | La commande **complète** : l'API, l'ERP et le partenaire en ont besoin pour livrer |
| `payload.cuerpo` | Ce qui est parti, **masqué** |
| `intento.sha256` | L'empreinte de ce qui est **vraiment** parti, sans masque : elle sert toujours à reconnaître les doublons (la 18) |

La route est déjà branchée. Ton travail est le mapping : `mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`. Aujourd'hui, il
renvoie tout tel que c'est arrivé (`$`). Et il a ses cas de test de référence dans `cases/`, comme les mappings de la 03.

## 2. Prédis

Réponds sur papier :

1. Dans le parcours de la 4008, quelles données de la cliente verrais-tu aujourd'hui ? Qui peut consulter ce parcours ?
2. Si tu masques **tout** ce qui concerne la cliente, pourrais-tu encore savoir si son courriel était bien écrit ou si le code postal
   était valide ?
3. La table `orden` conserve elle aussi la 4008 complète. Pourquoi celle-là peut-elle contenir les données ?

Regarde l'état actuel :

```bash
./verifica inicio
```

La première coche sort en rouge : les cas de test de référence attendent les données masquées et le mapping les laisse passer. Et plus bas, la
recherche trouve le nom, le courriel, le téléphone et la rue de la cliente dans la piste d'audit et dans le parcours.

## 3. Masque juste ce qu'il faut

Avant d'écrire, ouvre les trois cas de test de référence : `cases/orden-con-cliente`, `cases/embarque` et `cases/entrega`. Chacun
a son `entrada.json` et son `esperado.json`. Compare-les et remplis ce tableau :

| Donnée | Arrive | Est conservé | Ce que cela dit encore |
|---|---|---|---|
| `cliente.nombre` | `Ana Ejemplo Prueba` | `A. E. P.` | Ses initiales |
| `cliente.correo` | `ana.prueba@example.com` | `a***@example.com` | Si le domaine était valide |
| `cliente.telefono` | `+52 55 0000 0428` | `*** **** **28` | Les deux derniers chiffres, pour le reconnaître |
| `cliente.direccion.calle` | `Calle de Ejemplo 28` | `***` | Rien |
| `recibio` (dans une livraison) | `Ana Ejemplo Prueba` | `A. E. P.` | Ses initiales |
| `tienda`, `renglones`, `cp`, `colonia`… | | Inchangé | Tout ce qui sert à diagnostiquer |

Le mapping a deux TODO.

### TODO 1 : la fonction `$mascara`

En JSONata, on définit une fonction avec `function($v, $modo) { … }` et on la conserve dans une variable avec `:=`. La tienne reçoit
une valeur et un mode, et renvoie la valeur masquée. Voici sa forme, avec chaque mode sous forme de question `? :` :

```
(
  $mascara := function($v, $modo) {
    $not($exists($v)) ? $v
    : $modo = 'iniciales' ? …
    : $modo = 'correo'    ? …
    : $modo = 'telefono'  ? …
    : '***'
  };
  …   (aquí va el TODO 2)
)
```

La première ligne traite un cas que l'on oublie : **si la valeur n'est pas là, qu'elle reste absente.** Une expédition ne contient pas de
client ; sans cette ligne, un `cliente` inventé apparaîtrait.

Les fonctions de JSONata dont tu as besoin pour chaque mode :

| Mode | Idée | Fonctions |
|---|---|---|
| `iniciales` | Découper le nom en mots, prendre la première lettre de chacun avec un point, et les réunir par une espace | `$split($trim($v), ' ')`, `$map(…, function($p) { $substring($p, 0, 1) & '.' })`, `$join(…, ' ')` |
| `correo` | La première lettre, `***@` et ce qui suit l'arobase | `$substring($v, 0, 1)`, `$substringAfter($v, '@')`, et `&` pour réunir des textes |
| `telefono` | `*** **** **` et les deux derniers chiffres | `$replace($v, /[^0-9]/, '')` ne garde que les chiffres ; `$substring(…, -2)` prend les deux derniers |

### TODO 2 : l'appliquer là où il faut

JSONata a un opérateur pour changer **seulement une partie** d'un document et laisser le reste tel quel, celui de
transformation : `~> | ruta | { cambios } |`. Il se lit « dans ce qui se trouve à `ruta`, pose ces changements ».

```
$
  ~> | cliente | {
       "nombre": $mascara(nombre, 'iniciales'),
       "correo": $mascara(correo, 'correo'),
       "telefono": $mascara(telefono, 'telefono')
     } |
  ~> | cliente.direccion | { "calle": $mascara(calle, 'todo') } |
  ~> | $ | { "recibio": $mascara(recibio, 'iniciales') } |
```

Ce sont trois transformations enchaînées : une sur `cliente`, une sur `cliente.direccion` et une à la racine (`$`), pour le
`recibio` d'une livraison. Ce que tu ne nommes pas reste tel quel : c'est pour cela que le code postal et les lignes de commande passent sans être touchés.

Teste ton mapping avant de démarrer quoi que ce soit :

```bash
../../herramientas/probar-mapeo mio/orden-router/mappings/rastro/mascara/v1
```

## 4. Casses-en une exprès

Avec ton mapping terminé, ajoute le code postal à ce qui est masqué, dans la transformation de `cliente.direccion` :
`"cp": $mascara(cp, 'todo')`. Lance `./verifica`.

Avant cela, prédis : la piste d'audit masque maintenant davantage. Est-ce mieux ?

La première coche (le cas de test de référence attendait le code postal) et la dernière sortent en rouge : la piste d'audit ne dit plus le code postal. Si une livraison
échoue parce que le code postal n'existe pas, la piste d'audit ne te permet plus de le voir. **Trop masquer est aussi une erreur** : la piste d'audit existe
pour diagnostiquer, et une piste qui ne dit rien ne sert à rien. Un code postal seul, sans rue ni nom, désigne difficilement la cliente.
Remets-le comme il était.

## 5. Vérifie

```bash
./verifica
```

`verifica` teste tes cas de test de référence, envoie la 4008, attend que ses trois canaux aient répondu, puis **cherche** les
quatre données de la cliente dans la piste d'audit de la base, dans le parcours et dans les journaux de tous les services. Elle est
terminée quand tu vois les sept coches vertes :

```
✅ El mapeo rastro/mascara/v1 pasa sus casos dorados
✅ Arrancaron el router (que tapa los datos personales del rastro), los tres canales y el sondeo
✅ Control: la orden guardada sí tiene los 4 datos personales (la búsqueda encuentra lo que hay)
✅ En el rastro de la base (payload, intentos, acuses, avisos, conflictos): 0 datos personales
✅ GET /ordenes/4008/recorrido: la orden sí sale, y sin sus datos personales
✅ En los logs: la 4008 aparece (…) veces y sus datos personales 0
✅ Lo tapado sirve para diagnosticar: (…)
```

Regarde la troisième. Avant de compter des zéros, le verifica cherche les quatre mêmes données là où elles **doivent** se trouver, dans
la table `orden`, et les y trouve. S'il ne les y trouvait pas, le « 0 » des coches suivantes ne prouverait rien : il se pourrait
que la recherche soit mal écrite. C'est ce qu'on appelle un **contrôle positif** : avant de croire un zéro,
vérifie que la recherche sait trouver. Et lis le zéro avec sa portée : « 0 datos personales » veut dire 0
occurrence des quatre données que cherche le verifica, aux endroits où il les cherche.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata solucion/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`
te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `no pasa sus casos dorados` | Lance `probar-mapeo` comme à l'étape 3 : il te dit quel cas et quel champ ne correspondent pas |
| Le cas `embarque` échoue avec un `cliente` qui ne devrait pas être là | Il manque la première ligne de `$mascara` : si la valeur n'est pas là, qu'elle reste absente |
| `En el rastro de la base hay … apariciones` | Une transformation ne s'applique pas : vérifie le chemin de chaque `~> | … |` |
| `GET /ordenes/4008/recorrido: … de los 4 datos personales a la vista` | Même chose : le parcours lit dans `payload` |
| `Lo que guarda el rastro de la 4008` sans le code postal ou sans le domaine | Tu as trop masqué : le code postal et le domaine du courriel restent |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**On masque ce qui est montré, pas ce qui est utilisé.** Les destinations ont besoin des données complètes pour livrer : le
transporteur a besoin de l'adresse. La piste d'audit sert à diagnostiquer, et elle est lue par plus de personnes qu'il n'en faudrait pour connaître
la cliente. C'est pour cela qu'on masque là et non dans l'envoi.

**Masquer est une conception, pas un effacement.** Les initiales, le domaine du courriel, les deux derniers chiffres et le code postal en disent
assez pour reconnaître et diagnostiquer, et beaucoup moins que la donnée complète. Décider ce qui reste est la partie difficile.
Et masquer des champs n'est pas anonymiser : ensemble, ou avec d'autres sources, certaines données peuvent de nouveau désigner la personne
(l'exercice 2 l'explore). Une piste d'audit réelle exige en outre de décider qui peut la lire et combien de temps elle est conservée.

**L'empreinte reste celle du réel.** La piste d'audit conserve ce qui est masqué, mais l'empreinte de la 18 se calcule sur ce qui est vraiment
parti. On continue ainsi de reconnaître un doublon sans conserver la donnée.

**Avant de croire un zéro, cherche là où la chose se trouve.** Un « 0 datos personales » peut être une piste d'audit propre ou une recherche
qui ne sait pas chercher. Le contrôle positif est ce qui les distingue, et il vaut pour toute mesure qui te donne zéro.

## Exercice : ce qui se voit et ce qui ne se voit pas

1. Demande le parcours de la 4008 (`curl -s localhost:8080/ordenes/4008/recorrido`) et remplis deux colonnes : ce que tu peux
   savoir de la commande et ce que tu ne peux **pas** savoir de la cliente.
2. Dans l'`orden-4008.json`, cherche une autre donnée qui, jointe à celles qui sont restées sans masque, pourrait aider à identifier la
   cliente. La masquerais-tu ? Que perdrais-tu pour diagnostiquer si tu la masquais ?
3. Quelqu'un du support doit appeler la cliente parce que sa livraison a échoué. D'où devrait-il tirer le téléphone : de la
   piste d'audit ou d'ailleurs ? Qui devrait pouvoir le voir ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [La piste d'audit se conserve masquée](../decisiones/31-el-rastro-se-guarda-tapado.md)

## Révision

1. Quelles données de la 4008 sont personnelles, et lesquelles non ?
2. Pourquoi masque-t-on dans la piste d'audit et non dans ce qui part vers la destination ?
3. Que fait l'opérateur de transformation de JSONata, `~> | ruta | { … } |` ?
4. Pourquoi le code postal et le domaine du courriel restent-ils visibles ?
5. **La question de clôture :** le verifica cherche les données dans la table `orden` avant de compter des zéros dans la piste d'audit. À
   quoi cela sert-il, et qu'arriverait-il au test s'il ne le faisait pas ?

**Suite :** dans la leçon 28, l'intégration cesse de parler seulement par HTTP et par fichiers : les événements du cycle de vie de la commande
voyagent par un topic, et chaque intégration les lit avec son propre groupe.
