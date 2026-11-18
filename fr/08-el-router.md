# Leçon 08 — Le routeur

> **Ce que vous allez obtenir :** découper votre intégration en deux services qui tournent séparément : un **routeur** qui reçoit
> la commande et un service **int-rest** qui sait parler à l'API. Vous écrivez l'appel qui les relie, par le nom
> du service et jamais par une adresse.

**Avant de commencer, il vous faut :** les leçons 02 à 07 terminées et l'environnement de la 00 allumé :

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## La nouveauté du jour, en une phrase

Jusqu'ici, chaque leçon était **une** route qui faisait tout : recevoir, traduire et livrer. Avec trois destinations (l'API,
l'ERP et le partenaire), cette route grossirait sans fin. Aujourd'hui, vous la découpez : un service **décide où va** la commande et un autre **sait
la livrer**. Chacun tourne dans son propre conteneur.

```
tienda ──POST /ordenes──▶ [orden-router] ──POST http://int-rest:8080/integrar──▶ [int-rest] ──▶ API de pedidos
```

**L'image :** l'accueil d'un hôpital. La personne à l'accueil n'opère personne : elle demande ce qui vous arrive et vous envoie chez
le spécialiste. Si demain une nouvelle spécialité ouvre, l'accueil reste le même ; il apprend seulement une porte de plus.

---

## 1. Préparez votre dossier

```bash
cd lecciones/08-el-router
cp -R inicio mio
```

Regardez ce que vous avez copié : `mio/` contient **deux dossiers**, un par service.

| Dossier | Service | Ce qu'il contient |
|---|---|---|
| `mio/orden-router/` | Le routeur | Sa route : elle reçoit la commande de la boutique et la passe à int-rest |
| `mio/int-rest/` | int-rest | Sa route et le mapping v2 de la 03 : elle traduit la commande et la livre à l'API |

## 2. Lisez le compose de la leçon

Cette leçon n'utilise pas `herramientas/camel`. Les deux services démarrent ensemble avec leur propre `compose.yaml`, comme
l'environnement de la 00. Ouvrez-le :

```yaml
x-camel: &camel
  image: apache/camel-jbang:4.22.1
  command: ["run", "."]          # corre todos los *.camel.yaml de su carpeta

services:
  orden-router:
    <<: *camel
    volumes:
      - ./${DONDE:-mio}/orden-router:/work
    ports: ["8080:8080"]

  int-rest:
    <<: *camel
    volumes:
      - ./${DONDE:-mio}/int-rest:/work
    # sin ports: no se ve desde tu máquina; por dentro de la red, cualquier servicio lo alcanza
```

Deux choses qui valent la peine d'être regardées :

- **Les deux services sont la même image Camel.** La seule différence est le dossier que chacun monte. `&camel`
  définit la partie commune une fois et `<<: *camel` la réutilise. Pour créer un nouveau service, pas besoin d'écrire un autre
  programme : un autre dossier avec ses routes suffit.
- **Seul le routeur a `ports`.** C'est le seul visible depuis votre machine. int-rest ne se voit pas de l'extérieur, mais
  à l'intérieur du réseau Docker n'importe quel service peut l'appeler ; dans ce cours, le seul qui l'appelle est le routeur.

`DONDE` est une variable qui dit quels dossiers monter : `mio` (le vôtre, par défaut) ou `solucion` (le nôtre).

## 3. Lisez les deux routes

**`mio/int-rest/int-rest.camel.yaml`** est déjà complète. Elle reçoit sur `/integrar`, traduit avec le mapping v2 et appelle
l'API, comme la route de la 03.

**`mio/orden-router/orden-router.camel.yaml`** reçoit sur `/ordenes` et prépare la commande pour la transmettre. Avant
l'appel, elle lui ajoute des en-têtes qui commencent par `ce-` :

| En-tête | Valeur pour la commande 1003 |
|---|---|
| `ce-specversion` | `1.0` |
| `ce-type` | `mx.tienda.orden.creada` |
| `ce-source` | `/tiendas/T-022` |
| `ce-subject` | `1003` |
| `ce-id` | `orden-1003-creada` |

Ils forment l'**enveloppe** de la commande, dans un format standard qui s'appelle **CloudEvents** : ils disent ce qui s'est passé, où et à qui.
Aujourd'hui, vous allez seulement les **regarder** voyager ; dans la 09, ils servent à suivre une commande de service en service.

À la fin de la route, il y a un **TODO** : il manque l'appel à int-rest.

## 4. Prédisez

Avant d'écrire l'appel, répondez sur papier :

1. Si vous envoyez la commande 1003 au routeur tel qu'il est, sans l'appel, que répond-il à la boutique ? Quelque chose arrive-t-il à
   l'API ?
2. Une fois l'appel en place, dans quel ordre vous attendez-vous à voir passer la commande dans les journaux des deux services ?
3. À l'intérieur du conteneur du routeur, vers quelle machine pointe `localhost`, selon vous ?

## 5. Écrivez l'appel

À la place du TODO, ajoutez une étape `to` qui fasse un `POST` vers la route `/integrar` d'int-rest, sur son port 8080.
Appelez-le **par son nom de service**, `int-rest`, comme int-rest appelle l'API par `api-pedidos`. Utilisez
`throwExceptionOnFailure: false` pour que, si int-rest répond par une erreur, le routeur la note au lieu d'échouer :
la ligne suivante du journal dit ce qu'il a répondu.

