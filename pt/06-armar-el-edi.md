# Lição 06 — Montar o EDI

> **O que será alcançado:** converter o pedido no documento que o parceiro comercial espera, um **X12 850**, e
> conferir com um leitor em espanhol que ficou bem montado. Hoje não é preciso o ambiente: só o Docker.

**Antes de começar é preciso ter:** a lição 03 concluída. Esta lição usa a mesma ideia de mapeamento e casos de teste de referência.

## A novidade de hoje, em uma frase

A API fala JSON e o ERP fala tabelas. O parceiro comercial fala **EDI**: um formato de texto que as empresas usam
desde antes de existir a internet para mandar umas às outras pedidos de compra, faturas e avisos de embarque. O que
este parceiro usa se chama **X12**, e o pedido de compra é o documento **850**.

Assim é um pedido 850:

```
ISA*00*          *00*          *ZZ*TIENDANORTE    *ZZ*SOCIO01        *261112*1015*U*00401*000001001*0*P*>~
GS*PO*TIENDANORTE*SOCIO01*20261112*1015*1001*X*004010~
ST*850*0001~
BEG*00*SA*1001**20261112~
N1*ST*Tienda T-014*92*T-014~
PO1*1*2*EA*89.50**VN*SKU-0001~
PID*F****Taza blanca~
PO1*2*1*EA*249.00**VN*SKU-0042~
PID*F****Mantel azul~
CTT*2~
SE*9*0001~
GE*1*1001~
IEA*1*000001001~
```

Não tem nomes de campos como o JSON: **cada dado vale pela posição**. Parece ilegível, mas hoje se aprende
a lê-lo.

---

## 1. Prepare a pasta

```bash
cd lecciones/06-armar-el-edi
cp -R inicio mio
```

## 2. Faça a previsão

Veja o 850 de cima. Sem procurar nada, responda em um papel:

1. Há um `*` e um `~` em cada linha. O que cada um deve separar?
2. Em que linha deve ir o número do pedido (1001)? E as linhas do pedido?
3. As palavras `TIENDANORTE` e `SOCIO01` aparecem duas vezes. Na primeira linha levam espaços a mais no fim, e
   na segunda não. Por que será?

## 3. Leia-o com o leitor

O curso traz um leitor que explica um X12 em espanhol. A partir da pasta da lição:

```bash
../../herramientas/leer-x12 ../../datos/edi/850-ejemplo.edi
```

Compare com a previsão:

- **`~` fecha um segmento.** Um segmento é como uma linha do documento, e o nome dele são as primeiras letras
  (`ISA`, `BEG`, `PO1`…).
- **`*` separa os elementos** de um segmento. O leitor os numera: em `BEG*00*SA*1001**20261112`, o elemento 03
  é `1001`, o número do pedido. Dois `*` seguidos querem dizer que esse elemento vai vazio.
- **As linhas do pedido** são os pares `PO1` (quantidade, unidade, preço e produto) e `PID` (descrição).

O documento vai **dentro de dois envelopes**, como uma carta dentro de um pacote:

| Abre | Fecha | O que é |
|---|---|---|
| `ISA` | `IEA` | O envelope de fora: quem manda, para quem e um número de controle |
| `GS` | `GE` | Um grupo de documentos do mesmo tipo (aqui, pedidos de compra) |
| `ST` | `SE` | O documento: o pedido 850 |

Cada envelope se fecha repetindo o próprio número de controle, e o `SE` além disso **conta** quantos segmentos o
documento tem, de `ST` a `SE`. Assim o parceiro sabe se algo se perdeu no caminho.

E os espaços da pergunta 3? O `ISA` é **de largura fixa**: mede sempre 106 caracteres, e cada dado ocupa um
lugar exato. `TIENDANORTE` tem 11 letras e o lugar dele é de 15, então é preenchido com 4 espaços.

No fim, o leitor faz seis conferências (a largura do ISA, os números de controle, a contagem do SE…). Se alguma
falha, sai em vermelho.

## 4. O mapeamento calcula, o modelo arruma

