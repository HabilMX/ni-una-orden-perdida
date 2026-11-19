# Leçon 09 — Le fil entre les services

> **Ce que tu vas obtenir :** que chaque commande porte un **identifiant de corrélation** qui l'accompagne de la boutique au routeur, du
> routeur à int-rest et d'int-rest à l'API, pour que tu puisses la retrouver en n'importe quel point du parcours en cherchant
> une seule valeur.

**Avant de commencer, il te faut :** la leçon 08 terminée et l'environnement de la 00 allumé :

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## La nouveauté du jour, en une phrase

Dans la 08, la commande est passée par trois services, et chacun a écrit dans son propre journal. Si demain une boutique réclame « ma
commande n'est pas arrivée », comment rassembles-tu les lignes des trois journaux qui parlent de **cette** commande ? Aujourd'hui, tu lui attaches un fil : un
identifiant que chaque service reçoit et transmet au suivant.

**L'image :** le numéro de suivi d'un colis. Il passe par l'agence, le centre de tri et le livreur, et
à chaque endroit on le note. Avec ce numéro, tu sais où il est passé. Si une agence ne le note pas, à partir de là le colis
devient invisible.

## L'enveloppe, au complet

Dans la 08, tu as vu les en-têtes `ce-*` sans les utiliser. Aujourd'hui, tu les lis :

| En-tête | Ce qu'il dit | Pour la commande 1003 |
|---|---|---|
| `ce-specversion` | Quelle **version** de CloudEvents utilise l'enveloppe | `1.0` |
| `ce-id` | De quel **événement** il s'agit, un seul | `orden-1003-creada` |
| `ce-type` | De quel **type** est l'événement | `mx.tienda.orden.creada` |
| `ce-source` | **Où** cela s'est passé | `/tiendas/T-022` |
| `ce-subject` | **De quoi** il parle : la commande | `1003` |
| `ce-correlationid` | Le **fil** : il relie tout ce qui se passe à cause d'une même requête | c'est la boutique qui le décide, ou le routeur |

`ce-id` et `ce-correlationid` se ressemblent et ne sont pas la même chose. Le `ce-id` nomme **un** événement. Le fil suit une
requête de la boutique à travers tous les services qu'elle touche. Quand une commande génère plusieurs événements (créée, expédiée,
livrée), chacun a son propre `ce-id`, et tous peuvent partager le même fil.

`ce-correlationid` ne fait pas partie des champs de base de CloudEvents : c'est une **extension**, un champ que l'on ajoute à
l'enveloppe. La norme permet d'ajouter des extensions.

---

## 1. Prépare ton dossier

```bash
cd lecciones/09-el-hilo-entre-servicios
cp -R inicio mio
```

## 2. Lis d'où vient le fil

Ouvre `mio/orden-router/orden-router.camel.yaml`. En haut, il y a une étape `choice` :

| Si la boutique… | Le routeur… |
|---|---|
| envoie l'en-tête `X-Correlation-Id` | utilise ce même identifiant comme fil |
| ne l'envoie pas | en invente un nouveau, un UUID (un nombre au hasard si long que, en pratique, il ne se répète pas) |

Et à la fin, le routeur le **renvoie** à la boutique dans la réponse. La boutique a ainsi de quoi demander des nouvelles de sa
commande.

Regarde le commentaire : l'identifiant est conservé dans une variable **avant** de nettoyer les en-têtes. C'est le cœur de la
leçon.

## 3. Prédis

La route de `inicio/` a trois TODO, et pour l'instant aucun service ne transmet le fil au suivant. Réponds sur papier :

1. Si la boutique envoie la commande 1003 avec l'identifiant `mi-hilo-1`, à quel point du parcours se perd-il : dans le routeur, dans
   int-rest ou dans l'API ?
2. La boutique reçoit-elle son identifiant en retour, même s'il se perd en chemin ?

Vérifie-le :

```bash
./verifica inicio
```

Trois marques rouges apparaissent, et l'une dit `La API no recibió el hilo de la 1003 (anotó: correlacion=-)`. Le tiret est la façon dont
l'API écrit « aucun ne m'est parvenu ». Le routeur, lui, a bien noté le fil et la boutique l'a bien reçu en retour : **le fil se
perd dans le premier service qui ne le retransmet pas**, juste après le routeur.

## 4. Écris les trois étapes

Il y a trois TODO, et tous trois reposent sur la même idée : **recevoir le fil et le transmettre**.

**1. Dans le routeur : mets-le dans l'enveloppe.** Dans `mio/orden-router/orden-router.camel.yaml`, là où il est écrit TODO, ajoute un
`setHeader` de nom `ce-correlationid` avec la valeur de la variable `correlacion`. Prends pour modèle le `setHeader`
de `ce-subject`, qui se trouve quelques lignes plus haut.

**2. Dans int-rest : conserve-le avant qu'il ne soit effacé.** Dans `mio/int-rest/int-rest.camel.yaml`, le premier TODO. Ajoute un
`setVariable` appelé `correlacion` avec la valeur `${header.ce-correlationid}`. Il doit se placer **là**, en haut : quelques
étapes plus bas se trouve `removeHeaders`, qui efface tous les en-têtes arrivés.

