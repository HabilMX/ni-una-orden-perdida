# Leçon 24 — Ne pas faire tomber la destination

> **Ce que vous allez obtenir :** que l'ERP ne reçoive pas, en même temps, plus d'enregistrements que le plafond que vous lui fixez, même si int-bd
> tourne en trois répliques et que la boutique envoie 30 commandes d'un coup. Ce qui dépasse le plafond attend son tour : ce n'est pas rejeté.

**Avant de commencer, il vous faut :** les leçons 12 et 22 terminées et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Dans la 22, le problème était une destination en panne. Aujourd'hui, c'est l'inverse : la destination va bien, et **c'est vous qui pouvez la faire tomber**. À partir de
cette leçon, le routeur distribue jusqu'à 10 commandes à la fois (nous vous expliquons pourquoi plus bas), et int-bd tourne en 3 répliques,
comme dans la 12. Avec 30 commandes d'un coup et un ERP qui met une seconde et demie à enregistrer chacune, l'ERP en arrive à
avoir 10 enregistrements en cours. Un ERP réel supporte un certain nombre de connexions d'intégration ; passé ce nombre, il devient
lent pour tout le monde, y compris pour les utilisateurs qui saisissent à la main. Aujourd'hui, vous lui posez un **plafond** : le patron s'appelle
*Throttler*.

**L'image :** l'entrée d'un ascenseur d'une capacité de six personnes. Il y a trois portes dans le hall, et à chacune un gardien
qui en laisse passer deux à la fois. Le septième ne s'en va pas : il attend dans sa file que quelqu'un sorte. Si l'immeuble ouvre une
quatrième et une cinquième porte, chacune avec son gardien de deux, dix personnes peuvent arriver à l'ascenseur. **La capacité est par
porte**, et quand on ouvre des portes il faut refaire le calcul.

---

## 1. Préparez votre dossier

```bash
cd lecciones/24-no-tumbar-al-destino
cp -R inicio mio
```

Avant d'écrire, deux choses qui sont déjà faites et que vous devez connaître.

**La distribution traite maintenant 10 commandes à la fois.** Ouvrez `mio/orden-router/reparto.camel.yaml` et lisez le commentaire
de la route `repartir` : son `from` porte `concurrentConsumers: 10`. Jusqu'à la 23, il y avait un seul consommateur, et c'est pour cela que, dans
la 19, nous avons dit que retenter sur place préservait l'ordre. **À partir d'aujourd'hui, cela change :**

| | Jusqu'à la 23 | À partir de la 24 |
|---|---|---|
| Commandes distribuées à la fois | 1 | Jusqu'à 10 |
| Les opérations d'**une** commande | À la file | À la file, comme avant |
| Deux commandes **différentes** | À la file | Peuvent se croiser |
| Deux événements du cycle de vie de la même commande qui se croisent | Cela n'arrivait pas | La machine à états de la 17 y veille : ce qui arrive trop tôt se gare |

Pourquoi ce changement ? Parce qu'avec un seul consommateur, un lot de 30 commandes prend 30 fois le temps de la destination la plus
lente. Le prix à payer est le problème d'aujourd'hui : le routeur peut désormais en envoyer 10 à la fois à une destination.

**L'ERP mesure ce qu'il voit.** Chaque fois que l'ERP enregistre une commande, il note dans la table `muestra_concurrencia` combien
d'enregistrements il avait en cours à cet instant. La preuve du plafond n'est pas ce que vous avez écrit dans la route : c'est le maximum de cette
colonne.

Votre travail se place dans `mio/int-bd/int-bd.camel.yaml`, dans la route `int-bd`, au début de ses étapes. Ouvrez aussi le
`compose.yaml` de la leçon et cherchez `replicas: 3`.

## 2. Prédisez

Répondez sur papier :

