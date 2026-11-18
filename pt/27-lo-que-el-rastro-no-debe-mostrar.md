# Lição 27 — O que a trilha não deve mostrar

> **O que será alcançado:** que a trilha de auditoria de um pedido (o que a base guarda para diagnosticar e o que o
> percurso da lição 26 mostra) não revele quem é a cliente: nem o nome, nem o e-mail, nem o telefone, nem a rua. E que,
> mascarada, continue servindo para entender o que aconteceu. Para o destino, o pedido continua saindo completo.

**Antes de começar é preciso ter:** a lição 26 concluída e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Na lição 26 foi montado o percurso: qualquer pessoa que possa consultá-lo vê cada tentativa com o que saiu, por inteiro. Isso
inclui a pessoa que comprou. Hoje o pedido de teste é o 4008, que traz nome, e-mail, telefone e endereço de
uma cliente (inventados, como tudo neste curso). Quem atende o suporte precisa saber o que aconteceu com o pedido;
**não** precisa saber onde a cliente mora. Hoje os dados pessoais são **mascarados** antes de entrarem na trilha.

**A imagem:** o extrato do cartão no aplicativo do banco. Ele mostra `**** **** **** 0428`: o suficiente
para saber qual é, não o suficiente para que alguém o use. O banco tem, sim, o número completo, e o usa para
cobrar; o que ele mascara é **o que se mostra**.

---

## 1. Prepare a pasta

```bash
cd lecciones/27-lo-que-el-rastro-no-debe-mostrar
cp -R inicio mio
```

Abra `datos/ordenes/tramo-2/orden-4008.json` e marque com um lápis cada dado que diga **quem é** a cliente. Depois
abra `mio/orden-router/reparto.camel.yaml` e procure na rota `intentar` o comentário «Desde la 27». Antes de ser guardado
em `payload`, o corpo passa por um mapeamento novo:

```
lo que sale ──→ al canal (completo)
            └─→ mappings/rastro/mascara/v1 ──→ variable rastro ──→ payload (tapado)
```

| Peça | O que guarda |
|---|---|
| O corpo que vai para o canal | O pedido **completo**: a API, o ERP e o parceiro precisam dele para entregar |
| `payload.cuerpo` | O que saiu, **mascarado** |
| `intento.sha256` | A impressão digital do que saiu **de verdade**, sem mascarar: continua servindo para reconhecer repetidos (lição 18) |

A rota já está conectada. O trabalho é o mapeamento: `mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`. Hoje
ele devolve tudo como chega (`$`). E tem os casos de teste de referência em `cases/`, como os mapeamentos da lição 03.

## 2. Faça a previsão

Responda em um papel:

1. No percurso do 4008, que dados da cliente apareceriam hoje? Quem pode consultar esse percurso?
2. Se **tudo** da cliente for mascarado, ainda daria para saber se o e-mail estava bem escrito ou se o código postal
   era válido?
3. A tabela `orden` também guarda o 4008 completo. Por que essa pode ter os dados?

Veja como está hoje:

```bash
./verifica inicio
```

A primeira marca sai em vermelho: os casos de teste de referência esperam os dados mascarados e o mapeamento os deixa passar. E mais abaixo, a
busca encontra o nome, o e-mail, o telefone e a rua da cliente na trilha e no percurso.

## 3. Mascare na medida

Antes de escrever, abra os três casos de teste de referência: `cases/orden-con-cliente`, `cases/embarque` e `cases/entrega`. Cada
um tem o próprio `entrada.json` e o próprio `esperado.json`. Compare-os e preencha esta tabela:

| Dado | Chega | Guarda-se | O que ainda diz |
|---|---|---|---|
| `cliente.nombre` | `Ana Ejemplo Prueba` | `A. E. P.` | As iniciais |
| `cliente.correo` | `ana.prueba@example.com` | `a***@example.com` | Se o domínio era válido |
| `cliente.telefono` | `+52 55 0000 0428` | `*** **** **28` | Os dois últimos dígitos, para reconhecê-lo |
| `cliente.direccion.calle` | `Calle de Ejemplo 28` | `***` | Nada |
| `recibio` (em uma entrega) | `Ana Ejemplo Prueba` | `A. E. P.` | As iniciais |
| `tienda`, `renglones`, `cp`, `colonia`… | | Igual | Tudo o que serve para diagnosticar |

