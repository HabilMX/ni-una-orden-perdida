# Leçon 06 — Construire l'EDI

> **Ce que vous allez obtenir :** convertir la commande dans le document qu'attend le partenaire commercial, un **X12 850**, et
> vérifier avec un lecteur en espagnol qu'il est bien construit. Aujourd'hui, l'environnement n'est pas nécessaire : Docker suffit.

**Avant de commencer, il vous faut :** la leçon 03 terminée. Cette leçon reprend la même idée de mapping et de cas de test de référence.

## La nouveauté du jour, en une phrase

L'API parle JSON et l'ERP parle tables. Le partenaire commercial parle **EDI** : un format texte que les entreprises utilisent
depuis avant l'existence d'internet pour s'envoyer des bons de commande, des factures et des avis d'expédition. Celui qu'utilise
ce partenaire s'appelle **X12**, et le bon de commande est le document **850**.

Voici à quoi ressemble une commande 850 :

```
ISA*00*          *00*          *ZZ*TIENDANORTE    *ZZ*SOCIO01        *261112*1015*U*00401*000001001*0*P*>~
GS*PO*TIENDANORTE*SOCIO01*20261112*1015*1001*X*004010~
ST*850*0001~
BEG*00*SA*1001**20261112~
N1*ST*Tienda T-014*92*T-014~
PO1*1*2*EA*89.50**VN*SKU-0001~
PID*F****Taza blanca~
PO1*2*1*EA*249.00**VN*SKU-0042~
PID*F****Mantel azul~
CTT*2~
SE*9*0001~
GE*1*1001~
IEA*1*000001001~
```

Il n'a pas de noms de champs comme le JSON : **chaque donnée vaut par sa position**. Il paraît illisible, mais aujourd'hui vous allez
apprendre à le lire.

---

## 1. Préparez votre dossier

```bash
cd lecciones/06-armar-el-edi
cp -R inicio mio
```

## 2. Prédisez

Regardez le 850 ci-dessus. Sans rien chercher, répondez sur papier :

1. Il y a un `*` et un `~` sur chaque ligne. Que sépare chacun, selon vous ?
2. Sur quelle ligne se trouve, selon vous, le numéro de la commande (1001) ? Et les lignes de commande ?
3. Les mots `TIENDANORTE` et `SOCIO01` apparaissent deux fois. Sur la première ligne, ils portent des espaces en trop à la fin, et
   sur la deuxième non. Pourquoi, selon vous ?

## 3. Lisez-le avec le lecteur

Le cours fournit un lecteur qui explique un X12 en espagnol. Depuis le dossier de la leçon :

```bash
../../herramientas/leer-x12 ../../datos/edi/850-ejemplo.edi
```

Comparez avec votre prédiction :

- **`~` ferme un segment.** Un segment est comme une ligne du document, et son nom, ce sont les premières lettres
  (`ISA`, `BEG`, `PO1`…).
- **`*` sépare les éléments** d'un segment. Le lecteur les numérote : dans `BEG*00*SA*1001**20261112`, l'élément 03
  est `1001`, le numéro de la commande. Deux `*` à la suite veulent dire que cet élément est vide.
- **Les lignes** de la commande sont les paires `PO1` (quantité, unité, prix et produit) et `PID` (description).

Le document voyage **dans deux enveloppes**, comme une lettre dans un colis :

| Ouvre | Ferme | Ce que c'est |
|---|---|---|
| `ISA` | `IEA` | L'enveloppe extérieure : qui envoie, à qui, et un numéro de contrôle |
| `GS` | `GE` | Un groupe de documents du même type (ici, des bons de commande) |
| `ST` | `SE` | Le document : la commande 850 |

Chaque enveloppe se ferme en répétant son numéro de contrôle, et `SE`, en plus, **compte** combien de segments contient le
document, de `ST` à `SE`. Le partenaire sait ainsi si quelque chose s'est perdu en chemin.

Et les espaces de la question 3 ? L'`ISA` est **à largeur fixe** : il mesure toujours 106 caractères, et chaque donnée occupe une
place exacte. `TIENDANORTE` a 11 lettres et sa place en fait 15 : on le complète donc avec 4 espaces.

