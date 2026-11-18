# Lição 14 — Um arquivo, vários pedidos

> **O que será alcançado:** dividir o arquivo que o armazém deixa com vários embarques, de modo que cada pedido receba
> o próprio 856 e o status EMBARCADO, e que um embarque ruim não detenha os demais.

**Antes de começar é preciso ter:** a lição 13 concluída e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Na 13 o armazém deixava um arquivo por embarque. Na vida real quase nunca é assim: o armazém junta o que saiu
pela manhã e deixa **um único arquivo, um lote**, com cinco embarques dentro. Hoje ele é dividido em partes, uma por
pedido. O padrão se chama *Splitter*.

**A imagem:** a caixa que chega ao escritório com a correspondência de todo o andar. Ninguém entrega a caixa inteira
na primeira mesa: ela é aberta, cada envelope é tirado e levado ao destinatário. E se um envelope traz um nome que
ninguém conhece, ele é separado e os demais são entregues.

---

## 1. Prepare a pasta

```bash
cd lecciones/14-un-archivo-varias-ordenes
cp -R inicio mio
```

Abra o lote bom, `datos/embarques/lote-bodega-0001.json`. No alto ele traz os dados do lote (número, armazém,
quando foi gerado) e depois uma lista, `embarques`, com cinco elementos. Cada elemento é um embarque como o da
13: pedido, transportadora, código de rastreio e linhas do pedido.

O lote ruim é `lote-bodega-0002.json`: cinco embarques, e o terceiro é do pedido **9999**, que a loja nunca
criou. O router não vai aceitá-lo.

## 2. Faça a previsão

Antes de abrir a rota, responda em um papel:

1. O lote 0001 traz cinco embarques. Quantos 856 são esperados no SFTP do parceiro?
2. No lote 0002, o terceiro embarque falha. O que acontece com o quarto e o quinto? E com o primeiro e o segundo,
   que já passaram?
3. Ao terminar o lote 0002, para onde o arquivo deveria ir: para `.camel/`, para `.error/` ou para nenhum dos dois?

Veja como está hoje:

```bash
./verifica inicio
```

Hoje a sondagem manda ao router **o lote completo como se fosse um embarque**. O router procura um único pedido no
corpo, não o encontra e o rejeita. As marcas da API, do ERP e do parceiro saem em vermelho.

## 3. O que será escrito

Abra `mio/sondeo-bodega/sondeo-bodega.camel.yaml`. O `from` já traz os quatro cuidados da 13; só mudou o
`include`, que agora pega os `lote-bodega-<algo>.json`. O trabalho está nos três TODO dos passos:

| TODO | O que fazer | Para quê |
|---|---|---|
| 1 | Envolva os passos em um `split` com a expressão JSONPath `$.embarques[*]` | Uma mensagem para cada embarque da lista |
| 2 | Escreva `stopOnException: false` no `split` | Que um embarque ruim não detenha os demais |
| 3 | Mova para dentro do `split` os passos que guardam o pedido e chamam o router, e acrescente um `marshal` para JSON antes de chamar | Que cada parte viaje sozinha até o router |

Dentro do `split`, os passos vão no próprio `steps:`, com mais indentação. Assim fica o esqueleto; os passos de
dentro são os que já estão no arquivo:

```yaml
- split:
    jsonpath:
      expression: "$.embarques[*]"
    stopOnException: false
    steps:
      - setVariable: …   # la orden de esta parte
      - marshal:
          json: {}
      - …                # quitar encabezados, Content-Type, llamar al router, anotar
```

Duas coisas novas:

- **Cada parte chega como dados, não como texto.** O JSONPath entrega cada embarque como um mapa (chaves e
  valores). Por isso o pedido já não é tirado com `$.orden`, e sim com `${body[orden]}`, e antes de mandar a parte ao router
  é preciso transformá-la em texto JSON com `marshal`. Sem o `marshal`, o router recebe algo que não é JSON.
- **`stopOnException: false` já é o valor por padrão.** Escreve-se mesmo assim: quem lê a rota vê que foi pensado, não
  que foi esquecido.

## 4. Quebre um de propósito

Com o `split` concluído, troque `stopOnException` para `true` e rode `./verifica`. Antes, faça a previsão: quantos dos
quatro embarques bons do lote 0002 chegam?

