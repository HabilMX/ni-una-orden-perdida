# Lição 20 — Aceito, rejeitado ou não se sabe

> **O que será alcançado:** que o router classifique cada resposta do destino pelo que ele de fato disse: «sim»,
> «não» ou nada claro. Que um «não» não se repita, e que o que ficou sem resposta, ao esgotar as novas tentativas, fique
> marcado para que uma pessoa o confira, nunca como uma falha.

**Antes de começar é preciso ter:** a lição 19 concluída e o ambiente da 00 ligado. Esta lição usa a API
1.5.0, que tem três modos de teste: `rechaza`, `lento` e `satura`. Se a API for de antes, recrie-a uma vez:

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate api-pedidos
```

## A novidade de hoje, em uma frase

Na lição 19 o router aprendeu a tentar de novo quando o destino não responde. Mas «não respondeu» é só uma das
respostas possíveis. Há destinos que respondem «não» com toda a clareza, e há chamadas que são cortadas no meio sem
que se saiba se o destino chegou a escrever. Hoje o router aprende a distingui-las **pela evidência**, não pelo
que esperava.

**A imagem:** mandou-se uma carta registrada. Se volta o aviso de recebimento assinado, chegou. Se a carta volta com um carimbo
de «endereço inexistente», não chegou e não adianta mandá-la outra vez para o mesmo endereço. Mas se passam três semanas
e não chega nada, não se sabe: ela pode ter se perdido ou pode ter chegado e o aviso se perdeu na volta. O correto
não é dizer «perdeu-se»; é dizer «não sei» e perguntar na agência.

---

## 1. Prepare a pasta

```bash
cd lecciones/20-aceptado-rechazado-o-no-se-sabe
cp -R inicio mio
```

Hoje o trabalho é em dois arquivos do router já conhecidos:

| Arquivo | Rota | O que muda |
|---|---|---|
| `reparto.camel.yaml` | `intentar`, a que chama o canal e anota como foi | O `choice` que decide o desfecho (TODO 1 e 2) |
| `reintentos.camel.yaml` | O `onException` da lição 19 | O que acontece quando as novas tentativas se esgotam (TODO 3) |

E uma peça que já está feita: **o canal int-rest impõe à API um tempo limite (timeout) de 3 segundos**. Se a API não
responde nesse tempo, o int-rest não inventa um «sim» nem um «não»: responde **504** ao router, que em HTTP quer
dizer «quem estava atrás não respondeu a tempo».

E outra, também feita: **se o destino diz quanto esperar, espera-se isso.** Um 429 costuma trazer o cabeçalho
`Retry-After: 3`, «volte em 3 segundos». O router o usa como a pausa da nova tentativa seguinte, com um teto de 10
segundos para que um destino não o detenha por uma hora. E o cliente HTTP do Camel, que por conta própria repetiria um 429 sem
deixar trilha, está desligado (`automaticRetriesDisabled`): o único que tenta de novo é o router, e cada tentativa fica na
base.

## 2. Faça a previsão

Responda em um papel:

1. A API demorou 10 segundos e o canal cortou aos 3. Ela escreveu ou não escreveu o pedido? Como se saberia?
2. A API respondeu 422 «rejeitado pelo negócio». Se o router tenta de novo cinco vezes, o que a quinta vai responder?
   E se ela respondeu 429 «chegaram pedidos demais, volte em 3 segundos»?
3. Esgotaram-se as cinco novas tentativas de uma chamada que nunca respondeu. O pedido «falhou»? O que alguém deveria fazer
   com ele?

Veja como está hoje:

```bash
./verifica inicio
```

O primeiro teste passa: com a API em modo `rechaza`, o 4001 fica `rechazada` com uma única tentativa. Mas o segundo,
com a API em modo `lento`, sai errado: o 4002 fica **`rechazada`**, e a última marca mostra que a API **o
tem**, em CREADO. O router anotou «o destino disse que não» quando o destino nunca disse nada, e além disso escreveu.
Se alguém confia nessa linha e manda o pedido outra vez à mão, ou o dá por perdido, erra nos dois casos.

O terceiro também sai errado: com a API em modo `satura`, o 4003 recebe um 429 e fica `rechazada` na primeira
tentativa. A API não o tem, mas o teria aceitado três segundos depois. Esse pedido fica sem ser enviado.

## 3. Decida pela evidência

Abra `mio/orden-router/reparto.camel.yaml` e procure a rota `intentar`. Quase no fim, depois de chamar o canal,
há um `choice` que hoje diz: 2xx é `aceptada`; todo o resto, `rechazada`. Acima dele estão os TODO 1 e 2.

Esta é a tabela que ele tem de cumprir:

| O que se viu | `${variable.codigo}` | Desfecho | Tenta-se de novo? |
|---|---|---|---|
| O destino disse que sim | 200 a 299 | `aceptada` | Não é preciso |
| Agora não, tente depois | 408, 425, 429 | `no_se_sabe` | Sim; se trouxer `Retry-After`, essa espera (teto de 10 s) |
| O destino disse que não | O resto de 400 a 499 | `rechazada` | Não: vai dizer a mesma coisa |
| Nada claro: um 5xx, um 504 ou sem resposta | 500 a 599, ou `0` | `no_se_sabe` | Sim, com a política da lição 19 |

| TODO | O que escrever | Para quê |
|---|---|---|
| 1 | Mais dois `when`, nesta ordem: um com `${variable.codigo} in '408,425,429'` que põe `desenlace` em `no_se_sabe`, e depois um com `${variable.codigo} range '400..499'` que o põe em `rechazada` | Que um «agora não» leve a uma nova tentativa e um «não» seja anotado como «não» |
| 2 | Que o `otherwise` ponha `no_se_sabe` em vez de `rechazada` | Que o que não é um sim nem um não seja anotado como «não se sabe» |

Por que nessa ordem? O `choice` fica com o **primeiro** `when` que se cumpre, e um 429 também está entre 400 e
499. Se o `range` viesse primeiro, o 429 nunca chegaria ao `when` dele.

Repare em como isso se encaixa com a lição 19. No fim de `intentar` há um `filter`: se o desfecho ficou `no_se_sabe`, ele lança
uma `IOException`, e essa exceção é a que o `onException` captura para tentar de novo. **O desfecho escrito hoje
é o que decide se há nova tentativa.** Um `rechazada` não lança nada, e por isso não se repete.

Agora abra `mio/orden-router/reintentos.camel.yaml`. Dentro do `onException`, antes do `log`, está o TODO 3:

| TODO | O que escrever | Para quê |
|---|---|---|
| 3 | Um `to` com `sql:UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}`, com `dataSource: "#estado"` | Que o que se esgotou fique marcado para alguém conferir |

A última tentativa fica em `no_se_sabe`, porque foi isso que aconteceu nessa tentativa. O que muda é a
**operação**: ela já não está esperando uma nova tentativa, está esperando uma pessoa.

## 4. Quebre um de propósito

Com tudo concluído, mude o TODO 1 para que um 4xx também seja `no_se_sabe`, como se tudo o que não é 2xx valesse
a pena repetir. Rode `./verifica`.

Antes, faça a previsão: a API está em modo `rechaza` e responde 422 todas as vezes. Quantas tentativas o router vai fazer?

Seis: a primeira e cinco novas tentativas, e as seis respondem 422. Sai em vermelho a marca da rejeição de negócio. Repetir
um «não» não o transforma em «sim»: só bate seis vezes na API, detém a fila por uns trinta segundos (a lição 19) e no fim
deixa a operação em `conciliacion_pendiente`, mandando uma pessoa conferir algo que o destino já tinha respondido
com clareza. Devolva o `rechazada`.

## 5. Confira

```bash
./verifica
```

O `verifica` põe a API em modo `rechaza` e manda o 4001; depois a põe em modo `lento` (responde aos 10 segundos)
e manda o 4002; no fim, em modo `satura` (dois 429 com `Retry-After: 3` e depois atende), manda o 4003. Ao sair,
devolve-a ao modo normal. Está concluída quando aparecem as nove marcas verdes:

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

Veja a terceira. O `verifica` espia a base **enquanto a chamada ainda está a caminho** e encontra a tentativa já
escrita, com a impressão digital e sem desfecho. Essa marca não foi escrita hoje: existe desde a lição 15. Mas hoje se vê para que
serve: se o router caísse naquele instante, a base diria «isto foi tentado e não sei como foi». Se a tentativa fosse
escrita **depois** da chamada, não ficaria nada.

E veja a das esperas: os segundos saem do log da API, a hora em que cada chamada chegou a ela. Os
da própria execução vão ser um pouco diferentes; o que se pede é que nenhum fique abaixo de 3.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff -r mio/orden-router solucion/orden-router` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `esperaba rechazada, 1 intento y 422` e há 6 tentativas | Um 4xx está sendo tomado como `no_se_sabe`: confira o `range '400..499'` |
| `esperaba conciliacion_pendiente con 6 intentos` e diz `rechazada` | O `otherwise` continua pondo `rechazada`: é `no_se_sabe` (TODO 2) |
| `Ahora no: esperaba aceptada con 3 intentos…` e há `rechazada` com uma tentativa | O 429 foi tomado como «não»: o `when` de `408,425,429` vai **antes** do `range '400..499'` (TODO 1) |
| `Retry-After: esperaba dos esperas de al menos 3 s…` | Algo tenta de novo sem esperar: confira que não se mexeu no `CamelRedeliveryDelay` de `intentar` nem no `onException` |
| `esperaba conciliacion_pendiente` e diz `no_se_sabe` | Falta o `UPDATE` do TODO 3, ou ele não leva `dataSource: "#estado"` |
| `No pude poner la API en modo rechaza` ou `en modo satura` | A API é de antes da 1.5.0: recrie-a como diz «Antes de começar» |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**«Não se sabe» é um resultado, não uma falha do programa.** Em qualquer sistema que fala pela rede há chamadas que
são cortadas no meio. O destino pode ter escrito ou não. Chamar isso de «falha» leva a dois erros: reenviar à mão
(e duplicar, se não há idempotência) ou dar por perdido (quando chegou). No `verifica` viu-se isso: a API registrou
o 4002 dez segundos depois de o canal cortar.