À la fin, le lecteur fait six contrôles (la largeur de l'ISA, les numéros de contrôle, le compte du SE…). Si l'un d'eux
échoue, il sort en rouge.

## 4. Le mapping calcule, le gabarit met en place

La route `mio/orden-a-edi.camel.yaml` construit le 850 en deux étapes, chacune avec un seul travail :

| Pièce | Son travail | Où elle vit |
|---|---|---|
| **Le mapping** (JSONata, comme dans la 03) | **Calcule** tout : les remplissages à 15, le contrôle à 9 chiffres (1001 → `000001001`), les dates, les prix à deux décimales, le compte du `SE` et le texte nettoyé | `mio/mappings/edi/850/v1/` · fourni tout fait |
| **Le gabarit** (Mustache) | **Met en place** ce qui arrive déjà calculé, à son endroit dans le document. Il ne calcule rien | `mio/templates/edi/850/v1/850.mustache` · **à vous** |

Testez-le comme dans la 03, et ouvrez `mio/mappings/edi/850/v1/cases/dos-renglones/esperado.json` pour voir quelles données
arrivent au gabarit :

```bash
../../herramientas/probar-mapeo mio/mappings/edi/850/v1
```

**Pourquoi séparer les deux travaux ?** Parce que chacun se teste de son côté. Le calcul a ses cas de test de référence ; le
gabarit n'a qu'à poser chaque donnée à sa place. Si vous mêliez les calculs au gabarit, il n'y aurait aucun
moyen de les tester.

## 5. Terminez le gabarit

Ouvrez `mio/templates/edi/850/v1/850.mustache`. Il contient déjà l'enveloppe, le début du document et le destinataire :

```
ISA*00*          *00*          *ZZ*{{{body.isa.emisor}}}*ZZ*{{{body.isa.receptor}}}*…~
GS*PO*{{{body.gs.emisor}}}*…~
ST*850*{{{body.st.control}}}~
BEG*00*SA*{{{body.beg.orden}}}**{{{body.beg.fecha}}}~
N1*ST*{{{body.tienda.nombre}}}*92*{{{body.tienda.clave}}}~
```

Chaque `{{{ … }}}` est un emplacement que Mustache remplit avec une donnée du mapping. Il vous manque deux parties. Les instructions
complètes sont dans `LEEME.md`, à côté du gabarit.

**Les lignes.** Il vous faut un `PO1` et un `PID` pour chaque ligne de commande, et les lignes se trouvent dans `body.lineas`. Dans Mustache,
une **section** répète ce qu'elle contient une fois par élément d'une liste :

```
{{#body.lineas}}
… lo que va por cada renglón, usando {{{n}}}, {{{cantidad}}}, {{{precio}}}, {{{sku}}} y {{{descripcion}}} …
{{/body.lineas}}
```

À l'intérieur de la section, vous êtes déjà placé sur une ligne, comme à l'intérieur de `renglones.{ … }` en JSONata. Servez-vous du 850
d'exemple et du lecteur pour savoir à quelle position va chaque donnée.

**La clôture.** Après les lignes : `CTT` (combien de lignes), `SE` (le compte des segments et le contrôle du
`ST`), `GE` et `IEA`. Tous les nombres arrivent déjà calculés dans `body.ctt`, `body.se.segmentos` et les contrôles.

Deux règles :

- **N'écrivez pas de commentaires dans le gabarit :** là où il y avait un commentaire, Mustache laisse une ligne vide, et une
  ligne vide est une erreur dans un X12.
- **Utilisez des accolades triples `{{{ }}}`.** À l'étape 7, vous verrez pourquoi.

## 6. Lancez-la et lisez le résultat

Depuis `mio/`, mettez deux commandes dans `entrada/` et démarrez la route :

```bash
cd mio
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json ../../../datos/ordenes/orden-1005-caracteres.json entrada/
../../../herramientas/camel run orden-a-edi.camel.yaml
```

Quand vous voyez `Generé el 850 de la orden 1005`, arrêtez-la avec `Ctrl+C` et lisez ce qui est sorti :

```bash
../../../herramientas/leer-x12 salida/850-1001.edi
diff salida/850-1001.edi ../../../datos/edi/850-ejemplo.edi
```

Si le lecteur donne les six coches vertes et que `diff` n'imprime rien, votre 850 de la commande 1001 est identique à celui d'exemple.

## 7. Les caractères qui cassent un document

Ouvrez `datos/ordenes/orden-1005-caracteres.json`, dans le dossier racine du cours. Ses deux produits ont des noms
gênants :

| Produit dans la commande | Comment il sort dans le 850 |
|---|---|
| `Taza & plato` | `Taza & plato` |
| `Juego 4*6 ~ cristal` | `Juego 4 6   cristal` |

**Prédisez :** pourquoi le second nom a-t-il changé ? Pensez à ce que ferait le partenaire en lisant `PID*F****Juego 4*6 ~ cristal~`.

Le `*` et le `~` sont les séparateurs du document, et le `>` aussi : l'`ISA` les déclare (le `>` est son dernier
élément). S'ils se trouvent à l'intérieur d'une donnée, le partenaire lirait un élément de trop et un segment coupé en deux. C'est pour cela que le
mapping les remplace par des espaces avant qu'ils n'arrivent au gabarit, et que le cas
de test de référence `caracteres-especiales` vérifie qu'il le fait.