**3. Dans int-rest : transmets-le à l'API.** Le second TODO. Ajoute un `setHeader` appelé `X-Correlation-Id` avec la valeur
de la variable `correlacion`. Il se place après le `removeHeaders`, à côté des autres en-têtes de l'appel à l'API.

## 5. Suis-le de tes yeux

Depuis le dossier de la leçon, démarre les services et regarde leurs journaux :

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

Dans un **autre** terminal, depuis la racine du cours, joue le rôle de la boutique avec ton propre fil :

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: mi-hilo-1' -d @datos/ordenes/orden-1003.json
```

Cherche `mi-hilo-1` à trois endroits :

| Où | Quoi chercher |
|---|---|
| La réponse de `curl` | L'en-tête `X-Correlation-Id: mi-hilo-1` |
| Les journaux du routeur et d'int-rest | `correlacion=mi-hilo-1` dans leurs lignes |
| Le journal de l'API | `docker compose -f infra/compose.yaml logs api-pedidos \| grep mi-hilo-1` |

Si la même valeur apparaît aux trois endroits, le fil est complet. Envoie maintenant la commande **sans** l'en-tête
`X-Correlation-Id`. Quel identifiant la boutique a-t-elle reçu en retour ? Cherche-le dans les journaux : il doit être le même partout.

Une fois terminé, éteins les services :

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 6. L'erreur la plus courante : le lire trop tard

Fais l'essai : dans int-rest, déplace ton `setVariable` de la corrélation pour qu'il se retrouve **sous** le `removeHeaders`.
Éteins, redémarre et envoie la commande avec `mi-hilo-1`.

La boutique reçoit son identifiant en retour, le routeur le note, mais int-rest écrit `correlacion=` (vide) et l'API,
`correlacion=-`. Quand int-rest est allé lire l'en-tête, `removeHeaders` l'avait déjà effacé. Il n'y a ni erreur ni
alarme : **le fil se coupe en silence**.

D'où la règle : **ce dont tu as besoin de l'appel entrant, conserve-le dans une variable avant de nettoyer**. Les
variables ne s'effacent pas avec les en-têtes. Remets ton étape à sa place.

## 7. Vérifie

```bash
./verifica
```

`verifica` redémarre l'API, démarre les deux services et envoie deux commandes : la 1003 avec son propre fil et la 1001 sans
aucun. Elle est terminée quand tu vois les huit coches vertes :

```
✅ Arrancaron el router e int-rest
✅ La tienda mandó la 1003 y la respuesta final fue 201
✅ El router anotó el hilo tienda-…
✅ int-rest recibió el hilo en el sobre
✅ La API registró la 1003 con el mismo hilo: se puede seguir de punta a punta
✅ La tienda recibió de vuelta su mismo id
✅ Los encabezados del sobre (ce-*) no salieron hacia la tienda
✅ Sin id de la tienda, el router inventó … y llegó igual a la API
```

Là où tu vois `…` apparaît l'identifiant de cette exécution : `verifica` en utilise un nouveau à chaque fois.

La septième contrôle quelque chose que tu n'as pas écrit toi-même : l'enveloppe `ce-*` est destinée aux services de l'intérieur, et le routeur
l'efface avant de répondre à la boutique. Il ne renvoie à la boutique que `X-Correlation-Id`.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et ces deux commandes te disent en quoi elles diffèrent :

```bash
diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml
diff mio/int-rest/int-rest.camel.yaml solucion/int-rest/int-rest.camel.yaml
```

| Ce que tu vois | Ce que tu fais |
|---|---|
| `int-rest no anotó el hilo` | Il manque l'étape 1 dans le routeur, ou l'étape 2 s'est retrouvée sous le `removeHeaders` (étape 6) |
| `La API no recibió el hilo` avec tout le reste en vert | Il manque l'étape 3 : int-rest l'a, mais ne le transmet pas à l'API |
| `El puerto 8080 está ocupado` | Les services sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Exercice : retrouve une commande

Démarre ta version, envoie trois commandes (la 1001, la 1003 et de nouveau la 1001), chacune sans `X-Correlation-Id`, et
note l'identifiant que chacune t'a renvoyé. Ensuite, **avec le seul identifiant de la deuxième**, réponds : de quelle commande s'agissait-il, que lui a
répondu l'API et combien de temps s'est écoulé entre son arrivée au routeur et la réponse de l'API ? Indice : chaque ligne du journal
commence par l'heure.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [CloudEvents en mode binaire comme enveloppe](../decisiones/11-cloudevents-como-sobre.md)
- [Le fil de corrélation voyage avec la commande, mais ne sort pas de la maison](../decisiones/12-el-hilo-de-correlacion.md)

## Révision

1. Quelle différence y a-t-il entre `ce-id` et `ce-correlationid` ?
2. Si la boutique n'envoie pas d'identifiant, qui le pose et sous quelle forme ?
3. Pourquoi int-rest conserve-t-il le fil dans une variable avant le `removeHeaders` ?
4. Qu'a écrit l'API quand le fil ne lui est pas parvenu, et pourquoi est-ce pire qu'une erreur ?
5. À quoi sert-il à la boutique de recevoir son identifiant en retour ?

**Suite :** dans la leçon 10, le routeur n'envoie plus la commande seulement à int-rest : il l'envoie en même temps aux trois
destinations, et le même fil doit arriver à l'API, à l'ERP et au partenaire.