Si vous êtes bloqué, l'appel d'int-rest à l'API a exactement la même forme.

## 6. Démarrez-le et envoyez-lui une commande

Depuis le dossier de la leçon :

```bash
DONDE=mio docker compose -f compose.yaml up -d
DONDE=mio docker compose -f compose.yaml logs -f
```

La seconde commande affiche les journaux des deux services ensemble, chaque ligne portant le nom de celui qui l'a écrite.
Attendez de voir `Started orden-router` et `Started int-rest`. Dans un **autre** terminal, depuis la racine du cours :

```bash
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @datos/ordenes/orden-1003.json
```

Dans les journaux, vous devriez voir la commande passer par les deux services :

```
orden-router-1  | Orden 1003: se la paso a int-rest
int-rest-1      | Recibí del router la orden 1003 (sobre orden-1003-creada, tipo mx.tienda.orden.creada)
int-rest-1      | La API contestó 201 para la orden 1003
orden-router-1  | Orden 1003: int-rest contestó 201
```

Cela correspond-il à votre prédiction 2 ? La réponse revient par le même chemin : l'API répond à int-rest, int-rest au
routeur et le routeur à la boutique.

Si l'API répond `200` au lieu de `201`, c'est qu'elle avait déjà la commande 1003 de la leçon 03. Redémarrez-la pour qu'elle reparte vide :
`docker compose -f infra/compose.yaml restart api-pedidos`.

## 7. Cassez-le exprès : localhost

Maintenant, la question 3. Dans la route du routeur, remplacez `int-rest` par `localhost` :

```yaml
uri: http://localhost:8080/integrar
```

Éteignez puis redémarrez les services pour qu'ils prennent le changement, et envoyez la commande de nouveau :

```bash
DONDE=mio docker compose -f compose.yaml down
DONDE=mio docker compose -f compose.yaml up -d
```

La boutique reçoit un **404** et le journal du routeur dit `int-rest contestó 404`. Mais int-rest n'a jamais rien reçu.
**Dans un conteneur, `localhost` est ce conteneur lui-même.** Le routeur s'est appelé lui-même, a cherché une route
`/integrar` qu'il n'a pas et s'est répondu « elle n'existe pas ». Sur votre machine, `localhost` est votre machine ; dans chaque conteneur,
c'est le conteneur.

C'est pour cela que les services s'appellent par leur **nom** : Docker sait dans quel conteneur vit `int-rest`, même si demain
il change d'adresse. Remettez le nom et éteignez :

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 8. Vérifiez

```bash
./verifica
```

`verifica` redémarre l'API pour qu'elle reparte vide, démarre les deux services, envoie la commande 1003 et, à la fin, les
éteint. Elle est terminée quand vous voyez les six coches vertes :

```
✅ El router arrancó
✅ int-rest arrancó (la misma imagen, otra carpeta)
✅ La tienda mandó la 1003 al router y la respuesta final fue 201
✅ La API registró la orden 1003: el recorrido tienda → router → int-rest → API se completó
✅ int-rest recibió el sobre CloudEvents de la orden (id orden-1003-creada)
✅ int-rest no se ve desde tu máquina: solo el router lo alcanza
```

La quatrième est confirmée par l'API à partir de son propre registre : il ne suffit pas que le routeur dise avoir transmis la commande.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` vous dit en quoi elles
diffèrent.

| Ce que vous voyez | Ce que vous faites |
|---|---|
| `La tienda esperaba 201 y le llegó 200` | Le routeur a renvoyé la commande sans appeler personne : il manque l'étape `to` |
| `La tienda esperaba 201 y le llegó 404` | L'appel pointe vers `localhost` : revenez à l'étape 7 |
| `El puerto 8080 está ocupado` | Une autre route tourne encore, ou les services de cette leçon : `DONDE=mio docker compose -f compose.yaml down` |
| `El router no arrancó` | `verifica` affiche les dernières lignes du journal : c'est presque toujours l'indentation de la nouvelle étape |

---

## Exercice : un spécialiste de plus

Sans écrire de code, répondez sur papier : si demain vous vouliez un service `int-erp` qui livre la commande à l'ERP
avec la route de la 05, quel dossier créeriez-vous, qu'y mettriez-vous et quelles lignes ajouteriez-vous au `compose.yaml` ?
Lui donneriez-vous `ports` ? Qu'est-ce qui changerait dans le routeur ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Une seule image ; chaque service est un dossier](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)
- [Les services se parlent par leur nom, sur HTTP](../decisiones/10-servicios-por-nombre-sobre-http.md)
- [CloudEvents en mode binaire comme enveloppe](../decisiones/11-cloudevents-como-sobre.md)

## Révision

1. Que fait le routeur et que fait int-rest ? Pourquoi vaut-il mieux les séparer ?
2. Si les deux services utilisent la même image, qu'est-ce qui les distingue ?
3. Pourquoi le routeur appelle-t-il `int-rest` et non `localhost` ?
4. Pourquoi int-rest n'a-t-il pas `ports` dans le compose ?
5. Que disent les en-têtes `ce-type`, `ce-subject` et `ce-id` de la commande 1003 ?

**Suite :** dans la leçon 09, vous suivez une commande de service en service grâce à l'enveloppe CloudEvents, et vous découvrez ce qui
se passe quand un service ne la retransmet pas.
