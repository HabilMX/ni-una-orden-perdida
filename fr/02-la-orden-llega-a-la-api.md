# Leçon 02 — La commande arrive à l'API

> **Ce que tu vas obtenir :** qu'une commande de la boutique entre dans ta route par internet et arrive à l'API de commandes, et
> comprendre ce que l'API répond dans chaque cas. C'est la première fois que ton intégration parle à un autre système.

**Avant de commencer, il te faut :** la leçon 01 terminée et l'environnement de la 00 allumé :

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## La nouveauté du jour, en une phrase

Dans la 01, la station d'entrée était un dossier. Aujourd'hui, c'est une **adresse HTTP** : la boutique envoie la commande comme
ton navigateur envoie un formulaire, et ta route la renvoie à l'API de commandes de la même façon.

**HTTP, le minimum :** un programme envoie à un autre une **requête** avec une méthode (`POST` veut dire « voici quelque chose de nouveau »),
une adresse (`/ordenes`), des **en-têtes** (des données sur le message, comme des étiquettes sur une enveloppe) et un **corps**
(la commande). L'autre répond par un **code** à trois chiffres et son propre corps. Pour en savoir plus, il y a la fiche
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/).

---

## 1. Prépare ton dossier

```bash
cd lecciones/02-la-orden-llega-a-la-api
cp -R inicio mio
cd mio
```

## 2. Lis la route

Ouvre `orden-a-api.camel.yaml`. Elle a cinq étapes :

```
POST /ordenes  →  sacar el id de la orden  →  traducir  →  POST a la API  →  anotar lo que contestó
```

| Étape dans le YAML | Ce qu'elle fait |
|---|---|
| `from: platform-http:/ordenes` avec `httpMethodRestrict: POST` | **La station d'entrée.** Elle ouvre l'adresse `/ordenes` ; le paramètre `httpMethodRestrict` fait qu'elle n'accepte que `POST`. Le port 8080 n'est pas fixé par la route : c'est `herramientas/camel` qui l'ouvre |
| `setHeader` avec `name: Idempotency-Key` et l'expression `jsonpath` `"$.orden"` | Extrait le numéro de la commande (`"orden": "1001"`) et le place dans un en-tête appelé `Idempotency-Key`. C'est la **clé** grâce à laquelle l'API reconnaît une commande qu'elle a déjà reçue |
| `to: jsonata:…/mapeo.jsonata` | **Traduit** la commande du format de la boutique au format de l'API. Le traducteur est déjà fait ; dans la 03, c'est toi qui l'écris |
| `removeHeaders: CamelHttp*` | Efface les étiquettes de l'appel entrant, pour qu'elles ne se mélangent pas à celles de l'appel sortant |
| `to: http://api-pedidos:8080/pedidos` | **La station de sortie.** Elle appelle l'API par son nom de service (`api-pedidos`), pas par un numéro d'IP |

Ouvre aussi `datos/ordenes/orden-1001.json` et `mio/mappings/api/pedido/v1/mapeo.jsonata`. Mets-les côte à côte :
quel nom a changé (`renglones` → ?) et lequel est resté le même ?

## 3. Prédis

L'API de commandes n'accepte pas n'importe quoi : elle répond par un code différent selon ce qui lui arrive. Avant de lancer,
écris sur papier le code qu'elle donne, selon toi, dans chaque cas :

| Cas | Ta prédiction |
|---|---|
| Tu envoies la commande 1001 pour la première fois | |
| Tu envoies **la même** commande 1001 une nouvelle fois | |
| Tu envoies une commande sans lignes de commande | |

## 4. Lance-la et envoie-lui une commande

Dans un terminal, démarre la route :

```bash
../../../herramientas/camel run orden-a-api.camel.yaml
```

La première fois est plus longue, parce que Camel télécharge ce dont il a besoin pour parler HTTP. Attends la ligne
`Started orden-a-api`.

Dans un **autre** terminal, depuis le même dossier `mio/`, joue le rôle de la boutique et envoie la commande :

```bash
curl -i -XPOST localhost:8080/ordenes \
  -H 'Content-Type: application/json' \
  -d @../../../datos/ordenes/orden-1001.json
```

- `curl` est un programme qui fait des requêtes HTTP depuis le terminal.
- `-i` te montre le code et les en-têtes de la réponse, pas seulement le corps.
- `-XPOST` est la méthode.
- `-H` ajoute un en-tête.
- `-d @archivo` envoie ce fichier comme corps.

Regarde les deux terminaux. Dans celui de la route apparaissent `Recibí la orden 1001` et `La API contestó …`. Demande maintenant à
l'API ce qu'elle a enregistré ; elle vit sur le port 8081 :

```bash
curl localhost:8081/pedidos/1001
```

## 5. Compare avec ta prédiction

Envoie la même commande une seconde fois et regarde le code. Essaie ensuite une commande sans lignes : copie la commande,
**change son numéro** (`"orden": "1002"`, pour que ce soit une commande nouvelle), laisse les lignes vides et envoie-la.

