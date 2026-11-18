# Lição 23 — Reprocessar o que falhou

> **O que será alcançado:** uma porta de entrada no router, `POST /rerun`, para que uma pessoa mande de novo **só** o
> passo que ficou travado, sem duplicar nada nos destinos e deixando escrito quem pediu, por quê e com que
> versão saiu.

**Antes de começar é preciso ter:** as lições 22 e 18 concluídas, e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Na lição 20 o que não se soube ficou em `conciliacion_pendiente`, e na 22 o que o disjuntor não mandou ficou em
`pendiente`. As duas coisas esperam o mesmo: que alguém conserte a causa e mande de novo. Até hoje, esse
alguém não tinha como. O **rerun** é essa ferramenta: mais uma tentativa da mesma operação, pedida por uma pessoa
e anotada como tal.

**A imagem:** o balcão de reclamações de uma transportadora. Leva-se o código de rastreio e diz-se «este pacote ficou no
armazém por causa da tempestade». Não reenviam tudo o que foi mandado naquele dia: procuram **esse** pacote, conferem que ele de fato
está travado e o despacham de novo. No sistema fica anotado que quem reenviou foi o balcão, a pedido do cliente, por causa
da tempestade. Para o código de rastreio de um pacote que já foi entregue, a resposta é não.

---

## 1. Prepare a pasta

```bash
cd lecciones/23-reprocesar-lo-que-fallo
cp -R inicio mio
```

O router tem um arquivo novo, `orden-router/rerun.camel.yaml`, com uma única rota: `rerun`, que escuta em
`POST /rerun`. Recebe isto:

```json
{"orden": "4001", "momento": "creada", "canal": "edi",
 "pedido_por": "ana.soporte", "motivo": "el SFTP del socio estuvo caído"}
```

Abra-o e leia o comentário do alto. A versão de `inicio/` **funciona, mas não basta**. Marca a operação
como `pendiente` e a joga outra vez na distribuição. Isso tem três problemas:

| Problema | Por que importa |
|---|---|
| Sai como uma tentativa `auto`, sem nome nem motivo | Amanhã ninguém sabe que foi uma pessoa que pediu, nem por quê |
| Reabre o que já tinha dado certo, e o manda outra vez | Um rerun de algo aceito chama o destino de novo |
| Responde 202 «devuelta al reparto» | Quem pediu não sabe como foi |

E há algo que **não** é preciso construir: a tabela `intento` já tem as colunas `tipo`, `pedido_por` e
`motivo`, e o `INSERT` da rota `intentar` (em `reparto.camel.yaml`) já as preenche com três variáveis. Se elas não
existem, `tipo` fica em `auto`.

## 2. Faça a previsão

Responda em um papel:

1. O SFTP do parceiro volta. Se for reprocessado **o pedido completo** (os três canais) do 4001, o que acontece com a API
   e com o ERP, que já o tinham?
2. A API demorou e o canal cortou: o 4005 ficou sem saber como foi. Faz-se o rerun. Cria-se outro pedido na API?
   (Dica: as lições 02 e 18.)
3. Alguém pede um rerun de uma operação que já está `aceptada`. O que o router deveria responder?

Veja como está hoje:

```bash
./verifica inicio
```

Os reprocessamentos saem, e por isso várias marcas passam. Mas saem como tentativas `auto`, o rerun do que já estava
aceito chama o destino de novo em vez de se recusar, e um sem motivo é aceito do mesmo jeito.

## 3. Uma tentativa manual, com nome e motivo

Abra `mio/orden-router/rerun.camel.yaml`. Depois das cinco variáveis que são tiradas do corpo há quatro TODO, e
os quatro substituem o que está embaixo: o `UPDATE` para `pendiente`, o envio para `seda:repartir` e a resposta 202.

### TODO 1: sem nome ou sem motivo, não

Primeiro dois cabeçalhos, para que a resposta saia limpa: `removeHeaders` com `pattern: "*"` e um `setHeader` de
`Content-Type` em `application/json`. Depois um `filter` que deixa passar **só o que está errado**: se falta `orden`,
`momento`, `canal`, `pedido_por` ou `motivo`, responde 400 e para.

```yaml
- filter:
    expression:
      simple:
        expression: >-
          ${variable.orden} == null || ${variable.orden} == '' || …
          ${variable.pedido_por} == null || ${variable.pedido_por} == '' ||
          ${variable.motivo} == null || ${variable.motivo} == ''
    steps:
      - setHeader:
          name: CamelHttpResponseCode
          expression:
            constant:
              expression: "400"
              resultType: java.lang.Integer
      - setBody:
          expression:
            constant:
              expression: '{"resultado":"no reprocesado","motivo":"faltan datos: orden, momento, canal, pedido_por y motivo"}'
      - stop: {}
```

