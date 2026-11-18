# Leçon 29 — Ne pas perdre en consommant

> **Ce que vous allez obtenir :** qu'un événement qu'une intégration a lu mais n'a pas terminé (elle a livré la commande, mais n'a pas pu
> laisser son issue) **soit relu**, au lieu d'être tenu pour lu. Et que le lire deux fois ne crée pas la commande deux
> fois dans l'API.

**Avant de commencer, il vous faut :** les leçons 18 et 28 terminées, l'environnement de la 00 allumé et Kafka en marche
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## La nouveauté du jour, en une phrase

Dans la 28, chaque groupe tient son compte de l'endroit jusqu'où il a lu : l'**offset**. Jusqu'à aujourd'hui, ce compte est validé par Kafka tout seul, par le commit automatique des offsets (auto-commit)
(`autoCommitEnable`, activé d'origine), avec ce que le consommateur a déjà lu, qu'il ait terminé ou non. Et quand un événement
échoue au milieu, Camel le note dans le journal et passe au suivant : le compte avance **par-dessus** celui qui a échoué.
L'API a déjà la commande, mais le routeur n'apprend jamais comment cela s'est passé, et personne ne relit cet événement. Aujourd'hui,
l'offset se valide **à la main**, et seulement quand le travail est terminé.

**L'image :** le marque-page d'un livre. Si vous l'avancez chaque fois que vous tournez la page, même si vous vous êtes endormi au
milieu du chapitre, demain vous commencez au chapitre suivant et ce que vous n'avez pas lu, vous ne le lirez plus. Si vous ne le déplacez que
lorsque vous avez **terminé** le chapitre, le pire qui puisse arriver est de relire quelques pages.

```
leer el evento → entregarlo al canal → publicar el desenlace → confirmar el offset
                                                                ↑ si algo falla antes de aquí, se vuelve a leer
```

---

## 1. Préparez votre dossier

```bash
cd lecciones/29-no-perder-al-consumir
cp -R inicio mio
```

Votre travail se place dans les trois ponts : `mio/int-rest/kafka.camel.yaml`, `mio/int-bd/kafka.camel.yaml` et
`mio/int-edi/kafka.camel.yaml`. Les trois se modifient de la même façon.

Avant cela, ouvrez `mio/orden-router/desenlaces.camel.yaml` : le routeur lit **déjà** le topic `desenlaces` de cette façon. Il vous sert de
modèle.

## 2. Prédisez

Répondez sur papier :

1. L'API a reçu la commande 2001 et a répondu `201` à int-rest, mais Kafka n'a pas accepté l'issue qu'int-rest a voulu
   publier. Avec le commit automatique, le routeur apprend-il que la 2001 est arrivée ? Quelqu'un relit-il cet
   événement ?
2. Si vous ne validez l'offset qu'à la fin, et que l'on tue int-rest après la livraison mais avant la validation, que se passe-t-il au
   retour ? La 2001 est-elle créée deux fois dans l'API ? (Indice : la 18.)
3. Si un événement ne peut jamais être terminé, qu'arrive-t-il à ceux qui le suivent dans sa partition ?

Regardez l'état actuel :

```bash
./verifica inicio
```

Le verifica tend un piège à Kafka : il fait en sorte que le topic `desenlaces` n'accepte rien. Il envoie trois commandes, voit que
l'API et l'ERP les reçoivent et que le routeur n'en apprend aucune. Puis il retire le piège. Avec le commit
automatique, le routeur reste sans nouvelles d'**aucune** des 6 opérations : les événements étaient déjà validés.

## 3. Validez quand vous avez terminé

Dans chaque pont, il y a trois TODO.

### TODO 1 : les trois options du consommateur

Dans le `from: kafka:ordenes`, à côté de `groupId` :

```yaml
autoCommitEnable: false      # Kafka ya no confirma solo
allowManualCommit: true      # el puente confirma cada evento cuando terminó con él
breakOnFirstError: true      # si un evento falla, se vuelve a leer ése, no se sigue con el siguiente
```

Les trois vont ensemble :

| S'il manque… | Ce qui se passe |
|---|---|
| `autoCommitEnable: false` | Kafka valide tout seul ce qui est lu, et votre validation à la main ne change rien |
| `allowManualCommit: true` | Camel ne vous donne pas de quoi valider (le TODO 2 ne trouve rien) |
| `breakOnFirstError: true` | Camel passe à l'événement suivant et, en validant celui-là, valide aussi celui qui a échoué |

C'est cette dernière ligne que l'on oublie. Valider un événement dit à Kafka « j'ai lu jusqu'ici » dans sa partition : valider
le suivant couvre celui qui a échoué.

### TODO 2 : conserver de quoi valider

Avec `allowManualCommit`, Camel laisse dans l'en-tête `CamelKafkaManualCommit` l'objet qui valide **cet** événement.
Conservez-le dans une variable, en première étape, parce que la livraison au canal nettoie les en-têtes :

```yaml
- setVariable:
    name: confirmar
    expression:
      simple:
        expression: "${header.CamelKafkaManualCommit}"
```

### TODO 3 : valider à la fin

À la fin des étapes, après le `filter` (autrement dit, quand l'issue est déjà dans Kafka, ou que l'événement n'était pas pour
ce canal), appelez `commit()` sur cette variable. Camel a une étape, `script`, qui évalue une expression uniquement pour ce
qu'elle fait :

```yaml
- script:
    expression:
      simple:
        expression: "${variable.confirmar.commit()}"
```

Avant ce point, non : ce qui est validé n'est plus relu. Répétez les trois TODO dans les deux autres ponts.

## 4. Cassez-en une exprès

Avec vos trois ponts terminés, dans celui d'int-rest, déplacez le `script` du TODO 3 **au-dessus** du `filter`, pour qu'il
valide avant de livrer. Lancez `./verifica`.

Avant cela, prédisez : les trois options sont toujours en place. Ce qui a échoué est-il relu ?

Non. La coche des 6 opérations sort en rouge : le routeur n'apprend rien de celles de l'API. L'événement était déjà
validé quand son issue a échoué : `breakOnFirstError` revient donc à l'offset validé, qui se trouve déjà **après**
lui. **Les options disent comment on valide ; la place du `script` dit quand.** Et c'est le quand qui compte.
Remettez-le à la fin.

## 5. Vérifiez

```bash
./verifica
```

`verifica` fait deux expériences. Dans la première, il tend le piège sur `desenlaces`, envoie la 2001, la 2003 et la 2004, puis
le retire. Dans la seconde, il arrête int-rest, envoie la 2006, la 2008 et la 2009, rend l'API lente (2.5 s par commande),
rallume int-rest et le **tue** au milieu d'une livraison, pour le rallumer ensuite. Elle est terminée quand vous voyez les sept
coches vertes :

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Control: la API tiene (…) y el ERP (…) de las 3, y el router no se enteró de ninguna (sus desenlaces no se pudieron escribir)
✅ Kafka volvió a aceptar y el router se enteró de las 6 operaciones: lo que falló se volvió a leer
✅ En la API se creó cada una UNA vez (3 creaciones para 3 órdenes)
✅ Tras matar int-rest, la API tiene las 3 y las 3 operaciones quedaron aceptadas
✅ Cada una se creó UNA vez en la API (…)
```

La troisième est un **contrôle** : avant de retirer le piège, le verifica vérifie que quelque chose a vraiment échoué. Si le routeur
était déjà au courant des commandes, la quatrième coche ne prouverait rien.

Regardez la troisième : l'API peut avoir 1, 2 ou 3 des commandes. Avec votre version, un événement qui ne peut pas être terminé
**arrête sa partition** : Camel le retente, et ce qui le suit dans cette partition attend. C'est le coût de ne pas
perdre.

La dernière coche a deux formes. Si la coupure d'int-rest a laissé un événement non validé, int-rest l'a relu et
l'API l'a reconnu à sa clé d'idempotence (la 18) : elle a répondu `200`, pas `201`, et n'a rien créé de nouveau. Si la coupure est tombée
juste après la validation, il n'y avait rien à relire. Les deux sont correctes.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et `diff -r mio solucion` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `Kafka no levantó` | Allumez-le à la main avec la commande de « Avant de commencer » |
| `No arrancaron todas las rutas` | Vérifiez le YAML de vos ponts : le journal qui s'affiche en dessous dit quel fichier et quelle ligne |
| `El router se enteró de … de 6 operaciones: … se perdieron` | Il manque l'une des trois options du TODO 1, ou le `script` n'est pas à la fin |
| `Control: esperaba al menos 1 en la API y en el ERP y 0 enteradas en el router` | Si le routeur a bien été informé, le piège n'a pas été posé : relancez le verifica. Si l'API en a 0, vérifiez que le pont livre toujours |
| `En la API hubo … creaciones para 3 órdenes` | L'API en a créé une deux fois : avez-vous changé la clé qu'int-rest lui envoie ? (la 18) |
| `Tras matar int-rest, la API tiene … de 3` | Au retour, int-rest n'a pas relu ce qu'il n'avait pas validé : vérifiez les options du TODO 1 dans son pont |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**« Lu » n'est pas « terminé ».** Le commit automatique dit le premier. Ce qui compte, c'est le second, et seul
le sait celui qui a fait le travail.

**Ce qui se perd n'est pas toujours ce à quoi l'on s'attend.** Dans nos exécutions, tuer le consommateur au milieu n'a pas perdu
d'événements, même avec le commit automatique : à son retour, Kafka lui a donné ce qui n'avait pas encore été validé. Ce qui
s'est bel et bien perdu, c'est l'événement dont l'issue n'a pas pu être écrite. C'est pour cela que le verifica provoque les deux.

**Ne pas perdre oblige à tolérer les doublons.** Si un événement peut être lu deux fois, celui qui le reçoit doit le reconnaître.
La clé d'idempotence de la 18 est ce qui fait que cette leçon fonctionne.

**Ne pas perdre a un coût.** Un événement qui ne peut pas être terminé arrête sa partition. C'est pour cela que le pont laisse une
issue même si le canal ne répond pas (« on ne sait pas ») : celui qui retente la livraison est le routeur, pas le pont.

## Exercice : le marque-page

1. Sur papier, pour chaque cas, dites si l'événement se perd, se répète ou se passe bien, avec le commit automatique et avec la
   vôtre : (a) le canal livre et l'issue est publiée ; (b) le canal livre et l'issue ne peut pas être publiée ;
   (c) on tue le pont après la livraison et avant la publication.
2. Ouvrez `mio/orden-router/desenlaces.camel.yaml`. Où le routeur valide-t-il ? Que se passerait-il s'il validait avant de
   noter l'issue dans la base ?
3. Expliquez pourquoi le pont ne retente pas lui-même la livraison quand le canal ne répond pas. (Indice : la partition.)

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [L'offset se valide à la main, après avoir laissé l'issue, et ce qui échoue est relu](../decisiones/33-el-offset-se-confirma-despues-del-desenlace.md)

## Révision

1. Qu'est-ce que l'offset, et que fait le commit automatique ?
2. À quoi sert chacune des trois options du TODO 1 ?
3. Pourquoi le `commit()` va-t-il à la fin et non au début ?
4. Qu'est-ce qui évite qu'une commande lue deux fois soit créée deux fois dans l'API ?
5. **La question de clôture :** Kafka n'a pas accepté l'issue de la 2001. Expliquez ce qui arrive à cet événement avec
   le commit automatique et avec le vôtre, et ce que votre version coûte à la partition.

**Suite :** dans la leçon 30, la faille est de l'autre côté : le routeur enregistre la commande puis publie. Et si Kafka
est en panne juste à cet instant ?