**Um 4xx e um 5xx não se tratam do mesmo jeito.** O 4xx é o destino dizendo «o que foi mandado está errado, ou não aceito».
O 5xx é o destino dizendo «algo aconteceu comigo». O primeiro não muda se for repetido; o segundo, às vezes sim. Mas
nem todo 4xx é um «não»: um 408 (a requisição demorou a ser mandada), um 425 (é cedo demais) ou um 429 (está rápido demais) dizem
«agora não, tente depois». Por isso aqui se tenta de novo, e se trazem `Retry-After`, espera-se o que pedem. Com um
destino real, cada código se classifica conforme o que diz o contrato dele; esta tabela é a da API do curso.

**A tentativa é escrita antes de enviar.** É a única maneira de, depois de uma queda, conseguir encontrar o que
ficou no ar. Os bancos fazem o mesmo com uma transferência: primeiro a anotam como «em processamento», depois a
mandam.

**Conciliar é ir olhar no destino.** Quando o router não tem como saber, marca para alguém que tem: quem
tem acesso à API, ao ERP ou ao parceiro e confere se o pedido ficou lá.

## Exercício: sete respostas

1. Classifique estas sete respostas em `aceptada`, `rechazada` ou `no_se_sabe`, e escreva para cada uma que evidência
   decide:

   | # | O que o canal devolveu |
   |---|---|
   | a | 201, a API criou o pedido |
   | b | 200, a API já tinha esse pedido com essa mesma chave (a lição 02) |
   | c | 422 `llave-reusada`: a mesma chave com outro conteúdo |
   | d | 503, a API está em manutenção |
   | e | 504, o canal cortou aos 3 segundos |
   | f | Nenhuma: o contêiner do int-rest reiniciou no meio (código `0`) |
   | g | 429 com `Retry-After: 3`: a API recebeu demais em pouco tempo |