O `stop` termina a rota ali: o que vem embaixo não roda.

### TODO 2: só o que falhou

Leia a operação **da base**, não do corpo. Copie a consulta da rota `repartir` de `reparto.camel.yaml` e
mude duas coisas: que filtre também por `p.canal = :#${variable.canal}` em vez de por `p.estado = 'pendiente'`,
e que devolva também `p.estado`. Vai com `outputType: SelectOne`, de modo que o corpo fica como **uma** linha (ou
`null`).

Depois, um `choice` com dois ramos que respondem e param, como o `filter` do TODO 1:

| Se | Responde | Por quê |
|---|---|---|
| `${body} == null` | 404 | Não existe essa operação |
| `${body[estado]} in 'aceptada,confirmada,estacionada'` | 409 | O que foi aceito e o que foi confirmado deu certo; o que está estacionado é liberado pela varredura (a lição 17) |

Todo o resto (`pendiente`, `no_se_sabe`, `conciliacion_pendiente`, `rechazada`) segue para o TODO 3. Antes de seguir,
guarde o estado em uma variável `antes`: ele vai na resposta final.

### TODO 3: uma tentativa, aqui mesmo

Esta é a dificuldade de hoje. O reprocessamento **não** vai para a fila: faz-se uma tentativa naquele instante, com as mesmas
rotas que a distribuição usa.

1. Um `setVariable` `tipo` com `constant` `manual`. Com isso, o `INSERT` de `intentar` anota quem e por quê (as
   variáveis `pedido_por` e `motivo` já existem).
2. Um `to` para `direct:preparar`, com a linha no corpo. Ele monta o envelope, a impressão digital e o número de controle como
   a distribuição faz.
3. Um `doTry` com um `to` para `direct:intentar`. No `doCatch` de `java.io.IOException` (o «não se soube» da lição 20),
   um `UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}`: uma tentativa manual
   não tenta de novo sozinha, volta à vista de quem a pediu.
4. Um `filter`: se `${variable.desenlace} == 'aceptada'`, um `to` para `direct:barrido`, como faz a distribuição. Algo
   estacionado podia estar esperando este passo (a lição 17).

### TODO 4: diga como foi

Responda 200 com o que ficou nas variáveis:

```yaml
- setBody:
    expression:
      simple:
        expression: '{"resultado":"reprocesado","operacion":${variable.operacion},"intento":${variable.intento},"antes":"${variable.antes}","desenlace":"${variable.desenlace}","codigo":${variable.codigo},"version":"${variable.version}"}'
```

Repare no último campo. A tentativa sai com **o que está implantado hoje**: o mapeamento e o modelo vigentes, não
os do dia em que falhou. Se entre a queda e o rerun alguém corrigiu o mapeamento, o rerun sai corrigido, e a
versão com que saiu fica anotada na tentativa (`version_mapeo`) e na resposta.

## 4. Quebre um de propósito

Com tudo concluído, tire o segundo ramo do `choice` do TODO 2, o do 409. Rode `./verifica`.

Antes, faça a previsão: o `verifica` pede um rerun do 4001, que já está `aceptada` no parceiro. O que acontece no destino?

O router faz a tentativa manual: chama de novo o canal do parceiro, e a operação tem uma tentativa a mais. Sai em
vermelho a marca do rerun do que foi aceito. A trilha de auditoria já tem um reprocessamento de que ninguém precisava, e em um destino que não
reconhecesse repetidos haveria uma duplicação. **O router não pergunta ao
destino se ele já tem o pedido: pergunta à própria base.** Devolva o ramo.

## 5. Confira

```bash
./verifica
```

O `verifica` desliga o SFTP e manda quatro pedidos; põe a API em modo lento e manda o 4005; depois devolve tudo e
faz cinco reruns, um para cada passo travado. No fim pede um de algo aceito e um sem motivo. Está concluída
quando aparecem as onze marcas verdes:

```
✅ Arrancaron el router (con su POST /rerun), los tres canales y el sondeo
✅ Con el SFTP caído, el canal del socio de la 4001 a la 4004 quedó pendiente (lo que se reprocesa)
✅ Con la API lenta, el canal rest de la 4005 no supo cómo le fue y quedó (…)
✅ Rerun: el canal del socio de la 4001 a la 4004 y el rest de la 4005 quedaron aceptados
✅ Cada rerun quedó como intento «manual», con quién lo pidió, por qué y la versión del mapeo con que salió
✅ Sin duplicados: un 850 por orden en el socio, y la API creó la 4005 una vez y al rerun contestó 200 «reenvío»
✅ Solo el paso que falló: la API y el ERP de la 4001 a la 4004 no recibieron intentos nuevos
✅ Rerun de lo que ya está aceptado: 409, sin intento nuevo y la operación sigue (…)
✅ Rerun sin motivo: 400 (sin quién y por qué, el rastro quedaría mudo)
✅ El ERP reconoce un pedido que ya tiene: 200 «ya registrado», sin renglones nuevos
✅ El 997 del socio confirmó los cuatro 850 reprocesados
```