Sai em vermelho a marca do lote 0002: só chegam dois, o 4001 e o 4002. O `split` vai em ordem; ao falhar o
terceiro (o 9999) ele para, e o 4004 e o 4005 ficam sem aviso, embora tenham saído do armazém. Por um erro
de um pedido que nem existe, dois clientes não sabem onde está o pacote deles. Volte para `false`.

## 5. Confira

```bash
./verifica
```

O `verifica` cria os pedidos 4001 a 4005, deixa o lote 0001 na caixa postal e confere; depois limpa os destinos e deixa
o lote 0002. Está concluída quando aparecem as sete marcas verdes:

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Lote 0001: las 5 órdenes quedaron EMBARCADO en la API y en el ERP
✅ Socio: 5 archivos 856, uno por orden, y los 5 pasan el lector X12
✅ Buzón: el lote 0001 se apartó a .camel/
✅ Lote 0002: los otros 4 embarques pasaron (4001, 4002, 4004 y 4005 EMBARCADO en la API y el ERP)
✅ Sondeo: anotó en su bitácora que el embarque de la 9999 no pasó
✅ Buzón: el lote 0002 se apartó a .error/ para que alguien vea el embarque malo
```

Repare nas duas últimas: o lote 0002 terminou em `.error/` **embora quatro dos cinco embarques dele tenham passado**. O
`split` continuou com os demais, mas no final lembra que houve um erro, e a sondagem trata o arquivo como falho.
O arquivo em `.error/` diz «confira este»; o log diz qual:

```bash
DONDE=mio docker compose -f compose.yaml logs sondeo-bodega | grep "no aceptó"
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/sondeo-bodega solucion/sondeo-bodega` diz em que elas são diferentes.

| O que aparece | O que fazer |
|---|---|
| `solo 0 de 5 órdenes quedaron EMBARCADO` | Os passos que chamam o router ficaram fora do `split`, ou falta o `split`. Confira a indentação: eles têm de estar dentro do `steps:` dele |
| O log diz que o router não aceitou nenhum embarque | Falta o `marshal` para JSON: o router recebe um mapa, não texto |
| `el malo detuvo a los demás` | `stopOnException` ficou em `true` |
| `Al ERP le falta la tabla embarque` | Recrie o ERP como diz a 13 |

---

## O que isto ensina fora do curso

**Os lotes são a regra, não a exceção.** Bancos, armazéns e sistemas antigos entregam o trabalho em arquivos
grandes, uma vez por dia ou a cada hora. Dividir o arquivo e tratar cada parte como se tivesse chegado sozinha é a
primeira coisa que uma integração faz com eles.

**Um lote não é uma transação.** Na 05 aprendeu-se o «tudo ou nada» dentro do ERP. Aqui é o contrário: cada embarque
é de um cliente diferente, e a falha de um não é razão para deixar de avisar os outros quatro. O que convém, tudo ou
nada ou um por um, depende de as partes dependerem umas das outras.

## Exercício: o lote com o erro dele

Com a versão própria concluída:

1. Abra `buzon/.error/lote-bodega-0002.json`. No lugar de quem atende o armazém, como saber qual dos cinco
   embarques conferir? Basta o arquivo, ou é preciso o log?
2. Rode `./verifica` de novo sem mudar nada. Algum 856 ou algum EMBARCADO foi duplicado? Por que não? (Dica: o
   verifica limpa os destinos antes de cada lote, e a API reconhece a chave de cada embarque.)
3. Imagine que o armazém corrige o 9999 e manda de novo o lote 0002 completo. O que aconteceria com os quatro
   pedidos que já estavam EMBARCADO? Escreva a resposta: o tema de «quando duas mensagens são a mesma?» é a
   lição 18.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Um arquivo do armazém é dividido nos embarques dele, e um ruim não detém os demais](../decisiones/17-partir-el-archivo.md)

## Revisão

1. O que faz um Splitter, e por que ele é necessário com um lote do armazém?
2. Por que dentro do `split` o pedido é lido com `${body[orden]}` e não com `$.orden`?
3. Para que serve o `marshal` antes de chamar o router?
4. O que faz `stopOnException: false`? Por que se escreve se já é o valor por padrão?
5. **A pergunta de encerramento:** o lote 0002 terminou em `.error/` embora quatro embarques tenham passado. É um
   problema? O que esse arquivo diz, e o que diz o log?

**A seguir:** na lição 15 a pergunta de encerramento da 12 volta: se o router tem réplicas, onde vive o que diz respeito a
cada pedido? Hoje o router não se lembra de nada; amanhã ele guarda cada pedido, cada canal e cada tentativa na própria base.
