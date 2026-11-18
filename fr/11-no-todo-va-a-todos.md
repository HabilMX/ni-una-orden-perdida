# Leçon 11 — Tout ne va pas à tout le monde

> **Ce que vous allez obtenir :** que le routeur décide, commande par commande, si elle va au partenaire ou non, selon **la boutique** d'où elle vient.
> L'API et l'ERP continuent de les recevoir toutes ; le partenaire, seulement les siennes.

**Avant de commencer, il vous faut :** la leçon 10 terminée et l'environnement de la 00 allumé (avec l'ERP recréé dans la
10).

## La nouveauté du jour, en une phrase

Dans la 10, chaque commande allait aux trois destinations. Mais le partenaire commercial n'exploite pas toutes les boutiques : seulement les
**franchises**, la T-501 et la T-502. Si vous lui envoyez la commande d'une boutique en propre, il reçoit une commande qui n'est pas la sienne et
qu'il pourrait préparer. Aujourd'hui, le canal du partenaire **regarde la commande avant de l'envoyer**.

**L'image :** le facteur qui distribue dans un quartier. Il ne dépose pas toutes les lettres dans toutes les maisons : il lit l'adresse de
chacune et décide.

Ce patron s'appelle **Content-Based Router** : la destination dépend du contenu du message.