O mapeamento tem dois TODO.

### TODO 1: a função `$mascara`

Em JSONata define-se uma função com `function($v, $modo) { … }` e guarda-se em uma variável com `:=`. Esta recebe
um valor e um modo, e devolve o valor mascarado. A forma dela é assim, com cada modo como uma pergunta `? :`:

```
(
  $mascara := function($v, $modo) {
    $not($exists($v)) ? $v
    : $modo = 'iniciales' ? …
    : $modo = 'correo'    ? …
    : $modo = 'telefono'  ? …
    : '***'
  };
  …   (aquí va el TODO 2)
)
```

A primeira linha cuida de um caso que se esquece: **se o valor não vem, que continue sem vir.** Um embarque não traz
cliente; sem essa linha, apareceria um `cliente` inventado.

As funções de JSONata necessárias para cada modo:

| Modo | Ideia | Funções |
|---|---|---|
| `iniciales` | Dividir o nome em palavras, tomar a primeira letra de cada uma com um ponto, e uni-las com espaço | `$split($trim($v), ' ')`, `$map(…, function($p) { $substring($p, 0, 1) & '.' })`, `$join(…, ' ')` |
| `correo` | A primeira letra, `***@` e o que vem depois da arroba | `$substring($v, 0, 1)`, `$substringAfter($v, '@')`, e `&` para unir textos |
| `telefono` | `*** **** **` e os dois últimos dígitos | `$replace($v, /[^0-9]/, '')` deixa só os dígitos; `$substring(…, -2)` toma os dois últimos |

### TODO 2: aplicá-la onde cabe

JSONata tem um operador para mudar **só uma parte** de um documento e deixar o resto igual, o de
transformação: `~> | ruta | { cambios } |`. Lê-se «no que está em `ruta`, ponha estas mudanças».

```
$
  ~> | cliente | {
       "nombre": $mascara(nombre, 'iniciales'),
       "correo": $mascara(correo, 'correo'),
       "telefono": $mascara(telefono, 'telefono')
     } |
  ~> | cliente.direccion | { "calle": $mascara(calle, 'todo') } |
  ~> | $ | { "recibio": $mascara(recibio, 'iniciales') } |
```

São três transformações encadeadas: uma em `cliente`, uma em `cliente.direccion` e uma na raiz (`$`), para o
`recibio` de uma entrega. O que não é nomeado fica igual: por isso o código postal (CP) e as linhas do pedido passam sem ser tocados.

Teste o mapeamento antes de subir qualquer coisa:

```bash
../../herramientas/probar-mapeo mio/orden-router/mappings/rastro/mascara/v1
```

## 4. Quebre um de propósito

Com o mapeamento concluído, acrescente o código postal ao que é mascarado, na transformação de `cliente.direccion`:
`"cp": $mascara(cp, 'todo')`. Rode `./verifica`.

Antes, faça a previsão: a trilha agora mascara mais. Isso é melhor?

Saem em vermelho a primeira marca (o caso de teste de referência esperava o CP) e a última: a trilha já não diz o CP. Se uma entrega
falha porque o CP não existe, a trilha já não deixa ver isso. **Mascarar demais também é um erro**: a trilha existe
para diagnosticar, e uma que não diz nada não serve. Um CP sozinho, sem rua nem nome, dificilmente aponta para a cliente.
Devolva-o como estava.

## 5. Confira

```bash
./verifica
```

O `verifica` testa os casos de teste de referência, manda o 4008, espera que os três canais dele respondam e depois **procura** os
quatro dados da cliente na trilha da base, no percurso e nos logs de todos os serviços. Está
concluída quando aparecem as sete marcas verdes:

```
✅ El mapeo rastro/mascara/v1 pasa sus casos dorados
✅ Arrancaron el router (que tapa los datos personales del rastro), los tres canales y el sondeo
✅ Control: la orden guardada sí tiene los 4 datos personales (la búsqueda encuentra lo que hay)
✅ En el rastro de la base (payload, intentos, acuses, avisos, conflictos): 0 datos personales
✅ GET /ordenes/4008/recorrido: la orden sí sale, y sin sus datos personales
✅ En los logs: la 4008 aparece (…) veces y sus datos personales 0
✅ Lo tapado sirve para diagnosticar: (…)
```

