# Lição 11 — Nem tudo vai para todos

> **O que será alcançado:** que o router decida, pedido por pedido, se ele vai para o parceiro ou não, conforme **a loja de onde** vem.
> A API e o ERP continuam recebendo todos; o parceiro, só os dele.

**Antes de começar é preciso ter:** a lição 10 concluída e o ambiente da 00 ligado (com o ERP recriado na
10).

## A novidade de hoje, em uma frase

Na 10 cada pedido ia para os três destinos. Mas o parceiro comercial não opera todas as lojas: só as
**franquias**, a T-501 e a T-502. Se o pedido de uma loja própria for mandado a ele, chega um pedido que não é dele e
que ele poderia atender. Hoje o canal do parceiro **olha o pedido antes de mandá-lo**.

**A imagem:** o carteiro que entrega em um bairro. Ele não deixa todas as cartas em todas as casas: lê o endereço de
cada uma e decide.

Este padrão se chama **Content-Based Router**: o destino depende do conteúdo da mensagem.

| | Multicast (a 10) | Content-Based Router (hoje) |
|---|---|---|
| Para quem vai? | Para todos os destinos, sempre | Só para os que cumprem uma condição |
| Quem decide? | Ninguém: a lista é fixa | Uma regra que lê o pedido |

Hoje os dois são usados juntos: o multicast continua distribuindo para os três canais, e **dentro** do canal do parceiro uma
condição decide se o pedido segue ou não.

---

## 1. Prepare a pasta

```bash
cd lecciones/11-no-todo-va-a-todos
cp -R inicio mio
```

## 2. Faça a previsão

Abra o lote de 10 pedidos, a pasta `datos/ordenes/lote-10/`, na raiz do curso. Ela tem dez pedidos e um `LEEME.md`. Sem rodar
nada, abra os pedidos, veja o campo `"tienda"` de cada um e responda em um papel:

1. Quantos dos dez são de franquia (T-501 ou T-502)?
2. Com o router como está hoje, quantos arquivos são esperados na caixa postal do parceiro? E ao terminar a lição?
3. Quantos pedidos a API e o ERP deveriam ter?

Confira a de hoje:

```bash
./verifica
```

Saem duas marcas vermelhas. O parceiro tem **os dez** 850, e a API e o ERP também têm dez. Hoje o canal do parceiro
manda tudo.

## 3. Leia o canal do parceiro

Abra `mio/orden-router/orden-router.camel.yaml` e procure a rota `canal-socio`. Agora ela tem duas partes:

| Rota | O que faz |
|---|---|
| `canal-socio` | **Decide** se o pedido vai para o parceiro. Tem o TODO |
| `al-socio` | **Manda** o pedido ao int-edi e deixa a linha do que ele respondeu, como o canal da 10 |

Separá-las deixa a decisão em um único lugar: se amanhã mudar quem é parceiro, mexe-se em uma linha e não na chamada.

## 4. Escreva a condição

No TODO, troque o `to: direct:al-socio` por um `choice`. Um já foi usado na 09, no router, para decidir de
onde saía o fio:

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

**A condição.** A loja do pedido já está guardada em `${variable.tienda}`. Na linguagem `simple` do Camel,
`in` pergunta se um valor está em uma lista separada por vírgulas:

```
${variable.tienda} in 'T-501,T-502'
```

**O otherwise.** Os pedidos que não são de franquia não vão para o parceiro, mas o canal **tem de dizer isso**: um
`setBody` com `{"canal":"socio","resultado":"no aplica"}`. Se o canal ficasse calado, a loja não saberia se o
router decidiu não mandá-lo ou se esqueceu. `no aplica` («não se aplica») e «falhou» não são a mesma coisa, e a resposta deve distinguir os dois.

## 5. Compare com a previsão

```bash
./verifica
```

Agora o parceiro tem quatro 850, os dos pedidos 2002, 2005, 2007 e 2010, e a loja recebeu `no aplica` nos
outros seis. Deu para acertar o 4? A API e o ERP continuam com os dez: a condição só vive no canal do parceiro.