A rota `mio/orden-a-edi.camel.yaml` monta o 850 em dois passos, cada um com um único trabalho:

| Peça | O trabalho dela | Onde vive |
|---|---|---|
| **O mapeamento** (JSONata, como na lição 03) | **Calcula** tudo: os preenchimentos de 15, o controle de 9 dígitos (1001 → `000001001`), as datas, os preços com duas casas decimais, a contagem do `SE` e o texto limpo | `mio/mappings/edi/850/v1/` · vem pronto |
| **O modelo** (Mustache) | **Arruma** o que já vem calculado no lugar certo do documento. Não calcula nada | `mio/templates/edi/850/v1/850.mustache` · **fica por fazer** |

Teste-o como na lição 03, e abra `mio/mappings/edi/850/v1/cases/dos-renglones/esperado.json` para ver que dados
chegam ao modelo:

```bash
../../herramientas/probar-mapeo mio/mappings/edi/850/v1
```

**Por que separar os dois trabalhos?** Porque cada um se testa por conta própria. O cálculo tem os casos de teste de referência dele; o
modelo só tem de pôr cada dado no lugar. Com os cálculos misturados dentro do modelo, não haveria
como testá-los.

## 5. Termine o modelo

Abra `mio/templates/edi/850/v1/850.mustache`. Já traz o envelope, o início do documento e para quem vai:

```
ISA*00*          *00*          *ZZ*{{{body.isa.emisor}}}*ZZ*{{{body.isa.receptor}}}*…~
GS*PO*{{{body.gs.emisor}}}*…~
ST*850*{{{body.st.control}}}~
BEG*00*SA*{{{body.beg.orden}}}**{{{body.beg.fecha}}}~
N1*ST*{{{body.tienda.nombre}}}*92*{{{body.tienda.clave}}}~
```

Cada `{{{ … }}}` é uma lacuna que o Mustache preenche com um dado do mapeamento. Faltam duas partes. As instruções
completas estão em `LEEME.md`, junto ao modelo.

**As linhas do pedido.** É preciso um `PO1` e um `PID` para cada linha, e as linhas estão em `body.lineas`. No Mustache,
uma **seção** repete o que tem dentro uma vez para cada elemento de uma lista:

```
{{#body.lineas}}
… lo que va por cada renglón, usando {{{n}}}, {{{cantidad}}}, {{{precio}}}, {{{sku}}} y {{{descripcion}}} …
{{/body.lineas}}
```

Dentro da seção já se está em uma linha do pedido, assim como dentro de `renglones.{ … }` em JSONata. Use o 850
de exemplo e o leitor para saber em que posição vai cada dado.

**O fechamento.** Depois das linhas: `CTT` (quantas linhas), `SE` (a contagem de segmentos e o controle do
`ST`), `GE` e `IEA`. Todos os números já vêm calculados em `body.ctt`, `body.se.segmentos` e nos controles.

Duas regras:

- **Não escreva comentários dentro do modelo:** onde havia um comentário, o Mustache deixa uma linha vazia, e uma
  linha vazia é um erro em um X12.
- **Use chaves triplas `{{{ }}}`.** No passo 7 se verá por quê.

## 6. Rode a rota e leia o resultado

A partir de `mio/`, coloque dois pedidos em `entrada/` e inicie a rota:

```bash
cd mio
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json ../../../datos/ordenes/orden-1005-caracteres.json entrada/
../../../herramientas/camel run orden-a-edi.camel.yaml
```

Quando aparecer `Generé el 850 de la orden 1005`, pare-a com `Ctrl+C` e leia o que saiu:

```bash
../../../herramientas/leer-x12 salida/850-1001.edi
diff salida/850-1001.edi ../../../datos/edi/850-ejemplo.edi
```

Se o leitor dá as seis marcas verdes e o `diff` não imprime nada, o 850 do pedido 1001 é idêntico ao de exemplo.

## 7. Os caracteres que quebram um documento

Abra `datos/ordenes/orden-1005-caracteres.json`, na pasta raiz do curso. Os dois produtos dele têm nomes
incômodos:

| Produto no pedido | Como sai no 850 |
|---|---|
| `Taza & plato` | `Taza & plato` |
| `Juego 4*6 ~ cristal` | `Juego 4 6   cristal` |

**Faça a previsão:** por que o segundo nome mudou? Pense no que o parceiro faria ao ler `PID*F****Juego 4*6 ~ cristal~`.

O `*` e o `~` são os separadores do documento, e o `>` também: o `ISA` os declara (o `>` é o último
elemento dele). Se vão dentro de um dado, o parceiro leria um elemento a mais e um segmento cortado ao meio. Por isso o
mapeamento os troca por espaços antes que cheguem ao modelo, e o caso
de teste de referência `caracteres-especiales` confere que ele faça isso.

Agora o `&`. **Não é separador**: o X12 o aceita dentro de um dado, e o parceiro o leria bem. O risco dele é outro, e
vem do modelo. Troque **somente** `{{{descripcion}}}` por `{{descripcion}}`, com chaves duplas, e rode a
rota de novo. Veja o `PID` do pedido 1005:

```
PID*F****Taza &amp; plato~
```

O Mustache nasceu para fazer páginas web, e com chaves duplas «protege» o texto como se fosse HTML: o `&` se
converte em `&amp;`. Em uma página web é o correto. Em um X12, o parceiro receberia um produto que se chama
«Taza &amp; plato». As chaves triplas dizem ao Mustache: **ponha o dado tal como está**. Volte às chaves triplas.

## 8. Confira

A partir da pasta da lição:

```bash
cd ..
./verifica
```

O `verifica` esvazia `mio/entrada/` e `mio/salida/`, gera o 850 dos pedidos 1001 e 1005, e os confere com o
leitor. Está concluída quando aparecem as cinco marcas verdes:

```
✅ El mapeo del 850 pasa sus casos dorados
✅ El 850 de la 1001 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1005 es un X12 bien armado (el lector no encontró fallas)
✅ El 850 de la 1001 es idéntico al de ejemplo
✅ El 850 de la 1005 (con & y *) es el esperado
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/templates/edi/850/v1/850.mustache solucion/templates/edi/850/v1/850.mustache` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `trae &amp;: usa llaves TRIPLES` | Alguma chave ficou dupla: volte ao passo 7 |
| `no pasa la revisión del lector` | Rode `leer-x12` sobre esse arquivo: a conferência em vermelho diz o que falta (quase sempre o fechamento) |
| `no es idéntico al de ejemplo` | Compare-os com `diff`: costuma ser um elemento em outra posição ou um `*` a menos |

---

## O que aprendemos montando esta lição

Dois erros reais que apareceram ao construí-la, e por que não chegaram a quem faz o curso:

- **Uma função que «limpa» pode quebrar a largura fixa.** O JSONata tem `$trim`, que tira espaços. Também junta
  os espaços repetidos em um só, e isso teria apagado o preenchimento do `ISA`. Por isso o mapeamento usa `$pad`, que
  preenche, e nunca `$trim` sobre um campo de largura fixa.
- **Contar à mão falha.** Ao escrever o 850 de exemplo, o `SE` dizia 10 segmentos onde havia 9. O leitor o
  marcou em vermelho. Por isso a contagem é feita pelo mapeamento e conferida por uma ferramenta, não por uma pessoa.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O EDI é montado com um modelo sem lógica](../decisiones/07-mustache-sin-logica-para-el-edi.md)

## Revisão

1. Em um X12, o que o `*` separa e o que o `~` separa?
2. Quais são os três envelopes do documento, e que segmento abre e fecha cada um?
3. Por que o `ISA` leva espaços de preenchimento?
4. O que faz o mapeamento e o que faz o modelo? Por que convém não misturá-los?
5. Por que não pode ir um `*` dentro do nome de um produto, e o que acontece com um `&` quando se usam chaves duplas?

**A seguir:** na lição 07 o 850 deixa de ficar em `salida/`: viaja para a caixa postal SFTP do parceiro, com o cuidado de
que ele não pegue um arquivo pela metade.