Veja a terceira. Antes de contar zeros, o `verifica` procura os mesmos quatro dados onde eles **têm** de estar, na
tabela `orden`, e os encontra. Se não os encontrasse ali, o «0» das marcas seguintes não provaria nada: poderia
ser que a busca estivesse mal escrita. A isso se chama um **controle positivo**: antes de acreditar em um zero,
confira que a busca sabe encontrar. E leia o zero com o alcance dele: «0 datos personales» quer dizer 0
ocorrências dos quatro dados que o `verifica` procura, nos lugares onde os procura.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata solucion/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`
mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `no pasa sus casos dorados` | Rode `probar-mapeo` como no passo 3: ele diz que caso e que campo não coincidem |
| O caso `embarque` falha com um `cliente` que não deveria estar lá | Falta a primeira linha de `$mascara`: se o valor não vem, que continue sem vir |
| `En el rastro de la base hay … apariciones` | Alguma transformação não é aplicada: confira o caminho de cada `~> | … |` |
| `GET /ordenes/4008/recorrido: … de los 4 datos personales a la vista` | A mesma coisa: o percurso lê de `payload` |
| `Lo que guarda el rastro de la 4008` sem o CP ou sem o domínio | Mascarou-se demais: o CP e o domínio do e-mail ficam |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Mascara-se o que se mostra, não o que se usa.** Os destinos precisam dos dados completos para entregar: a
transportadora precisa do endereço. A trilha é para diagnosticar, e é lida por mais pessoas do que as que deveriam conhecer
a cliente. Por isso se mascara ali e não no envio.

**Mascarar é um design, não um apagamento.** As iniciais, o domínio do e-mail, os dois últimos dígitos e o CP dizem o
suficiente para reconhecer e diagnosticar, e muito menos que o dado completo. Decidir o que fica é a parte difícil.
E mascarar campos não é anonimizar: juntos, ou com outras fontes, alguns dados podem voltar a apontar para a pessoa (o
exercício 2 explora isso). Uma trilha real precisa, além disso, decidir quem pode lê-la e por quanto tempo é guardada.

**A impressão digital continua sendo a do real.** A trilha guarda o mascarado, mas a impressão digital da lição 18 é tirada do que saiu
de verdade. Assim se continua reconhecendo um repetido sem guardar o dado.

**Antes de acreditar em um zero, procure onde ele de fato está.** Um «0 dados pessoais» pode ser uma trilha limpa ou uma busca
que não sabe buscar. O controle positivo é o que os distingue, e serve para qualquer medição que dê zero.

## Exercício: o que se vê e o que não se vê

1. Peça o percurso do 4008 (`curl -s localhost:8080/ordenes/4008/recorrido`) e preencha duas colunas: o que dá para
   saber do pedido e o que **não** dá para saber da cliente.
2. No `orden-4008.json`, procure outro dado que, junto com os que ficaram sem mascarar, poderia ajudar a identificar a
   cliente. Valeria mascará-lo? O que se perderia para diagnosticar ao mascará-lo?
3. Alguém do suporte precisa telefonar para a cliente porque a entrega dela falhou. De onde deveria tirar o telefone: da
   trilha ou de outro lugar? Quem deveria poder vê-lo?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [A trilha é guardada mascarada](../decisiones/31-el-rastro-se-guarda-tapado.md)

## Revisão

1. Que dados do 4008 são pessoais, e quais não são?
2. Por que se mascara na trilha e não no que vai para o destino?
3. O que faz o operador de transformação de JSONata, `~> | ruta | { … } |`?
4. Por que o CP e o domínio do e-mail ficam à vista?
5. **A pergunta de encerramento:** o `verifica` procura os dados na tabela `orden` antes de contar zeros na trilha. Para
   que serve isso, e o que aconteceria com o teste se não o fizesse?

**A seguir:** na lição 28 a integração deixa de falar só por HTTP e arquivos: os eventos do ciclo de vida do pedido
viajam por um tópico, e cada integração os lê com o próprio grupo.
