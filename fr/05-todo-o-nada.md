# Leçon 05 — Tout ou rien

> **Ce que vous allez obtenir :** que, lorsqu'une ligne de la commande est fausse, l'ERP ne garde pas une commande à moitié faite :
> soit tout est enregistré, soit rien ne l'est. Vous y arrivez en ajoutant **une seule étape** à la route de la 04.

**Avant de commencer, il vous faut :** la leçon 04 terminée et l'environnement de la 00 allumé :

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## La nouveauté du jour, en une phrase

Dans la 04, votre route écrit dans l'ERP en plusieurs étapes : l'en-tête, puis chaque ligne et, à la fin, la procédure.
Si une étape du milieu échoue, les précédentes **sont déjà écrites**. Aujourd'hui, vous enveloppez toutes ces étapes dans une
**transaction** : un paquet que l'ERP valide en entier ou annule en entier.

**L'image :** un virement bancaire. On retire d'un compte et on ajoute à l'autre. Si la banque faisait le
retrait et que l'ajout échouait ensuite, l'argent disparaîtrait. C'est pour cela que les deux opérations vont ensemble : soit les deux passent, soit
aucune ne passe.

| Mot | Ce qu'il signifie |
|---|---|
| **Transaction** | Un groupe d'écritures que la base traite comme une seule |
| **Valider** (*commit*) | Quand tout se termine bien, la base enregistre tout le groupe d'un coup |
| **Annuler** (*rollback*) | Si quelque chose échoue, la base efface tout ce que le groupe avait écrit, comme si rien ne s'était passé |

---

## 1. Préparez votre dossier

```bash
cd lecciones/05-todo-o-nada
cp -R inicio mio
```

`inicio/` est la route de la 04 déjà terminée, split et `CALL` compris, plus trois pièces nouvelles fournies toutes faites.

## 2. Faites connaissance avec la commande qui va échouer

Ouvrez `datos/ordenes/orden-1004-renglon-invalido.json`, dans le dossier racine du cours. Elle a deux lignes :

| Ligne de commande | Produit | Quantité | Prix |
|---|---|---|---|
| 1 | Tasse blanche | 1 | 89.50 |
| 2 | Nappe bleue | **0** | 249.00 |

L'ERP n'accepte pas une quantité de 0 : la table `pedido_detalle` a une règle (`CHECK (cantidad > 0)`) qui rejette cette
ligne. C'est comme un système réel, qui ne laisse pas enregistrer « zéro nappe ».

## 3. Prédisez

Votre route écrit d'abord l'en-tête, puis la ligne 1, puis la ligne 2. Répondez sur papier :

1. Quand la commande 1004 arrive, à quelle étape la route échoue-t-elle ?
2. À la fin, que reste-t-il dans `pedido_cabecera` et que reste-t-il dans `pedido_detalle` pour la commande 1004 ?
3. Avec quel statut l'en-tête reste-t-il ?

## 4. Vérifiez-le

Lancez `verifica` avec la route telle qu'elle est fournie, sans rien changer :

```bash
./verifica
```

Vous allez voir **deux marques rouges** :

```
❌ Quedó la cabecera de la 1004 sin su detalle completo: falta la transacción
❌ Quedaron renglones sueltos de la 1004
```

Demandez à l'ERP ce qu'il a gardé :

```bash
docker compose -f ../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle WHERE pedido='1004';"
```

L'en-tête de la 1004 est là, en `RECIBIDO`, avec un total de 89.50, et il a **une** ligne : la tasse. La nappe
n'est jamais entrée et la procédure n'a jamais tourné. C'est une commande dont l'ERP croit qu'elle existe, que personne ne préparera et que
quelqu'un devra retrouver et effacer à la main.

Aviez-vous vu juste ? Remarquez que la boutique, elle, a bien été informée de l'erreur (la route lui a répondu un 500), mais cela ne nettoie pas ce qui
avait déjà été écrit.