Maintenant, le `&`. **Ce n'est pas un séparateur** : X12 l'accepte à l'intérieur d'une donnée, et le partenaire le lirait bien. Son risque est ailleurs, et
il vient du gabarit. Remplacez **seulement** `{{{descripcion}}}` par `{{descripcion}}`, avec des accolades doubles, et relancez la
route. Regardez le `PID` de la commande 1005 :

```
PID*F****Taza &amp; plato~
```

Mustache est né pour faire des pages web, et avec des accolades doubles il « protège » le texte comme si c'était du HTML : le `&` se
transforme en `&amp;`. Dans une page web, c'est ce qu'il faut. Dans un X12, le partenaire recevrait un produit qui s'appelle
« `Taza &amp; plato` ». Les accolades triples disent à Mustache : **pose la donnée telle quelle**. Remettez les accolades triples.

## 8. Vérifiez

Depuis le dossier de la leçon :

```bash
cd ..
./verifica
```

`verifica` vide `mio/entrada/` et `mio/salida/`, génère le 850 des commandes 1001 et 1005, et les contrôle avec le
lecteur. Elle est terminée quand vous voyez les cinq coches vertes :

```
✅ El mapeo del 850 pasa sus casos dorados
✅ El 850 de la 1001 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1005 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1001 es idéntico al de ejemplo
✅ El 850 de la 1005 (con & y *) es el esperado
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/templates/edi/850/v1/850.mustache solucion/templates/edi/850/v1/850.mustache` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `trae &amp;: usa llaves TRIPLES` | Une accolade est restée double : revenez à l'étape 7 |
| `no pasa la revisión del lector` | Lancez `leer-x12` sur ce fichier : le contrôle en rouge dit ce qui manque (presque toujours la clôture) |
| `no es idéntico al de ejemplo` | Comparez-les avec `diff` : c'est en général un élément à une autre position ou un `*` en moins |

---

## Ce que nous avons appris en construisant cette leçon

Deux erreurs réelles apparues pendant sa construction, et pourquoi elles ne sont pas arrivées jusqu'à vous :

- **Une fonction qui « nettoie » peut casser la largeur fixe.** JSONata a `$trim`, qui retire les espaces. Elle réunit aussi
  les espaces répétés en un seul, et cela aurait effacé le remplissage de l'`ISA`. C'est pour cela que le mapping utilise `$pad`, qui
  remplit, et jamais `$trim` sur un champ à largeur fixe.
- **Compter à la main échoue.** En écrivant le 850 d'exemple, le `SE` annonçait 10 segments là où il y en avait 9. Le lecteur l'a
  signalé en rouge. C'est pour cela que le compte est fait par le mapping et contrôlé par un outil, pas par une personne.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [L'EDI se construit avec un gabarit sans logique](../decisiones/07-mustache-sin-logica-para-el-edi.md)

## Révision

1. Dans un X12, que sépare le `*` et que sépare le `~` ?
2. Quelles sont les trois enveloppes du document, et quel segment ouvre et ferme chacune ?
3. Pourquoi l'`ISA` porte-t-il des espaces de remplissage ?
4. Que fait le mapping et que fait le gabarit ? Pourquoi vaut-il mieux ne pas les mêler ?
5. Pourquoi un `*` ne peut-il pas figurer dans le nom d'un produit, et qu'arrive-t-il à un `&` si vous utilisez des accolades doubles ?

**Suite :** dans la leçon 07, le 850 ne reste plus dans `salida/` : il part vers la boîte aux lettres SFTP du partenaire, en veillant à ce
qu'il ne prenne pas un fichier à moitié écrit.
