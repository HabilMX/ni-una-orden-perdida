# Leçon 20 — Accepté, rejeté ou on ne sait pas

> **Ce que tu vas obtenir :** que le routeur classe chaque réponse de la destination selon ce qu'elle a vraiment dit : « oui »,
> « non » ou rien de clair. Qu'un « non » ne se répète pas, et que ce qui est resté sans réponse, une fois les nouvelles tentatives épuisées, soit
> marqué pour qu'une personne l'examine, jamais comme un échec.

**Avant de commencer, il te faut :** la leçon 19 terminée et l'environnement de la 00 allumé. Cette leçon utilise l'API
1.5.0, qui a trois modes de test : `rechaza`, `lento` et `satura`. Si ton API date d'avant, recrée-la une fois :

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate api-pedidos
```

## La nouveauté du jour, en une phrase

Dans la 19, le routeur a appris à retenter quand la destination ne répond pas. Mais « elle n'a pas répondu » n'est qu'une des
réponses possibles. Il y a des destinations qui répondent « non » en toute clarté, et il y a des appels qui se coupent au milieu sans
que nous sachions si la destination a eu le temps d'écrire. Aujourd'hui, le routeur apprend à les distinguer **d'après les preuves**, pas d'après ce
qu'il attendait.

**L'image :** tu as envoyé une lettre recommandée. Si l'accusé de réception te revient signé, elle est arrivée. Si on te la retourne avec un tampon
« adresse inexistante », elle n'est pas arrivée et il ne sert à rien de la renvoyer à la même adresse. Mais si trois semaines passent
et que rien ne te revient, tu ne sais pas : elle a pu se perdre, ou elle a pu arriver et c'est l'accusé qui s'est perdu au retour. Ce qu'il faut
dire, ce n'est pas « elle s'est perdue » ; c'est « je ne sais pas », et aller demander au bureau de poste.

---

## 1. Prépare ton dossier

```bash
cd lecciones/20-aceptado-rechazado-o-no-se-sabe
cp -R inicio mio
```

Aujourd'hui, tu travailles dans deux fichiers du routeur que tu connais déjà :

| Fichier | Route | Ce que tu changes |
|---|---|---|
| `reparto.camel.yaml` | `intentar`, celle qui appelle le canal et note comment cela s'est passé | Le `choice` qui décide de l'issue (TODO 1 et 2) |
| `reintentos.camel.yaml` | Le `onException` de la 19 | Ce qui se passe quand les nouvelles tentatives sont épuisées (TODO 3) |

Et une pièce qui est déjà faite : **le canal int-rest impose à l'API un délai d'expiration de 3 secondes**. Si l'API ne
répond pas dans ce délai, int-rest n'invente ni un « oui » ni un « non » : il répond **504** au routeur, ce qui en HTTP veut
dire « celui qui était derrière n'a pas répondu à temps ».

Et une autre, déjà faite aussi : **si la destination dit combien de temps attendre, on attend ce temps-là.** Un 429 porte en général l'en-tête
`Retry-After: 3`, « reviens dans 3 secondes ». Le routeur le reprend comme pause de sa prochaine tentative, avec un plafond de 10
secondes pour qu'une destination ne le retienne pas une heure. Et le client HTTP de Camel, qui de lui-même répéterait un 429 sans
laisser de trace, est désactivé (`automaticRetriesDisabled`) : le seul qui retente est le routeur, et chaque tentative reste dans
la base.

## 2. Prédis

Réponds sur papier :

1. L'API a mis 10 secondes et le canal a coupé au bout de 3. A-t-elle écrit la commande ou non ? Comment le saurais-tu ?
2. L'API a répondu 422 « rejeté pour une raison métier ». Si le routeur la retente cinq fois, que répondra la cinquième ?
   Et si elle a répondu 429 « tu m'en as envoyé trop, reviens dans 3 secondes » ?
3. Les cinq nouvelles tentatives d'un appel qui n'a jamais reçu de réponse sont épuisées. La commande a-t-elle « échoué » ? Que devrait en faire
   quelqu'un ?

Regarde l'état actuel :

```bash
./verifica inicio
```

Le premier test passe : avec l'API en mode `rechaza`, la 4001 reste `rechazada` avec une seule tentative. Mais le deuxième,
avec l'API en mode `lento`, se passe mal : la 4002 reste **`rechazada`**, et la dernière coche montre que l'API **l'a
bel et bien**, en CREADO. Le routeur a noté « la destination a dit non » alors que la destination n'a jamais rien dit, et qu'en plus elle a écrit.
Si quelqu'un se fie à cette ligne et la renvoie à la main, ou la tient pour perdue, il se trompe dans les deux cas.

Le troisième se passe mal aussi : avec l'API en mode `satura`, la 4003 reçoit un 429 et reste `rechazada` à la première
tentative. L'API ne l'a pas, mais elle l'aurait acceptée trois secondes plus tard. Cette commande reste non envoyée.

## 3. Décide d'après les preuves

Ouvre `mio/orden-router/reparto.camel.yaml` et cherche la route `intentar`. Presque à la fin, après l'appel au canal,
il y a un `choice` qui dit aujourd'hui : 2xx est `aceptada` ; tout le reste, `rechazada`. Au-dessus se trouvent les TODO 1 et 2.

Voici le tableau qu'il doit respecter :

| Ce qui a été vu | `${variable.codigo}` | Issue | Retente-t-on ? |
|---|---|---|---|
| La destination a dit oui | 200 à 299 | `aceptada` | Inutile |
| Pas maintenant, réessaie plus tard | 408, 425, 429 | `no_se_sabe` | Oui ; s'il porte `Retry-After`, cette attente-là (plafond de 10 s) |
| La destination a dit non | Le reste de 400 à 499 | `rechazada` | Non : elle dira la même chose |
| Rien de clair : un 5xx, un 504 ou pas de réponse | 500 à 599, ou `0` | `no_se_sabe` | Oui, avec la politique de la 19 |

| TODO | Ce que tu écris | Pour quoi faire |
|---|---|---|
| 1 | Deux `when` de plus, dans cet ordre : un avec `${variable.codigo} in '408,425,429'` qui met `desenlace` à `no_se_sabe`, puis un avec `${variable.codigo} range '400..499'` qui le met à `rechazada` | Qu'un « pas maintenant » se retente et qu'un « non » soit noté comme « non » |
| 2 | Que l'`otherwise` mette `no_se_sabe` au lieu de `rechazada` | Que ce qui n'est ni un oui ni un non soit noté comme « on ne sait pas » |

Pourquoi dans cet ordre ? Le `choice` retient le **premier** `when` qui est vérifié, et un 429 se trouve aussi entre 400 et
499. Si le `range` venait en premier, le 429 n'arriverait jamais à son `when`.

Regarde comment cela s'emboîte avec la 19. À la fin d'`intentar`, il y a un `filter` : si l'issue est restée `no_se_sabe`, il lève
une `IOException`, et cette exception est celle qu'attrape ton `onException` pour retenter. **L'issue que tu écris aujourd'hui
est celle qui décide si l'on retente.** Un `rechazada` ne lève rien : il ne se répète donc pas.

Ouvre maintenant `mio/orden-router/reintentos.camel.yaml`. À l'intérieur du `onException`, avant le `log`, se trouve le TODO 3 :

| TODO | Ce que tu écris | Pour quoi faire |
|---|---|---|
| 3 | Un `to` avec `sql:UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}`, avec `dataSource: "#estado"` | Que ce qui est épuisé soit marqué pour que quelqu'un l'examine |

La dernière tentative reste en `no_se_sabe`, parce que c'est ce qui s'est passé lors de cette tentative. Ce qui change, c'est
l'**opération** : elle n'attend plus une nouvelle tentative, elle attend une personne.

## 4. Casses-en une exprès

Une fois tout terminé, change ton TODO 1 pour qu'un 4xx soit aussi `no_se_sabe`, comme si tout ce qui n'est pas un 2xx valait
la peine d'être répété. Lance `./verifica`.

Avant cela, prédis : l'API est en mode `rechaza` et répond 422 à chaque fois. Combien de tentatives le routeur va-t-il faire ?

Six : la première et cinq nouvelles tentatives, et les six répondent 422. La coche du rejet métier sort en rouge. Répéter
un « non » ne le transforme pas en « oui » : cela ne fait que frapper six fois l'API, arrêter la file une trentaine de secondes (la 19) et, à la fin,
laisser l'opération en `conciliacion_pendiente`, en envoyant une personne examiner quelque chose à quoi la destination avait déjà répondu
clairement. Remets le `rechazada`.

## 5. Vérifie

```bash
./verifica
```

`verifica` met l'API en mode `rechaza` et envoie la 4001 ; puis il la met en mode `lento` (elle répond au bout de 10 secondes)
et envoie la 4002 ; à la fin, en mode `satura` (deux 429 avec `Retry-After: 3`, puis elle répond normalement), il envoie la 4003. En sortant,
il la remet en mode normal. Elle est terminée quand tu vois les neuf coches vertes :

```
✅ Arrancaron el router, los tres canales y el sondeo
✅ Rechazo de negocio: la 4001 quedó rechazada con un solo intento (422): un «no» no se repite
✅ Mientras la llamada va en camino, su intento ya está en la base (con huella y sin desenlace): se escribe antes de enviar
✅ No se sabe: la 4002 se intentó 6 veces, las 6 sin respuesta a tiempo (504, no_se_sabe), y quedó en conciliacion_pendiente
✅ Y la API sí tiene la 4002 (CREADO): por eso no era un fallo ni un rechazo, era «no se sabe»
✅ Ahora no: la 4003 recibió dos 429 (no_se_sabe), se reintentó y quedó aceptada al tercer intento (201): un 429 no es un «no»
✅ Y respetó el Retry-After: entre una llamada y la siguiente pasaron 3.05 3.04 s, ninguna antes de los 3 s que pidió la API
✅ La API tiene la 4003 (CREADO): los 429 no la registraron, el reintento sí
✅ Los otros canales siguieron: ERP y socio aceptaron las dos órdenes
```

Regarde la troisième. Le verifica jette un œil dans la base **pendant que l'appel est encore en route** et y trouve la tentative déjà
écrite, avec son empreinte et sans issue. Cette trace, tu ne l'as pas écrite aujourd'hui : elle existe depuis la 15. Mais aujourd'hui, on voit à quoi
elle sert : si le routeur tombait à cet instant, la base dirait « ceci a été tenté et je ne sais pas comment cela s'est passé ». Si la tentative
était écrite **après** l'appel, il ne resterait rien.

Et regarde celle des attentes : les secondes viennent du journal de l'API, l'heure à laquelle chaque appel lui est parvenu. Les
tiennes seront un peu différentes ; ce qui est demandé, c'est qu'aucune ne descende sous 3.

**Ça ne tourne pas ?** `./verifica solucion` teste la nôtre, et
`diff -r mio/orden-router solucion/orden-router` te dit en quoi elles diffèrent.

| Ce que tu vois | Ce que tu fais |
|---|---|
| `esperaba rechazada, 1 intento y 422` et il y a 6 tentatives | Un 4xx est pris pour `no_se_sabe` : revois le `range '400..499'` |
| `esperaba conciliacion_pendiente con 6 intentos` et il dit `rechazada` | L'`otherwise` met encore `rechazada` : c'est `no_se_sabe` (TODO 2) |
| `Ahora no: esperaba aceptada con 3 intentos…` et il y a `rechazada` avec une tentative | Le 429 a été pris pour un « non » : le `when` de `408,425,429` va **avant** le `range '400..499'` (TODO 1) |
| `Retry-After: esperaba dos esperas de al menos 3 s…` | Quelque chose retente sans attendre : vérifie que tu n'as touché ni au `CamelRedeliveryDelay` d'`intentar` ni au `onException` |
| `esperaba conciliacion_pendiente` et il dit `no_se_sabe` | Il manque l'`UPDATE` du TODO 3, ou il ne porte pas `dataSource: "#estado"` |
| `No pude poner la API en modo rechaza` ou `en modo satura` | Ton API date d'avant la 1.5.0 : recrée-la comme l'indique « Avant de commencer » |
| `El puerto 8080 está ocupado` | Les services d'une autre exécution sont restés en marche : `DONDE=mio docker compose -f compose.yaml down` |

---

## Ce que cela enseigne au-delà du cours

**« On ne sait pas » est un résultat, pas une défaillance du programme.** Dans tout système qui parle par le réseau, il y a des appels qui
se coupent au milieu. La destination a pu écrire ou non. Appeler cela un « échec » mène à deux erreurs : le renvoyer à la main
(et dupliquer, s'il n'y a pas d'idempotence) ou le tenir pour perdu (alors qu'il est arrivé). Tu l'as vu dans le verifica : l'API a enregistré
la 4002 dix secondes après que le canal a coupé.

**Un 4xx et un 5xx ne se traitent pas de la même façon.** Le 4xx, c'est la destination qui dit « ce que tu m'as envoyé est faux, ou je ne l'accepte pas ».
Le 5xx, c'est la destination qui dit « il m'est arrivé quelque chose, à moi ». Le premier ne change pas si tu le répètes ; le second, parfois oui. Mais
tout 4xx n'est pas un « non » : un 408 (tu as tardé à l'envoyer), un 425 (c'est trop tôt) ou un 429 (tu vas trop vite) disent
« pas maintenant, réessaie plus tard ». C'est pour cela qu'ici on les retente, et s'ils portent `Retry-After`, on attend ce qu'ils demandent. Avec une
destination réelle, chaque code se classe selon ce que dit son contrat ; ce tableau est celui de l'API du cours.

**La tentative s'écrit avant l'envoi.** C'est la seule manière de pouvoir retrouver, après une panne, ce qui
est resté en l'air. Les banques font de même avec un virement : elles le notent d'abord comme « en cours », puis elles
l'envoient.

**Rapprocher, c'est aller voir la destination.** Quand le routeur ne peut pas savoir, il le marque pour quelqu'un qui le peut : celui qui
a accès à l'API, à l'ERP ou au partenaire et vérifie si la commande y est.

## Exercice : sept réponses

1. Classe ces sept réponses en `aceptada`, `rechazada` ou `no_se_sabe`, et écris pour chacune quelle preuve
   en décide :

   | # | Ce que le canal a renvoyé |
   |---|---|
   | a | 201, l'API a créé la commande |
   | b | 200, l'API avait déjà cette commande avec cette même clé (la 02) |
   | c | 422 `llave-reusada` : la même clé avec un autre contenu |
   | d | 503, l'API est en maintenance |
   | e | 504, le canal a coupé au bout de 3 secondes |
   | f | Aucune : le conteneur d'int-rest a redémarré au milieu (code `0`) |
   | g | 429 avec `Retry-After: 3` : l'API en a reçu trop en peu de temps |

2. Dans la base, après avoir lancé `./verifica`, regarde les tentatives de la 4002 :
   `SELECT n, desenlace, respuesta->>'codigo' AS codigo FROM intento i JOIN operacion p ON p.id = i.operacion WHERE p.orden = '4002' AND p.canal = 'rest' ORDER BY n;`
   Puis interroge l'API : `curl -s localhost:8081/pedidos/4002`. Écris en une phrase ce que tu dirais à la personne
   qui doit rapprocher cette opération.
3. Si la tentative était écrite **après** l'appel au canal et que le routeur tombait avec l'appel en route, que
   resterait-il dans la base ? Qu'arriverait-il à cette commande ?

---

## Pourquoi on a fait ainsi

Ce que nous avons décidé dans cette leçon, avec ce que nous avons écarté et ce qui nous ferait changer d'avis :

- [Trois issues, décidées d'après les preuves : accepté, rejeté ou on ne sait pas](../decisiones/24-tres-desenlaces.md)

## Révision

1. Quelles sont les trois issues possibles d'une tentative, et quelle preuve décide de chacune ?
2. Pourquoi un 422 ne se retente-t-il pas, alors qu'un 429 ou un 504 oui ? Pourquoi le `when` du 429 va-t-il avant celui de la plage ?
3. Qu'est-ce que `conciliacion_pendiente`, et pourquoi cela ne s'appelle-t-il pas « en échec » ?
4. Pourquoi le canal répond-il 504 quand son délai d'expiration est dépassé, au lieu d'un 500 ou d'un 422 ?
5. **La question de clôture :** la tentative s'écrit avant l'envoi. Sers-toi de la 4002 pour expliquer ce qui se perdrait si elle
   s'écrivait après.

**Suite :** dans la leçon 21, la destination est le partenaire, et le partenaire ne répond pas dans le même appel. Le 850 reste sur son
SFTP et l'accusé de réception arrive plus tard, dans un autre fichier. Déposer n'est pas confirmer.
