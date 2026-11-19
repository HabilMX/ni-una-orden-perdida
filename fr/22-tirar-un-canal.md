# Leçon 22 — Faire tomber un canal

> **Ce que tu vas obtenir :** que, lorsque le SFTP du partenaire tombe, l'API et l'ERP continuent de recevoir leurs commandes
> immédiatement. Le routeur cesse d'appeler la destination en panne, note ce qu'il n'a pas envoyé, et ne la teste de nouveau, tout seul, qu'au bout d'un
> moment.

**Avant de commencer, il te faut :** la leçon 20 terminée et l'environnement de la 00 allumé.

## La nouveauté du jour, en une phrase

Dans la 10, tu as éteint l'ERP et vu que l'API et le partenaire recevaient la commande quand même : « un canal en panne n'arrête pas les deux
autres ». Depuis la 19, ce n'est plus vrai. Le routeur retente sur place, avec des pauses qui totalisent une demi-minute, et **pendant qu'il attend le
partenaire, toute la file attend avec lui**. Une commande qui devait seulement arriver à l'API met une demi-minute à cause
d'une destination qui n'a rien à voir. Aujourd'hui, tu poses un **coupe-circuit** par canal : quand une destination échoue plusieurs fois de suite, le
routeur cesse de l'appeler pendant un moment.

**L'image :** les disjoncteurs du tableau électrique de ta maison, un par zone. Si le sèche-linge fait un court-circuit, le disjoncteur de la
buanderie saute et la cuisine garde le courant. Tu ne demandes à personne de cesser d'utiliser le sèche-linge : le disjoncteur coupe tout seul. Et
une fois que tu as vérifié, tu le réenclenches et tu testes. S'il n'y avait qu'un seul disjoncteur pour toute la maison, un court-circuit dans la buanderie te
laisserait sans réfrigérateur.

---

## 1. Prépare ton dossier

```bash
cd lecciones/22-tirar-un-canal
cp -R inicio mio
```

Tout ton travail tient dans `mio/orden-router/reparto.camel.yaml`, dans la route `intentar`, à l'étape 3 : l'appel au
canal. Lis-le avant d'écrire. Le commentaire du haut décrit les trois états d'un coupe-circuit :

| État | Ce qu'il fait | Quand il change |
|---|---|---|
| **Fermé** | Appelle le canal, comme toujours | Il **s'ouvre** si, sur les 4 derniers appels (avec au moins 2), la moitié ou plus ont échoué |
| **Ouvert** | N'appelle **pas** le canal : il répond immédiatement que rien n'a été tenté | Au bout de 10 secondes, il devient **semi-ouvert** |
| **Semi-ouvert** | Laisse passer **un** appel d'essai | S'il se passe bien, il **se ferme** ; s'il échoue, il s'ouvre de nouveau |

Et il y a une route déjà faite, `sin-respuesta`, plus bas dans le même fichier. C'est celle qui s'exécute quand le coupe-circuit ne laisse pas
passer l'appel ou quand l'appel a échoué. Elle distingue les deux cas, et cela compte :

| Cas | `codigo` | Issue de la tentative | L'opération |
|---|---|---|---|
| Le coupe-circuit était ouvert et **il n'y a pas eu d'appel** | `-1` | `no_enviado` | Reste `pendiente` |
| Il y a eu un appel et il a échoué | `0` | `no_se_sabe` (la 20) | Se retente |

`no_enviado` n'est pas un doute : le routeur sait qu'il n'a pas appelé. C'est pour cela que cela ne se retente pas sur place et ne part pas en rapprochement.
L'opération reste `pendiente`, et dans la 23 tu verras comment elle est renvoyée.

## 2. Prédis

Réponds sur papier :

1. Tu fais tomber le SFTP du partenaire et quatre commandes de franchise arrivent. Sans coupe-circuit, combien de temps la quatrième met-elle à
   arriver à l'API ? (Indice : additionne les pauses de la 19.)
2. Pourquoi un coupe-circuit par canal et non un seul pour tout le routeur ?
3. Le SFTP revient. Qui doit « réenclencher le disjoncteur » ?

Regarde l'état actuel :

```bash
./verifica inicio
```

Le verifica éteint le SFTP, envoie quatre commandes et compte combien sont arrivées à l'API et à l'ERP en 25 secondes environ.
Il en arrive **une sur quatre**. La 4001 passe à l'API et à l'ERP, puis la file reste à attendre les nouvelles tentatives du partenaire ;
les trois autres n'avancent pas, alors que l'API et l'ERP se portent parfaitement.

## 3. Pose un disjoncteur par canal

À l'étape 3 d'`intentar`, il y a un `doTry` qui appelle `direct:llamar-canal`. C'est ce `doTry` que tu vas remplacer.
Il y a trois TODO :