Veja a sexta. A API **já tinha** o 4005: a chamada lenta o registrou depois de o canal cortar, como na lição 20.
O rerun o mandou outra vez com a mesma chave, e a API respondeu 200 `reenvío` («reenvio»), não 201. É isso que torna seguro o
rerun de um «não se sabe».

Faça um rerun à mão e veja a resposta:

```bash
curl -s -XPOST localhost:8080/rerun -H 'Content-Type: application/json' \
  -d '{"orden":"4001","momento":"creada","canal":"edi","pedido_por":"yo","motivo":"probar"}'
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/rerun.camel.yaml solucion/orden-router/rerun.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `Esperaba 5 intentos manuales` e há 0 | Falta o `setVariable` `tipo` em `manual`, ou a tentativa foi posta na fila em vez de chamar `direct:intentar` |
| `Se reprocesó de más: la API y el ERP … pasaron de … a … intentos` | A consulta não filtra por canal: está pegando as três operações do evento do ciclo de vida |
| `Rerun de la 4001 ya aceptada: contestó …` | Falta o ramo do 409, ou ele não leva `stop` |
| `Rerun sin motivo: esperaba 400` | No `filter` do TODO 1 falta `motivo`, ou falta o `stop` |
| `de las 5 operaciones reprocesadas, … quedaron aceptadas` | Falta `direct:preparar` antes de `direct:intentar`: sem ele não há envelope nem número de controle |
| `No pude poner la API en modo lento` | A API é de antes: recrie-a como na lição 20 |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Um reprocessamento é mais uma tentativa, não uma operação nova.** Se fosse nova, cada destino a veria como outro pedido.
Como é a mesma operação, sai com a mesma chave e o mesmo número de controle, e cada destino pode reconhecê-la.

**Reprocessa-se o passo, não o pedido.** Mandar tudo de novo «por via das dúvidas» enche a trilha de ruído e aposta que
todos os destinos aguentam um repetido. Manda-se o que falhou e nega-se o que deu certo.

**Quem e por quê não são burocracia.** Daqui a um mês alguém vai perguntar por que o 4005 tem duas tentativas.
Sem `pedido_por` nem `motivo`, a resposta é «não sei»; com eles, «o suporte pediu porque a API esteve lenta».

**Sai com o que há hoje, e isso é anotado.** Um rerun não revive o passado: usa o mapeamento vigente. Por isso cada tentativa guarda a própria
versão, e por isso dá para saber o que o destino recebeu a cada vez.

## Exercício: o balcão de reclamações

Com a versão própria concluída, rode `./verifica` e depois:

1. Consulte as tentativas do canal do parceiro do 4001:
   `SELECT n, tipo, pedido_por, motivo, desenlace, version_mapeo FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.orden = '4001' AND p.canal = 'edi' ORDER BY n;`
   Conte a história dessa operação em três frases, com o que diz cada linha.
2. Peça três reruns à mão: um de um pedido que não existe (`9999`), um do 4001 no canal `rest` e um sem
   `pedido_por`. Anote o que cada um responde e por quê.
3. Imagine que amanhã o mapeamento do parceiro é corrigido para a versão 2 e faz-se um rerun de um 850 que tinha falhado ontem.
   Com que versão ele sai? Onde se pode conferir isso? Por que não seria melhor que saísse com a versão de ontem?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O reprocessamento à mão é mais uma tentativa, com nome e motivo](../decisiones/27-el-rerun-es-un-intento-manual.md)

## Revisão

1. Que estados de uma operação podem ser reprocessados, e quais não? Por que o que está estacionado também não?
2. Por que o rerun lê a operação da base e não confia no que diz o corpo?
3. O que evita que um rerun do 4005 crie outro pedido na API?
4. Por que sem `pedido_por` ou sem `motivo` a resposta é 400?
5. **A pergunta de encerramento:** o rerun sai com a configuração vigente e deixa anotada a própria versão. Por que com a
   de hoje, e para que serve anotá-la?

**A seguir:** na lição 24 o problema não é o destino estar fora do ar, e sim derrubá-lo com as próprias chamadas. Com três réplicas
do int-bd, quantas chamadas ao mesmo tempo o ERP recebe?