## 5. Lisez les pièces nouvelles

Ouvrez `mio/orden-al-erp.camel.yaml`. En haut, à côté de la connexion `erp` que vous connaissez déjà, il y a deux nouveaux beans :

| Bean | Ce qu'il fait | Dans l'image de la banque |
|---|---|---|
| `transacciones` (un `DataSourceTransactionManager`) | Ouvre, valide ou annule la transaction sur la connexion `erp` | Le guichetier qui mène l'opération |
| `todoONada` (une `SpringTransactionPolicy`) | La règle que vous allez utiliser dans la route : « s'il y a déjà une transaction ouverte, rejoins-la ; sinon, ouvres-en une » (`PROPAGATION_REQUIRED`) | La politique de la banque : les deux opérations vont ensemble |

Et dans `mio/dependencias.txt`, il y a deux pièces de plus que Camel télécharge au démarrage : `camel-spring` et `spring-jdbc`. Ce sont
elles qui savent gérer les transactions. Vous n'écrivez pas de Java : tout se déclare dans le YAML.

## 6. Ajoutez l'étape

Cherchez dans la route le commentaire qui dit **TE TOCA** (« à vous »). Une seule étape y prend place :

```yaml
- transacted:
    ref: todoONada
```

`transacted` veut dire : **d'ici jusqu'à la fin de la route, tout se passe dans une transaction**, avec la
règle `todoONada`. Si n'importe quelle étape en dessous échoue, tout ce qui a été écrit depuis ce point est annulé.

Regardez **où** elle se place : avant l'écriture de l'en-tête. Que se passerait-il si vous la mettiez après l'`INSERT` de
l'en-tête ?

Ce patron a un nom dans les EIP : **Transactional Client**. La route ne se contente pas d'envoyer des messages : elle demande à la destination
de traiter plusieurs écritures comme une seule.

## 7. Vérifiez

```bash
./verifica
```

⚠️ Comme dans la 04, `verifica` **vide les tables de l'ERP** avant de tester.

Elle est terminée quand vous voyez les six coches vertes :

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ La orden 1004 (con un renglón de cantidad 0) fue rechazada (500)
✅ No quedó la cabecera de la 1004: se deshizo
✅ No quedó ningún renglón de la 1004
✅ La orden buena 1001 sí quedó completa y REGISTRADO
```

La dernière compte autant que les autres : la transaction ne doit pas gêner les bonnes commandes.

Interrogez de nouveau l'ERP comme à l'étape 4. De la 1004, il ne reste maintenant rien : ni en-tête, ni tasse.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| Les deux mêmes marques rouges qu'à l'étape 4 | L'étape `transacted` est absente, ou placée après l'en-tête |
| `La ruta no arrancó` | Vérifiez l'indentation de l'étape : `transacted` se place au même niveau que les autres étapes |
| `El puerto 8080 está ocupado` | Une route tourne encore : arrêtez-la avec `Ctrl+C` |

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Dans l'ERP, tout ou rien](../decisiones/06-todo-o-nada-en-el-erp.md)

## Révision

1. Qu'est-ce qu'une transaction, et que signifient valider et annuler ?
2. Sans transaction, qu'est-il resté dans l'ERP avec la commande 1004, et pourquoi est-ce un problème ?
3. Pourquoi l'étape `transacted` se place-t-elle avant l'`INSERT` de l'en-tête ?
4. À quoi sert chacun des deux nouveaux beans ?
5. La boutique a reçu un 500. Que devrait-elle faire de cette commande ? *(Pas encore de réponse : dans la troisième partie du
   cours, « Que rien n'échoue », vous déciderez ce qui se retente et ce qui s'écarte.)*

**Suite :** dans la leçon 06, la commande part vers le partenaire commercial, qui ne reçoit ni JSON ni tables : il reçoit un fichier
EDI, un format texte que les entreprises utilisent depuis des décennies pour se parler.
