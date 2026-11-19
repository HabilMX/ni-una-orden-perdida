# Leçon 03 — Traduire la commande

> **Ce que tu vas obtenir :** écrire toi-même le traducteur qui convertit la commande de la boutique en commande de l'API,
> le vérifier avec des cas de test avant de lancer la route, et découvrir pourquoi un ordinateur n'additionne pas les centimes comme
> toi.

**Avant de commencer, il te faut :** la leçon 02 terminée et l'environnement de la 00 allumé :

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## La nouveauté du jour, en une phrase

Dans la 02, le traducteur était déjà fait. Aujourd'hui, c'est toi qui l'écris, et l'API en demande maintenant davantage : pour chaque poste, son **montant**
(quantité × prix), et pour la commande, le **total**. Si le total ne correspond pas **au centime près** aux postes, l'API la
rejette.

**Le traducteur s'appelle un mapping** et il est écrit en **JSONata**, un petit langage pour transformer un JSON en un autre.
Tu vas le lire avant de l'écrire : presque tout se comprend du premier coup.

---

## 1. Prépare ton dossier

```bash
cd lecciones/03-traducir-la-orden
cp -R inicio mio
```

Cette fois, **tu restes dans le dossier de la leçon**, tu n'entres pas dans `mio/` : l'outil de test se lance d'ici.

## 2. Lis le mapping que tu connais déjà

Ouvre `mio/mappings/api/pedido/v1/mapeo.jsonata`. C'est celui de la 02 :

```
{
  "pedido": orden,
  "tienda": tienda,
  "fecha": creada,
  "partidas": [renglones.{
    "posicion": renglon,
    "sku": sku,
    "cantidad": cantidad,
    "precio": precio
  }]
}
```

Il se lit ainsi : **à gauche, le nom que veut l'API ; à droite, d'où il vient dans la commande de la boutique.**
`"pedido": orden` veut dire « le champ `pedido` vaut ce que contient le champ `orden` ».

La ligne étrange est `renglones.{ … }`. Le point veut dire **« pour chacun »** : pour chaque ligne de la commande, construis
un poste avec ces champs. Les crochets extérieurs garantissent qu'il en sorte toujours une liste, même si la commande ne contient
qu'une seule ligne.

Pourquoi le fichier vit-il dans un dossier `v1` ? Parce qu'aujourd'hui tu vas écrire la `v2` **à côté**, sans toucher à la v1. Si
quelque chose se passe mal, la route peut revenir à la version précédente en changeant un seul chiffre.

## 3. Lis les cas de test de référence

Ouvre le dossier `mio/mappings/api/pedido/v2/cases/`. Il contient trois cas, et chacun est une paire de fichiers :

| Fichier | Ce que c'est |
|---|---|
| `entrada.json` | Une commande de la boutique |
| `esperado.json` | Exactement ce que ton mapping doit produire avec cette commande |

C'est ce qu'on appelle des **cas de test de référence** : le résultat correct, écrit avant de programmer. Ils sont le contrôle de l'erreur de
cette leçon : tu n'as à demander à personne si ton mapping est correct, les cas te le disent.

Ouvre `cases/dos-renglones/esperado.json` et compare-le avec ce que produit la v1. Quels champs sont nouveaux ?

## 4. Prédis

Ouvre `cases/centavos/entrada.json`. C'est la commande 1003, celle d'une papeterie :

| Ligne de commande | Quantité | Prix |
|---|---|---|
| Trombone | 3 | 0.10 |
| Élastique | 7 | 0.70 |
| Carnet | 1 | 19.99 |

Avant de continuer, écris sur papier :

1. Quel est le montant du trombone (3 × 0.10) ?
2. Quel est le total de la commande ?

Garde tes réponses : tu en auras besoin à l'étape 6.

## 5. Écris la v2

Ouvre `mio/mappings/api/pedido/v2/mapeo.jsonata`. Il ne contient que quelques instructions et des accolades vides `{ }`.

1. **Copies-y le contenu de la v1**, sans le commentaire du haut.
2. **Ajoute le montant** dans chaque poste. En JSONata, la multiplication est `*`, et à l'intérieur de `renglones.{ … }` tu
   es déjà placé sur une ligne : `cantidad` et `precio` sont donc ceux de cette ligne.
3. **Ajoute le total** de la commande, après `"partidas"`. JSONata a une fonction `$sum( … )` qui additionne une
   liste de nombres. Indice : `renglones.(cantidad * precio)` produit la liste des montants.

Quand tu l'as, teste-le contre les cas de test de référence, depuis le dossier de la leçon :

```bash
../../herramientas/probar-mapeo mio/mappings/api/pedido/v2
```