| TODO | Ce que tu écris | Pour quoi faire |
|---|---|---|
| 1 | Un `choice` sur `${variable.canal}` à trois branches (`rest`, `bd`, `edi`), et dans chacune un `circuitBreaker` avec son `id` : `cortacircuito-rest`, `cortacircuito-bd`, `cortacircuito-edi` | Un disjoncteur par zone |
| 2 | Dans chaque `circuitBreaker`, sa `resilience4jConfiguration` | Quand il s'ouvre et combien de temps il attend avant de tester |
| 3 | Dans chacun, un `onFallback` qui appelle `direct:sin-respuesta` | Que soit noté s'il n'y a pas eu d'appel ou s'il y a eu un appel qui a échoué |

Voici **une** branche. Les deux autres sont identiques, avec leur canal et leur `id` :

```yaml
- choice:
    when:
      - expression:
          simple:
            expression: "${variable.canal} == 'rest'"
        steps:
          - circuitBreaker:
              id: cortacircuito-rest
              resilience4jConfiguration:
                slidingWindowSize: 4
                minimumNumberOfCalls: 2
                failureRateThreshold: 50
                waitDurationInOpenState: "10000"
                permittedNumberOfCallsInHalfOpenState: 1
                automaticTransitionFromOpenToHalfOpenEnabled: true
              steps:
                - to:
                    uri: direct:llamar-canal
              onFallback:
                steps:
                  - to:
                      uri: direct:sin-respuesta
      # … la rama de bd y la de edi
```

Chaque ligne de la configuration est une ligne du tableau des états :

| Paramètre | Valeur | Ce qu'il dit |
|---|---|---|
| `slidingWindowSize` | 4 | Il regarde les 4 derniers appels |
| `minimumNumberOfCalls` | 2 | Il ne décide pas avec moins de 2 |
| `failureRateThreshold` | 50 | Il s'ouvre à 50 % d'échecs ou plus |
| `waitDurationInOpenState` | `"10000"` | Ouvert 10 secondes (en millisecondes et entre guillemets) |
| `permittedNumberOfCallsInHalfOpenState` | 1 | En semi-ouvert, un seul appel d'essai |
| `automaticTransitionFromOpenToHalfOpenEnabled` | `true` | Il passe en semi-ouvert tout seul, sans attendre que quelqu'un appelle |

Tu écris la configuration trois fois, à l'identique. On pourrait l'écrire une fois et la réutiliser ; ici, elle figure en entier dans chaque canal
pour que chaque disjoncteur se lise seul.

Une note pour plus tard : si un jour tu compiles cette route en natif avec Camel Quarkus, le coupe-circuit est fourni par une autre
bibliothèque, mais le `circuitBreaker` du YAML est le même et tu ne changes rien.

## 4. Casses-en une exprès

Avec tes trois coupe-circuits terminés, change l'`id` des trois en un seul, `cortacircuito`, comme s'il n'y avait qu'un
seul disjoncteur pour tout le routeur. Lance `./verifica`.

Avant cela, prédis : l'API et l'ERP vont bien. Restent-ils à jour ?

Non. Le coupe-circuit compte ensemble les appels des trois canaux : ceux du partenaire échouent, ceux de l'API et de l'ERP non,
et avec la moitié en échec il s'ouvre **pour tous**. L'API et l'ERP cessent de recevoir des commandes à cause d'une destination qui n'est pas la
leur, et dans le journal le coupe-circuit de `rest` et celui de `bd` apparaissent ouverts. La coche de l'API et de l'ERP
et celle du journal sortent en rouge. Remets les trois `id`.

## 5. Vérifie

```bash
./verifica
```

`verifica` éteint le SFTP, envoie quatre commandes, contrôle l'API, l'ERP et l'état ; puis il rallume le SFTP,
attend que les 10 secondes soient passées et en envoie une cinquième. En sortant, il laisse le SFTP allumé. Elle est terminée quand tu vois les
cinq coches vertes :

```
✅ Arrancaron el router (con un cortacircuito por canal), los tres canales y el sondeo
✅ Con el SFTP caído, la API y el ERP siguieron al día: las 4 órdenes en los dos, en (…) s
✅ El canal del socio: las 4 quedaron pendientes, su último intento «no_enviado», y de la 4002 en adelante con un solo intento (no esperaron)
✅ Log: se abrió el cortacircuito del socio, y solo ese
✅ Volvió el SFTP: la 4005 pasó por el cortacircuito ya cerrado y su 850 está en el socio
```

Lis la troisième calmement. La 4001, elle, a bien essayé plusieurs fois : le coupe-circuit était encore fermé, et ce sont les échecs de
ces tentatives qui l'ont ouvert. À partir de la 4002, il y a une seule tentative, `no_enviado` : le routeur n'a même
pas appelé.

