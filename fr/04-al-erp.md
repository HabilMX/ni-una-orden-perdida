# Leçon 04 — La commande arrive à l'ERP

> **Ce que vous allez obtenir :** enregistrer la commande dans l'ERP de l'entreprise, qui ne reçoit pas du JSON mais des lignes dans des tables :
> un en-tête, une ligne de détail pour chaque poste et, à la fin, une procédure qui vérifie que tout tombe juste.

**Avant de commencer, il vous faut :** la leçon 03 terminée et l'environnement de la 00 allumé :

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## La nouveauté du jour, en une phrase

Jusqu'ici, la destination était une API qui reçoit un JSON complet d'un seul coup. L'ERP est différent : il range
l'information dans des **tables**, comme des feuilles de calcul, et chaque commande occupe **plusieurs lignes dans deux tables**. Aujourd'hui, votre route
découpe la commande en morceaux et l'écrit ligne par ligne.

**Trois mots que vous allez utiliser :**

| Mot | Ce que c'est | Une image |
|---|---|---|
| **ERP** | Le système central où l'entreprise enregistre ce qu'elle vend, achète et facture | Le livre de comptes de l'entreprise |
| **SQL** | Le langage pour parler à une base de données : `INSERT` ajoute une ligne, `SELECT` les consulte | Les instructions que vous dictez à celui qui tient le livre |
| **Procédure stockée** | Un programme qui vit **dans** la base et s'appelle avec `CALL` | Le comptable qui relit la page avant de la signer |

---

## 1. Préparez votre dossier

```bash
cd lecciones/04-al-erp
cp -R inicio mio
```

## 2. Faites connaissance avec les tables de l'ERP

L'ERP a deux tables. Voici à quoi ressemblerait la commande 1001 une fois enregistrée :

**`pedido_cabecera`** — une ligne par commande

| pedido | tienda | fecha | total | estatus |
|---|---|---|---|---|
| 1001 | T-014 | 2026-11-12 10:15:00 | 428.00 | REGISTRADO |

**`pedido_detalle`** — une ligne pour chaque poste de la commande

| pedido | posicion | sku | cantidad | precio |
|---|---|---|---|---|
| 1001 | 1 | SKU-0001 | 2 | 89.50 |
| 1001 | 2 | SKU-0042 | 1 | 249.00 |

La colonne `pedido` du détail est ce qui **relie** chaque ligne à son en-tête. C'est pour cela que l'en-tête s'écrit
en premier : l'ERP n'accepte pas une ligne de détail pour une commande qui n'existe pas encore.

Et l'`estatus` commence à `RECIBIDO`. Il ne passe à `REGISTRADO` que lorsque la procédure `registrar_pedido` vérifie
que la somme du détail est égale au total de l'en-tête. Si vous voulez voir comment elle est faite, elle est dans
`infra/mariadb/01-erp.sql` ; vous n'avez pas besoin de la modifier.

## 3. Lisez le mapping et la route

**Le mapping est déjà fait :** `mio/mappings/erp/pedido/v1/mapeo.jsonata`. Il convertit la commande en un JSON à deux
parties, `cabecera` et `detalle`, qui portent exactement les noms des colonnes. Testez-le comme dans la 03 :

```bash
../../herramientas/probar-mapeo mio/mappings/erp/pedido/v1
```

Regardez la date. La boutique envoie `2026-11-12T10:15:00-06:00` et le mapping la laisse en `2026-11-12 10:15:00`. Il lui
retire le fuseau horaire (`-06:00`) parce que la colonne de l'ERP est de type `DATETIME`, qui conserve la date et l'heure **sans fuseau**.
L'accord avec l'ERP est que cette heure est celle de la boutique ; le type de donnée ne le sait pas, c'est celui qui le lit qui le sait.

**La route :** ouvrez `mio/orden-al-erp.camel.yaml`. Au-dessus de la route, il y a un **bean** appelé `erp` : c'est la connexion à
la base, avec son adresse, son utilisateur et son mot de passe. Les étapes `sql:` l'utilisent avec `dataSource: "#erp"`.

La route fait déjà ceci :

```
POST /ordenes  →  traducir  →  guardar cabecera y detalle en variables  →  INSERT de la cabecera  →  (te toca)
```

Regardez l'`INSERT` de l'en-tête :

```yaml
uri: sql:INSERT INTO pedido_cabecera (pedido, tienda, fecha, total) VALUES (:#pedido, :#tienda, :#fecha, :#total)
```

Chaque `:#nombre` est un emplacement que Camel remplit avec le champ de ce nom **que contient le corps du message**. C'est pour cela que,
juste avant, la route met l'en-tête comme corps avec `setBody`.

Encore une chose : le pilote de MariaDB, la pièce qui apprend à Camel à parler à cette base, n'est pas inclus.
C'est le fichier `dependencias.txt` qui le demande, et `herramientas/camel` le télécharge au démarrage.

## 4. Prédisez

La route de `inicio/` n'écrit que l'en-tête. Avant de la compléter, répondez sur papier :

1. Si vous envoyez la commande 1001 avec la route telle qu'elle est, quelles lignes restent dans chaque table, et avec quel statut ?
2. Si vous ajoutiez le `CALL registrar_pedido` **sans** écrire le détail, que répondrait la procédure ?

Vous pouvez vérifier la première tout de suite. Depuis `mio/`, démarrez la route :

```bash
cd mio
../../../herramientas/camel run orden-al-erp.camel.yaml
```