2. Na base, depois de rodar `./verifica`, veja as tentativas do 4002:
   `SELECT n, desenlace, respuesta->>'codigo' AS codigo FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.orden = '4002' AND p.canal = 'rest' ORDER BY n;`
   E depois pergunte à API: `curl -s localhost:8081/pedidos/4002`. Escreva em uma frase o que se diria à pessoa
   que tem de conciliar essa operação.
3. Se a tentativa fosse escrita **depois** de chamar o canal e o router caísse com a chamada a caminho, o que
   ficaria na base? O que aconteceria com esse pedido?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Três desfechos, decididos pela evidência: aceito, rejeitado ou não se sabe](../decisiones/24-tres-desenlaces.md)

## Revisão

1. Que três desfechos uma tentativa pode ter, e que evidência decide cada um?
2. Por que um 422 não leva a nova tentativa e um 429 ou um 504 sim? Por que o `when` do 429 vai antes do `when` do intervalo?
3. O que é `conciliacion_pendiente`, e por que não se chama «falha»?
4. Por que o canal responde 504 quando o tempo limite dele vence, em vez de um 500 ou um 422?
5. **A pergunta de encerramento:** a tentativa é escrita antes de enviar. Use o 4002 para explicar o que se perderia se
   fosse escrita depois.

**A seguir:** na lição 21 o destino é o parceiro, e o parceiro não responde na mesma chamada. O 850 fica no
SFTP dele e a confirmação de recebimento chega depois, em outro arquivo. Depositar não é confirmar.