Se sair `Socio: … y tiene «850-2002.edi 850-2007.edi»`, a condição deixa de fora a T-502. Confira a lista.

## 6. Confira

Está concluída quando aparecem as cinco marcas verdes:

```
✅ Arrancaron el router y los tres canales
✅ API: tiene las 10 órdenes del lote
✅ ERP: tiene las 10 órdenes REGISTRADO
✅ Socio: solo llegaron los 850 de franquicia (850-2002.edi 850-2005.edi 850-2007.edi 850-2010.edi)
✅ El router le dijo a la tienda «no aplica» en las 6 que no son de franquicia
```

As marcas da API, do ERP e do parceiro contam **no destino**: o registro da API, as linhas do ERP e os
arquivos da caixa postal.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/orden-router.camel.yaml solucion/orden-router/orden-router.camel.yaml` diz em que elas são
diferentes.

| O que aparece | O que fazer |
|---|---|
| O parceiro tem os dez | A condição não está, ou o `to` ficou fora do `when` |
| O parceiro tem dois | A lista da condição tem uma loja só |
| `Esperaba «no aplica» en 6 respuestas y lo vi en 0` | Falta o `setBody` do `otherwise` |
| `Tu ERP no tiene la columna correlacion` | Recrie o ERP como diz a 10 |

---

## Exercício: a regra fora da rota

Hoje a lista de franquias está **escrita dentro da rota**. Se o parceiro abrir outra franquia, alguém tem de
editar o YAML, conferi-lo e implantá-lo de novo para mudar um dado do negócio. Mova-a para a **configuração**: um
valor que se muda sem mexer na rota.

**1.** Na condição, troque a lista por uma variável de ambiente com um valor por padrão:

```
${variable.tienda} in '{{env:FRANQUICIAS:T-501,T-502}}'
```

`{{env:FRANQUICIAS:…}}` quer dizer: «use a variável de ambiente `FRANQUICIAS`; se ela não existir, use o que vem depois
dos dois-pontos». Rode `./verifica`: deve continuar em cinco de cinco, porque usa o valor por padrão.

**2.** Agora mude a regra **sem mexer na rota**. Crie `mio/franquicias.yaml` com isto:

```yaml
services:
  orden-router:
    environment:
      FRANQUICIAS: "T-501"
```

É um arquivo que **se soma** ao compose da lição: acrescenta uma variável de ambiente ao router e não muda mais nada.

**3.** Esvazie a caixa postal do parceiro, suba os serviços com os dois arquivos, mande o lote e veja a caixa postal:

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

Antes de rodar, faça a previsão: quantos 850 são esperados na caixa postal, e quantas respostas com `no aplica`?

Nas respostas, repare só na linha do canal `socio`. A API e o ERP já tinham esses pedidos do último
`verifica`, de modo que as linhas deles dizem outra coisa; para o parceiro, a caixa postal começou vazia. Ao terminar, rode `./verifica`
para deixar tudo como estava.

**4.** Responda: o que mudou e o que não mudou? Quem deveria poder mudar `FRANQUICIAS`: quem programa a rota ou quem
administra o negócio?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Quem recebe o quê se decide pelo conteúdo, em um único lugar](../decisiones/14-quien-recibe-se-decide-en-un-lugar.md)

## Revisão

1. Que diferença há entre um Multicast e um Content-Based Router? Dê um exemplo de quando usar cada um.
2. O que faz `in` na condição `${variable.tienda} in 'T-501,T-502'`?
3. Por que o canal responde `no aplica` em vez de ficar calado?
4. Por que a decisão vive em `canal-socio` e a chamada em `al-socio`?
5. O que se ganha ao mover a lista de franquias para uma variável de ambiente?

**A seguir:** na lição 12 um serviço deixa de ser um só: rodam-se várias réplicas dele para que aguente mais
pedidos.