| | Multicast (la 10) | Content-Based Router (aujourd'hui) |
|---|---|---|
| À qui va-t-elle ? | À toutes les destinations, toujours | Seulement à celles qui remplissent une condition |
| Qui décide ? | Personne : la liste est fixe | Une règle qui lit la commande |

Aujourd'hui, vous utilisez les deux ensemble : le multicast continue de distribuer aux trois canaux, et **à l'intérieur** du canal du partenaire une
condition décide si la commande continue ou non.

---

## 1. Préparez votre dossier

```bash
cd lecciones/11-no-todo-va-a-todos
cp -R inicio mio
```

## 2. Prédisez

Ouvrez le lot de 10 commandes, le dossier `datos/ordenes/lote-10/`, à la racine du cours. Il contient dix commandes et un `LEEME.md`. Sans rien
lancer, ouvrez les commandes, regardez leur champ `"tienda"` et répondez sur papier :

1. Combien des dix viennent d'une franchise (T-501 ou T-502) ?
2. Avec le routeur tel qu'il est aujourd'hui, combien de fichiers attendez-vous dans la boîte aux lettres du partenaire ? Et quand vous aurez terminé ?
3. Combien de commandes l'API et l'ERP devraient-ils avoir ?

Vérifiez celle d'aujourd'hui :

```bash
./verifica
```

Deux marques rouges apparaissent. Le partenaire a **les dix** 850, et l'API et l'ERP en ont dix aussi. Aujourd'hui, le canal du partenaire
envoie tout.

## 3. Lisez le canal du partenaire

Ouvrez `mio/orden-router/orden-router.camel.yaml` et cherchez la route `canal-socio`. Elle a maintenant deux parties :

| Route | Ce qu'elle fait |
|---|---|
| `canal-socio` | **Décide** si la commande va au partenaire. Elle contient le TODO |
| `al-socio` | **Envoie** la commande à int-edi et laisse la ligne de ce qu'il a répondu, comme le canal de la 10 |

Les séparer laisse la décision à un seul endroit : si demain le partenaire change, on touche une ligne et pas l'appel.

## 4. Écrivez la condition

Dans le TODO, remplacez le `to: direct:al-socio` par un `choice`. Vous en avez déjà utilisé un dans la 09, dans le routeur, pour décider
d'où venait le fil :

```yaml
- choice:
    when:
      - expression:
          simple:
            expression: "…tu condición…"
        steps:
          - to:
              uri: direct:al-socio
    otherwise:
      steps:
        # … el canal contesta que no aplica
```

**La condition.** La boutique de la commande est déjà conservée dans `${variable.tienda}`. Dans le langage `simple` de Camel,
`in` demande si une valeur figure dans une liste séparée par des virgules :

```
${variable.tienda} in 'T-501,T-502'
```

**L'otherwise.** Les commandes qui ne viennent pas d'une franchise ne vont pas au partenaire, mais le canal **doit le dire** : un
`setBody` avec `{"canal":"socio","resultado":"no aplica"}`. Si le canal se taisait, la boutique ne saurait pas si le
routeur a décidé de ne pas l'envoyer ou s'il a oublié. « `no aplica` » (sans objet) et « échec » ne sont pas la même chose, et la réponse doit les distinguer.

## 5. Comparez avec votre prédiction

```bash
./verifica
```

Maintenant, le partenaire a quatre 850, ceux des commandes 2002, 2005, 2007 et 2010, et la boutique a reçu « `no aplica` » pour les
six autres. Aviez-vous trouvé 4 ? L'API et l'ERP gardent les dix : la condition ne vit que dans le canal du partenaire.

Si vous obtenez `Socio: … y tiene «850-2002.edi 850-2007.edi»`, votre condition laisse de côté la T-502. Revoyez la liste.

## 6. Vérifiez

Elle est terminée quand vous voyez les cinq coches vertes :

```
✅ Arrancaron el router y los tres canales
✅ API: tiene las 10 órdenes del lote
✅ ERP: tiene las 10 órdenes REGISTRADO
✅ Socio: solo llegaron los 850 de franquicia (850-2002.edi 850-2005.edi 850-2007.edi 850-2010.edi)
✅ El router le dijo a la tienda «no aplica» en las 6 que no son de franquicia
```

Les coches de l'API, de l'ERP et du partenaire comptent **dans la destination** : le registre de l'API, les lignes de l'ERP et les
fichiers de la boîte aux lettres.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` vous dit en quoi elles
diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| Le partenaire a les dix | La condition est absente, ou le `to` est resté hors du `when` |
| Le partenaire en a deux | La liste de la condition ne contient qu'une boutique |
| `Esperaba «no aplica» en 6 respuestas y lo vi en 0` | Il manque le `setBody` de l'`otherwise` |
| `Tu ERP no tiene la columna correlacion` | Recréez l'ERP comme l'indique la 10 |

---

## Exercice : la règle hors de la route

Aujourd'hui, la liste des franchises est **écrite dans la route**. Si le partenaire ouvre une autre franchise, quelqu'un doit
modifier le YAML, le relire et le redéployer pour changer une donnée métier. Déplacez-la vers la **configuration** : une
valeur qui se change sans toucher à la route.

**1.** Dans votre condition, remplacez la liste par une variable d'environnement avec une valeur par défaut :

```
${variable.tienda} in '{{env:FRANQUICIAS:T-501,T-502}}'
```

`{{env:FRANQUICIAS:…}}` veut dire : « utilise la variable d'environnement `FRANQUICIAS` ; si elle n'existe pas, utilise ce qui suit
les deux-points ». Lancez `./verifica` : le résultat doit rester à cinq sur cinq, puisqu'il utilise la valeur par défaut.

**2.** Changez maintenant la règle **sans toucher à la route**. Créez `mio/franquicias.yaml` avec ceci :

```yaml
services:
  orden-router:
    environment:
      FRANQUICIAS: "T-501"
```

C'est un fichier qui **s'ajoute** au compose de la leçon : il ajoute une variable d'environnement au routeur et ne change rien d'autre.

**3.** Videz la boîte aux lettres du partenaire, démarrez les services avec les deux fichiers, envoyez le lot et regardez la boîte aux lettres :

```bash
docker compose -f ../../infra/compose.yaml exec socio-sftp sh -c 'rm -f /home/socio/entrada/*'
(cd mio/int-edi && ../../../../herramientas/confiar-en-socio)
DONDE=mio docker compose -f compose.yaml -f mio/franquicias.yaml up -d
for f in ../../datos/ordenes/lote-10/orden-*.json; do
  curl -s -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @$f; echo
done
docker compose -f ../../infra/compose.yaml exec socio-sftp ls /home/socio/entrada
DONDE=mio docker compose -f compose.yaml down
```

Avant de lancer, prédisez : combien de 850 attendez-vous dans la boîte aux lettres, et combien de réponses avec « `no aplica` » ?

Dans les réponses, ne regardez que la ligne du canal `socio`. L'API et l'ERP avaient déjà ces commandes depuis votre dernier
`verifica` : leurs lignes disent donc autre chose ; pour le partenaire, la boîte aux lettres est partie vide. Une fois terminé, lancez `./verifica`
pour tout remettre en l'état.

**4.** Répondez : qu'est-ce qui a changé et qu'est-ce qui n'a pas changé ? Qui devrait pouvoir changer `FRANQUICIAS` : celui qui programme la route ou celui qui
gère l'activité ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Qui reçoit quoi se décide par le contenu, à un seul endroit](../decisiones/14-quien-recibe-se-decide-en-un-lugar.md)

## Révision

1. Quelle différence y a-t-il entre un Multicast et un Content-Based Router ? Donnez un exemple de cas où vous utiliseriez chacun.
2. Que fait `in` dans la condition `${variable.tienda} in 'T-501,T-502'` ?
3. Pourquoi le canal répond-il « `no aplica` » au lieu de se taire ?
4. Pourquoi la décision vit-elle dans `canal-socio` et l'appel dans `al-socio` ?
5. Que gagnez-vous à déplacer la liste des franchises vers une variable d'environnement ?

**Suite :** dans la leçon 12, un service cesse d'être unique : vous faites tourner plusieurs répliques du même pour qu'il supporte plus
de commandes.