1. Rappelez-vous la 12. Avec int-bd en 3 répliques et sans plafond, combien d'enregistrements à la fois l'ERP peut-il voir ? (Indice : le
   routeur en envoie jusqu'à 10.)
2. L'ERP en supporte 6 à la fois. Combien **chaque** réplique d'int-bd doit-elle en laisser passer ?
3. Avec le plafond en place, les 30 commandes mettent-elles plus ou moins de temps à être enregistrées ? En perd-on une ?

Regardez l'état actuel :

```bash
./verifica inicio
```

L'ERP enregistre les 30, et vite. Mais la coche de la concurrence sort en rouge : l'ERP a vu **jusqu'à 10** enregistrements à
la fois, alors que le maximum avec 3 répliques et un plafond de 2 serait 6.

## 3. Posez le plafond

Le TODO se trouve dans la route `int-bd`. Ce que vous écrivez est une seule étape, au début, avant tout le reste :

```yaml
- throttle:
    mode: ConcurrentRequests
    expression:
      constant:
        expression: "…"        # cuántos a la vez, POR COPIA
```

Le nombre, c'est vous qui le calculez, avec le calcul de l'ascenseur :

| Donnée | Valeur |
|---|---|
| Ce que supporte l'ERP | 6 à la fois |
| Répliques d'int-bd (`compose.yaml`) | 3 |
| Plafond par réplique | 6 ÷ 3 = **2** |

Regardez le `mode`. Camel a deux façons de compter, et **celle qu'il applique par défaut n'est pas celle d'aujourd'hui** :

| Mode | Ce qu'il compte | Convient-il pour l'ERP ? |
|---|---|---|
| `TotalRequests` (par défaut) | Combien **entrent** par période, par exemple 2 par seconde | Non : si chaque enregistrement prend 1.5 s, il en entre 2 chaque seconde et ils s'accumulent |
| `ConcurrentRequests` | Combien **sont en cours** : entrés et pas encore servis d'une réponse | Oui : c'est exactement ce que supporte l'ERP |

C'est pour cela que le `mode` s'écrit, même s'il a l'air d'un détail. Sans lui, le plafond existe, mais il compte autre chose.

Et remarquez ce que le plafond ne fait **pas** : il ne rejette pas. La troisième commande qui arrive à une réplique y attend son tour jusqu'à
ce que l'une des deux qui sont en cours reçoive sa réponse. Pour le routeur, cet appel a seulement pris un peu plus de temps : ce n'est pas une erreur, et
il ne le retente pas.

## 4. Cassez-en une exprès

Avec votre plafond terminé, effacez la ligne `mode: ConcurrentRequests` et lancez `./verifica`.

Avant cela, prédisez : le plafond dit toujours « 2 ». L'ERP en voit-il au plus 6 ?

Non. Sans `mode`, Camel utilise `TotalRequests` : chaque réplique laisse **entrer** 2 commandes par seconde, quel que soit le nombre de celles qui
sont encore en cours. Comme chaque enregistrement prend une seconde et demie, ils s'accumulent, et l'ERP en voit de nouveau plus de 6. La coche de
la concurrence sort en rouge. Le nombre était le bon ; ce qui n'allait pas, c'était **ce qu'il comptait**. Remettez la ligne.

## 5. Vérifiez

```bash
./verifica
```

`verifica` démarre int-bd en 3 répliques, envoie 30 commandes d'un coup, puis demande à l'ERP combien d'enregistrements il a vus à
la fois. Elle est terminée quand vous voyez les cinq coches vertes :

```
✅ Arrancaron el router, los tres canales (int-bd en 3 copias) y el sondeo
✅ El ERP registró las 30 órdenes en (…) s
✅ El ERP vio a lo más (…) registros a la vez: no pasó de 3 copias × tope 2 = 6
✅ El tope hizo esperar, no rechazó: las 30 operaciones del ERP aceptadas al primer intento
✅ La API recibió las 30: el tope del ERP no frenó a los otros canales
   Registros por copia de int-bd: (…)
```

La dernière ligne n'est pas une coche : elle vous dit combien d'enregistrements a faits chaque réplique. Les trois ont travaillé.

Comparez la deuxième coche avec celle d'`inicio/`. Avec le plafond, les 30 mettent plus de temps : dans nos exécutions, environ 16 secondes
contre environ 6 ; votre verifica imprime les vôtres. **C'est ce que coûte de ménager l'ERP**, et c'est un prix raisonnable : l'ERP continue de servir les autres.

Regardez vous-même le compte de l'ERP :

```bash
docker compose -f ../../infra/compose.yaml exec erp mariadb -uintegracion -ppractica erp \
  -e "SELECT MAX(en_curso) AS maximo, COUNT(*) AS registros FROM muestra_concurrencia"
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/int-bd/int-bd.camel.yaml solucion/int-bd/int-bd.camel.yaml` vous dit en quoi elles diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `El ERP vio hasta 10 registros a la vez` | Il n'y a pas de `throttle`, ou il s'est retrouvé après l'appel à l'ERP : il va au début des étapes |
| `El ERP vio hasta` plus de 6, mais moins de 10 | Il manque `mode: ConcurrentRequests`, ou le plafond n'est pas 2 |
| `int-bd corre en 1 copia(s) y se esperaban 3` | Vous avez changé le `replicas` du `compose.yaml` : remettez-le à 3 |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**La destination a une limite, même si elle ne le dit pas.** Un ERP, une base de données ou l'API d'un tiers supportent un certain
nombre de connexions. Une intégration qui grandit en répliques et en concurrence sans plafond est la façon la plus courante de faire tomber
un système qui allait bien.

**Le plafond se place là où l'on connaît la destination.** int-bd est le seul à parler à l'ERP : c'est donc lui qui sait combien il
supporte. Mettre le plafond dans le routeur mêlerait à un seul endroit ce qu'il sait de toutes les destinations.

**Le plafond est par réplique, et passer à l'échelle oblige à recalculer.** Si demain int-bd tourne en 5 répliques, le même plafond de 2 en laisse
passer 10. Le calcul est : ce que supporte la destination, divisé par le nombre de répliques qui lui parlent.

**Faire attendre n'est pas rejeter.** Un plafond qui rejette oblige celui qui appelle à retenter, et l'on y gagne peu. Un
plafond qui fait attendre rend seulement l'appel plus lent, et pour le routeur cela reste un « oui ».

## Exercice : l'ascenseur avec plus de portes

1. Sur papier : l'ERP en supporte 6 et int-bd tourne en 3 répliques avec un plafond de 2. Si l'ERP de production en supportait 20 et que vous vouliez
   faire tourner 4 répliques, quel plafond mettriez-vous ? Et avec 6 répliques ? Écrivez le calcul.
2. Dans le `compose.yaml`, passez les répliques d'int-bd à `5`, sans toucher au plafond, et lancez `./verifica`. La première
   coche va sortir en rouge, parce que le verifica en attend 3. Lisez la troisième : combien d'enregistrements à la fois l'ERP a-t-il vus ? A-t-il dépassé
   6 ? Écrivez ce que vous changeriez dans `int-bd.camel.yaml` pour qu'il ne les dépasse pas, et remettez les répliques à 3.
3. À partir d'aujourd'hui, la distribution traite 10 commandes à la fois. Expliquez en deux phrases pourquoi cela ne met pas en désordre les événements
   d'**une** même commande. (Indice : la 17.)

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Un plafond par réplique vers l'ERP, en comptant ce qui est en cours](../decisiones/28-un-tope-por-copia.md)

## Révision

1. Qu'est-ce qu'un Throttler, et quel problème résout-il ici ?
2. Pourquoi le plafond se place-t-il dans int-bd et non dans le routeur ?
3. Quelle différence y a-t-il entre `TotalRequests` et `ConcurrentRequests` ? Pourquoi est-ce le second qui convient aujourd'hui ?
4. Qu'arrive-t-il à une commande qui arrive quand sa réplique en a déjà 2 en cours ?
5. **La question de clôture :** l'ERP en supporte 6 et tourne aujourd'hui avec 3 répliques. Combien d'enregistrements à la fois peut-il voir avec
   5 répliques et le même plafond, et que devriez-vous changer en passant à l'échelle ?

**Suite :** dans la leçon 25, la question est autre : l'API et l'ERP ont déjà accepté et le partenaire n'a pas envoyé son 997.
Quand une commande est-elle **complète** ?