Et regarde le journal du routeur pendant que cela se passe :

```bash
DONDE=mio docker compose -f compose.yaml logs orden-router | grep -i cortacircuito
```

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff mio/orden-router/reparto.camel.yaml solucion/orden-router/reparto.camel.yaml` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `a los (…) s la API tiene 1 de 4` | Il n'y a pas de coupe-circuit, ou celui du partenaire ne s'ouvre jamais : vérifie que la branche `edi` existe et que `failureRateThreshold` vaut 50 |
| `El canal del socio quedó` avec 0 non envoyée | Il manque l'`onFallback` vers `direct:sin-respuesta` : sans lui, rien ne note le `no_enviado` |
| `esperaba el cortacircuito de edi abierto y los de rest y bd cerrados` | Deux branches partagent un `id` : chaque canal porte le sien |
| `Volvió el SFTP y la 4005 quedó «pendiente»` | Le coupe-circuit ne passe pas en semi-ouvert : il manque `automaticTransitionFromOpenToHalfOpenEnabled: true`, ou `waitDurationInOpenState` est bien supérieur à 10000 |
| `No arrancaron todas las rutas` | Vérifie l'indentation : `steps` et `onFallback` se placent dans `circuitBreaker`, à la même hauteur que `resilience4jConfiguration` |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

Si le verifica s'interrompt au milieu, le SFTP se rallume tout seul à la sortie. Si tu le vois malgré tout éteint :
`docker compose -f ../../infra/compose.yaml start socio-sftp`.

---

## Ce que cela enseigne au-delà du cours

**Une destination en panne ne doit pas faire tomber celles qui vont bien.** C'est l'idée derrière le coupe-circuit et les
« compartiments » d'un navire : si l'un est inondé, les autres restent au sec. Sans cela, la plus petite panne du système
décide de la vitesse de l'ensemble.

**Ne pas appeler ce que tu sais en panne rend service aux deux.** Tu t'épargnes l'attente, et la destination ne reçoit pas une vague
d'appels pendant qu'elle essaie de revenir.

**Le coupe-circuit se rétablit tout seul.** L'appel d'essai en semi-ouvert évite que quelqu'un doive se souvenir de
« réenclencher le disjoncteur ». Si la destination va toujours mal, il s'ouvre de nouveau et rien ne s'est passé.

**« Je ne l'ai pas envoyé » est un fait, et on le note comme tel.** Une tentative `no_enviado` dit autre chose qu'une `no_se_sabe`. La
première peut être renvoyée sans crainte, parce que la destination ne l'a jamais vue.

## Exercice : le disjoncteur en trois temps

Avec ta version terminée, lance `./verifica`, puis :

1. Avec le journal d'`orden-router`, construis une chronologie de l'exécution : quand le coupe-circuit de `edi` s'est ouvert,
   combien de commandes il a laissées sans envoi et quand l'appel d'essai est passé. Marques-y les trois états : fermé,
   ouvert et semi-ouvert.
2. Dans la base, consulte les tentatives du canal du partenaire :
   `SELECT p.orden, i.n, i.desenlace, i.respuesta->>'cuerpo' FROM intento i JOIN operacion p ON p.id = i.operacion WHERE p.canal = 'edi' AND p.momento = 'creada' ORDER BY p.orden, i.n;`
   Que dit la réponse d'une tentative `no_enviado` ? Et celle d'une `no_se_sabe` ?
3. Les quatre commandes du partenaire sont restées `pendiente`. Personne ne va les envoyer toutes seules. Que manque-t-il au routeur pour
   les récupérer ? Écris ta réponse : c'est la leçon 23.

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Un coupe-circuit par canal, dans le routeur](../decisiones/26-un-cortacircuito-por-canal.md)

## Révision

1. Quels sont les trois états d'un coupe-circuit, et que fait-il dans chacun ?
2. Que signifie chaque nombre de la configuration : 4, 2, 50, 10000 et 1 ?
3. Pourquoi un coupe-circuit par canal et non un seul pour tout le routeur ?
4. En quoi une tentative `no_enviado` se distingue-t-elle d'une `no_se_sabe` ? Pourquoi la première ne part-elle pas en rapprochement ?
5. **La question de clôture :** dans la 10, tu as conclu qu'« un canal en panne n'arrête pas les deux autres ». Pourquoi cela a-t-il cessé
   d'être vrai dans la 19, et qu'est-ce qui le rend vrai de nouveau ? Sers-toi de ce que tu as vu dans le verifica d'`inicio/` et dans le tien.

**Suite :** dans la leçon 23, quelqu'un doit renvoyer ce qui est resté en attente ou en rapprochement. Comment
retraites-tu une commande sans la dupliquer et en laissant écrit qui l'a fait et pourquoi ?