```bash
cp ../../../datos/ordenes/orden-1001.json sin-renglones.json   # en tu editor: "orden": "1002" y "renglones": []
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @sin-renglones.json
```

| Cas | Ce que répond l'API | Ce que cela signifie |
|---|---|---|
| Première fois | **201** | *Created* : « je l'ai créée » |
| La même commande une nouvelle fois | **200**, avec `ya_existia` | « Je l'avais déjà ; je ne l'ai pas dupliquée ». L'API a reconnu la clé |
| Sans lignes de commande | **422** | « J'ai compris ce que tu as envoyé, mais cela n'a pas de sens » : une commande sans postes ne peut pas être enregistrée |
| Sans la clé `Idempotency-Key` | **400** | « Il te manque quelque chose d'obligatoire » |
| La même clé avec un **autre** contenu | **422** | « Cette clé, tu l'as déjà utilisée avec autre chose ». Regarde le `type` du corps |

Ce tableau est le **contrat de l'API du cours**. Du brouillon de l'IETF pour `Idempotency-Key`, il reprend le 400 sans
clé et le 422 pour clé réutilisée ; le 201 et le 200 sont décidés par cette API, parce que le brouillon ne les fixe pas. Autre
différence : dans le brouillon, la clé est une chaîne entre guillemets (`"1001"`) ; ici, elle va sans guillemets, comme le numéro
de la commande. Une API qui suivrait le brouillon à la lettre la demanderait entre guillemets : avant d'intégrer, on lit son
contrat.

Quand quelque chose se passe mal, l'API répond par un JSON d'erreur de forme standard (*Problem Details*, le type
`application/problem+json`) : il contient `type`, `title`, `status` et `detail`. C'est le `type` qui distingue un 422 d'un autre.

Les deux derniers cas, tu ne les as pas essayés. **Comment ferais-tu pour provoquer un 400 ?** Indice : quelle ligne de la route pose la clé ?
Et si tu envoies la commande 1001 avec une autre quantité ? L'API te répond 422, comme pour la commande sans lignes. Comment distingues-tu
les deux ? Regarde le corps de la réponse. Fais les essais, puis remets la route dans son état d'origine.

Vois-tu pourquoi la commande sans lignes avait besoin d'un autre numéro ? Avec le 1001, tu aurais eu la même clé avec un autre
contenu, et le 422 aurait été dû à la clé, pas aux lignes.

**L'important dans le 200 :** si le réseau flanche et que la boutique renvoie la commande, l'API ne crée pas deux commandes, parce que la clé
lui dit que c'est la même. C'est pour cela que l'identifiant de la commande voyage dès la première station. Dans la leçon 18, tu construiras
cette même protection dans ton propre intégration.

## 6. Vérifie

Arrête la route avec `Ctrl+C` (sinon le port 8080 reste occupé) et, depuis le dossier de la leçon :

```bash
cd ..
./verifica
```

`verifica` redémarre l'API pour qu'elle reparte vide, démarre ta route, envoie la commande deux fois et contrôle. Elle est terminée
quand tu vois les cinq coches vertes :

```
✅ La ruta arrancó
✅ Primer envío: la API creó el pedido (201)
✅ Reenvío: la API lo reconoció y no lo duplicó (200)
✅ La API tiene el pedido 1001 con sus 2 partidas
✅ La API recibió la llave 1001: el id de la orden viajó hasta el destino
```

La dernière est confirmée par **l'API**, pas par ta route : `verifica` lit ce que la destination a noté. Que ta route dise avoir envoyé quelque chose
ne prouve pas que c'est arrivé ; celui qui le prouve, c'est celui qui l'a reçu. Cette idée reviendra souvent dans le cours.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre. Si la nôtre sort en vert et pas la tienne,
`diff mio/orden-a-api.camel.yaml solucion/orden-a-api.camel.yaml` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `El puerto 8080 está ocupado` | Une route tourne encore dans un autre terminal : arrête-la avec `Ctrl+C` |
| `La API no levantó: ¿corriste la 00?` | Allume l'environnement : `docker compose -f infra/compose.yaml up -d --wait` |
| `curl: (7) Failed to connect` | La route n'a pas encore démarré : attends la ligne `Started` |

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [L'API reçoit une clé d'idempotence](../decisiones/05-idempotency-key-hacia-la-api.md)

## Révision

1. Quelle est la station d'entrée de cette route, et quelle est celle de sortie ?
2. À quoi sert l'en-tête `Idempotency-Key`, et d'où vient sa valeur ?
3. Quelle différence y a-t-il entre un 201 et un 200 dans cette API ?
4. Pourquoi la route appelle-t-elle `api-pedidos` et non une adresse IP ?
5. Pourquoi la dernière vérification est-elle faite par l'API et non par ta route ?

**Suite :** dans la leçon 03, le traducteur n'est plus fourni. C'est toi qui l'écris, et des cas de test te disent s'il
est correct.