L'outil exécute ton mapping avec chaque `entrada.json` et compare le résultat avec son `esperado.json`. À la fin, il dit
`Casos: 3 · pasan … · fallan …`. Si un cas échoue, il te montre ce qu'il a obtenu ; ouvre-le à côté de son `esperado.json` et cherche la différence.

La première fois est plus longue, parce que Camel télécharge ce dont il a besoin. Attends que la ligne des cas apparaisse.

## 6. Compare avec ta prédiction

Si tu as écrit le montant sous la forme `cantidad * precio`, le plus probable est que **le cas `centavos` ait échoué**. Regarde
ce qu'il a obtenu :

```
"importe": 0.30000000000000004      ← el clip
"importe": 4.8999999999999995       ← la liga
"total": 25.189999999999998
```

Tu avais prédit 0.30, et tu avais raison. L'ordinateur, non. **Il ne conserve pas les décimales comme tu les écris :** il les conserve
en binaire, et 0.10 en binaire n'a pas de fin exacte, tout comme 1/3 en décimal vaut 0.3333… sans fin. À la multiplication,
ce petit reste se montre.

Pour une personne, 0.30000000000000004 et 0.30 sont la même chose. **Pour l'API, non :** elle compare le total à la somme des
postes au centime près, et s'ils ne correspondent pas, elle rejette la commande avec un **422** : « El total 25.189999999999998 no cuadra
con las partidas, que suman 25.19 ». Dans un vrai système de vente, un centime
d'écart, c'est une facture qui ne tombe pas juste.

La solution est d'**arrondir à deux décimales** avec `$round(numero, 2)`. Applique-la au montant et au total, et teste
de nouveau jusqu'à voir :

```
Casos: 3 · pasan 3 · fallan 0
```

Vois-tu pourquoi les cas de test de référence s'écrivent avant de programmer ? Si tu avais comparé « à l'œil », 0.30000000000000004
ressemble à 0.30 et l'erreur serait allée jusqu'à l'API.

## 7. Passe la route à la v2

Ton nouveau mapping existe, mais la route utilise encore l'ancien. Ouvre `mio/orden-a-api.camel.yaml` et cherche la ligne :

```yaml
uri: jsonata:mappings/api/pedido/v1/mapeo.jsonata
```

Change-la pour qu'elle pointe vers la v2. C'est le seul changement de la route.

Si tu veux la voir tourner, fais comme dans la 02 : depuis `mio/`, démarre la route avec
`../../../herramientas/camel run orden-a-api.camel.yaml` et, dans un autre terminal, envoie la commande 1003
(`-d @../../../datos/ordenes/orden-1003.json`). Demande ensuite à l'API : `curl localhost:8081/pedidos/1003`.

## 8. Vérifie

Arrête la route avec `Ctrl+C` si tu l'as laissée tourner et, depuis le dossier de la leçon :

```bash
./verifica
```

Elle est terminée quand tu vois les quatre coches vertes :

```
✅ El mapeo v2 pasa todos sus casos dorados
✅ La ruta usa la versión 2 del mapeo
✅ La API aceptó la orden 1003 (201)
✅ El pedido quedó con total 25.19
```

Les deux premières contrôlent ton travail ; les deux dernières sont confirmées par **l'API**. Si le mapping passait ses cas mais que la
route restait en v1, l'API recevrait une commande sans total.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/mappings/api/pedido/v2/mapeo.jsonata solucion/mappings/api/pedido/v2/mapeo.jsonata` te dit en quoi elles
diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `Casos: 3 · pasan 2 · fallan 1` sur `centavos` | Il manque l'arrondi : reviens à l'étape 6 |
| `No hay casos en …` | Tu as mal écrit le dossier : il doit se terminer par `pedido/v2` |
| `La API no aceptó la orden 1003: llegó 422` | Le total ne correspond pas au centime près : revois les `$round` de l'étape 6 |
| `El pedido quedó con total ninguno` | La route est restée en v1, qui n'envoie pas de total : reviens à l'étape 7 |
| `El puerto 8080 está ocupado` | Une route tourne encore : arrête-la avec `Ctrl+C` |

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [JSONata pour traduire entre formats](../decisiones/03-jsonata-para-traducir.md)
- [Sans cas de test de référence, pas de mapping](../decisiones/04-casos-dorados.md)

## Révision

1. Dans `"pedido": orden`, quel côté est l'API et quel côté est la boutique ?
2. Que fait le point dans `renglones.{ … }` ?
3. Qu'est-ce qu'un cas de test de référence, et pourquoi s'écrit-il avant le mapping ?
4. Pourquoi 3 × 0.10 ne donne-t-il pas exactement 0.30 sur un ordinateur ?
5. À quoi sert que la v2 vive à côté de la v1 et non par-dessus ?

**Suite :** dans la leçon 04, la commande arrive dans un système qui ne parle pas JSON : l'ERP, qui range tout dans des tables.