Dans un autre terminal, depuis le même dossier `mio/`, envoyez la commande et interrogez l'ERP :

```bash
curl -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../../datos/ordenes/orden-1001.json

docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle;"
```

Comparez les deux réponses. **Votre route** a répondu à la boutique `{"pedido":"1001","erp":"REGISTRADO"}`. **L'ERP**
dit autre chose : l'en-tête est en `RECIBIDO` et le détail est vide. La route ne ment pas exprès : sa dernière
étape écrit cette réponse sans rien demander à l'ERP. C'est pour cela que, dans ce cours, ce qui compte est ce qu'a enregistré la
destination, jamais ce que dit la route.

Une commande avec un en-tête et sans détail est une commande à moitié faite. Pour l'ERP, elle existe, mais on ne peut ni la préparer ni la facturer.

## 5. Écrivez le détail et la clôture

Arrêtez la route avec `Ctrl+C`. Dans `mio/orden-al-erp.camel.yaml`, deux commentaires disent **TE TOCA** (« à vous »).

**Le détail.** La liste des lignes se trouve dans `${variable.detalle}`, et il vous faut un `INSERT` pour chacune. C'est pour cela
qu'existe l'étape **`split`** : elle prend une liste, la découpe en ses éléments et exécute ses étapes une fois par élément,
avec cet élément comme corps.

```yaml
- split:
    expression:
      simple:
        expression: "${variable.detalle}"
    steps:
      - to:
          uri: sql:INSERT INTO pedido_detalle (…) VALUES (…)
          parameters:
            dataSource: "#erp"
```

Complétez vous-même les colonnes et les emplacements. Prenez l'`INSERT` de l'en-tête comme modèle ; les colonnes du détail sont
dans le tableau de l'étape 2.

**La clôture.** Après le `split`, appelez la procédure :

```yaml
uri: sql:CALL registrar_pedido(:#pedido)
```

Attention au corps : après le `split`, que contient-il ? L'emplacement `:#pedido` a besoin d'un corps qui possède le champ
`pedido`. Quelle étape de la route a déjà fait cela avant l'`INSERT` de l'en-tête ?

Ce patron a un nom dans les EIP : **Splitter**. Une commande de plusieurs postes se découpe en messages plus petits,
un par poste, parce que c'est ainsi que la destination les reçoit.

## 6. Comparez avec votre prédiction

Relancez la route et envoyez la commande 1001. Avant cela, videz l'ERP pour repartir propre :

```bash
docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "DELETE FROM pedido_detalle; DELETE FROM pedido_cabecera;"
```

Consultez les deux tables comme à l'étape 4. L'en-tête devrait maintenant dire `REGISTRADO` et le détail avoir deux lignes.

Et la question 2 ? Si vous appelez la procédure sans détail, elle répond `El total no cuadra con el detalle` : la somme de
zéro ligne vaut 0, et le total est 428. **L'ERP ne fait pas confiance à ce que vous lui envoyez : il le vérifie.** Comme l'API dans la
03, une destination qui valide, c'est ce qui rend une erreur visible.

## 7. Vérifiez

Arrêtez la route avec `Ctrl+C` et, depuis le dossier de la leçon :

```bash
cd ..
./verifica
```

⚠️ `verifica` **vide les tables de l'ERP** avant de tester : c'est un ERP de pratique, et il repart ainsi de zéro à chaque fois.

Elle est terminée quand vous voyez les six coches vertes :

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ El ERP tiene las 2 cabeceras
✅ El pedido 1001 tiene sus 2 renglones de detalle
✅ El pedido 1003 tiene sus 3 renglones de detalle
✅ El procedimiento marcó los 2 pedidos REGISTRADO
```

`verifica` envoie deux commandes, la 1001 et celle des centimes (1003), et demande à l'ERP ce qu'il a enregistré. Comme toujours, c'est
la destination qui confirme, pas votre route.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `Al pedido 1001 le faltan renglones de detalle` | Le `split` ne s'exécute pas ou il lui manque un emplacement : vérifiez qu'il parcourt `${variable.detalle}` |
| `Hay pedidos sin REGISTRADO` | Il manque le `CALL`, ou le corps ne contient pas `pedido` : revoyez l'étape 5 |
| `No pude hablar con el ERP: ¿corriste la 00?` | Allumez l'environnement : `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | Une route tourne encore : arrêtez-la avec `Ctrl+C` |

---

## Pourquoi on a fait ainsi

Aujourd'hui, vous avez écrit la commande en trois étapes séparées. La fiche qui explique pourquoi cela ne suffit pas, et ce qui a été
décidé, est celle de la leçon suivante : [Dans l'ERP, tout ou rien](../decisiones/06-todo-o-nada-en-el-erp.md).

## Révision

1. Pourquoi une commande occupe-t-elle des lignes dans deux tables, et quelle colonne les relie ?
2. Pourquoi l'en-tête s'écrit-il avant le détail ?
3. Que fait un `split`, et que contient le corps à chaque tour ?
4. D'où Camel tire-t-il la valeur de `:#sku` ?
5. Que vérifie `registrar_pedido` avant de marquer une commande `REGISTRADO` ?

**Suite :** que se passe-t-il si une ligne du milieu est fausse et que l'ERP la rejette ? L'en-tête est déjà écrit et le
détail à moitié. Dans la leçon 05, vous ferez en sorte que l'ERP enregistre **tout ou rien**.
