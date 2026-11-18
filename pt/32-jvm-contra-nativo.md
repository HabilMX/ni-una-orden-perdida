# Lição 32 — JVM contra nativo, medido

> **Apêndice opcional.** Pode ser **lida** sem ser rodada.
>
> **O que será alcançado:** construir o mesmo int-rest de duas formas, para a JVM e como executável nativo, e medir na
> própria máquina o que muda: quanto cada um demora para ser construído, para ficar pronto para atender e quanta memória usa. E
> medir **bem**: que cada número diga o que diz que mede.

**Antes de começar, confira a máquina.** Compilar para nativo é o mais pesado de todo o curso. Na máquina em que
medimos (Mac com Apple M5, Docker com 6 CPU e 16 GiB), o compilador chegou a usar 4,6 GB de memória e 4,6 núcleos, e
a construção nativa demorou 202 segundos a partir do zero. O detalhe está em
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).

**É preciso ter também:** a lição 31 lida (esta usa o `Dockerfile` dela) e, para quem não conhece o modo nativo,
[`conceptos/antes-de-empezar/07-nativo.md`](../conceptos/antes-de-empezar/07-nativo.md). Não é preciso o kind nem o
ambiente da 00: a lição traz o próprio `compose.yaml` com a API, os dois int-rest e um cliente de carga.

## A novidade de hoje, em uma frase

Até hoje, cada serviço do curso rodava sobre a JVM. O Camel Quarkus também pode ser compilado **para nativo**: um
executável que inicia sem JVM. Fala-se muito de quanto mais rápido ele inicia e de quanta memória economiza. Hoje não se acredita:
**mede-se**, com um método fixo, na própria máquina. A dificuldade nova não é o nativo: é **medir bem**.

**A imagem:** comparar dois carros. Se um é medido com o motor já quente e o outro a frio, ou em um se conta
desde que o motor liga e no outro desde que o motorista entra, o resultado não diz nada dos carros. Para
comparar, os dois vão pela mesma pista, com a mesma carga e o mesmo cronômetro.

| O que se mede | Com o quê | Por que assim |
|---|---|---|
| Construir | O relógio, com `--no-cache` | Para que nenhum reaproveite o da vez anterior |
| O que o Quarkus diz | A linha `started in …` do log dele | Para compará-la com a seguinte |
| Até saudável (`hasta sano`) | Desde que o contêiner é **pedido** até que `/q/health/ready` responde | É o que espera quem precisa que a instância do serviço atenda |
| Memória em repouso (`memoria en reposo`) | `docker stats`, já iniciado, sem trabalho | O que ocupa esperando |
| Memória com carga (`memoria con carga`) | `docker stats` a cada segundo **durante** uma carga de 100 pedidos por segundo, 20 s; a mais alta | O que ocupa trabalhando |
| Imagem | `docker image inspect` | O que pesa baixá-la |

Os dois contêineres têm o mesmo limite (1 CPU e 512 MiB) e são medidos **um por vez**, para que um não tire CPU
do outro.

---

## 1. Prepare a pasta

```bash
cd lecciones/32-jvm-contra-nativo
cp -R inicio mio
```

O trabalho é um script: `mio/medir`. Ele já constrói as duas imagens, sobe cada modo, lança a carga e escreve a
tabela em `mio/cifras.md`. Faltam as três funções que **medem**. Abra-o e leia de cima a baixo antes de escrever.

Abra também `compose.yaml`: os dois int-rest saem do mesmo `Dockerfile` da lição 31, com dois `target` diferentes
(`jvm` e `nativo`), e escutam em `8091` e `8092`.

## 2. Faça a previsão

Escreva em um papel, **antes** de medir:

1. Quantas vezes mais rápido o nativo deve iniciar? E quanta memória a menos deve usar em repouso?
2. Qual dos dois deve demorar mais para ser **construído**?
3. O Quarkus diz no log `started in 0.05s`. É isso que a instância do serviço demora para poder atender um pedido?

Veja como está hoje (constrói as duas imagens a partir do zero; na máquina de `CAPACIDAD.md` demorou cerca de 7 minutos):

```bash
./verifica inicio
```

As imagens são construídas, iniciam e entregam o pedido à API. Mas em `cifras.md` faltam `hasta sano`,
`memoria en reposo` e `memoria con carga`: as funções que medem ainda respondem `—`.

## 3. As três medidas

### TODO 1: a memória de um contêiner

O `docker stats` a dá assim: `145.8MiB / 512MiB`. Peça uma única leitura, só essa coluna, e fique com o primeiro
número, em MiB:

```bash
memoria() {
  docker stats --no-stream --format '{{.MemUsage}}' "$1" 2>/dev/null | awk '{
    v=$1; n=v+0
    if (v ~ /GiB/) n=n*1024; else if (v ~ /KiB/) n=n/1024
    printf "%.1f\n", n }'
}
```

`n=v+0` converte `145.8MiB` em `145.8`: o awk toma os dígitos do começo. O resto é trocar de unidade.

### TODO 2: até saudável

Tome a hora **antes** de pedir o contêiner e pergunte a cada 50 ms por `/q/health/ready`, no máximo 60 segundos:

```bash
hasta_sano() {
  local modo=$1 t0 p
  p=$(puerto "$modo"); t0=$(ms)
  C up -d --force-recreate "int-rest-$modo" >/dev/null 2>&1
  for _ in $(seq 1 1200); do
    curl -sf -o /dev/null --max-time 1 "localhost:$p/q/health/ready" && { echo $(( $(ms) - t0 )); return 0; }
    sleep 0.05
  done
  echo "—"
}
```

Por que não basta o `started in` do log? Porque isso o Quarkus diz de si mesmo: não conta o que
o contêiner demora para nascer. Quem espera que a instância do serviço atenda, espera as duas coisas.

### TODO 3: a memória com carga

A carga roda em segundo plano. **Enquanto** ela continua viva, tome a memória a cada segundo e fique com a mais alta:

```bash
while kill -0 "$carga" 2>/dev/null; do
  m=$(memoria "$cont"); awk -v a="$m" -v b="$tope" 'BEGIN{exit !(a+0 > b+0)}' && tope=$m
  sleep 1
done
```

`kill -0` não mata nada: só pergunta se o processo continua vivo. Vai antes do `wait`.

## 4. Quebre um de propósito

Com o `medir` concluído, mude o TODO 3: apague o `while` e, depois do `wait`, tome **uma** única leitura
(`tope=$(memoria "$cont")`). Antes de rodar, copie o `mio/cifras.md` para outro lugar. Rode `./verifica`.

Antes, faça a previsão: o `verifica` detecta isso?

Não: sai verde. `memoria con carga` continua sendo um número, e o `verifica` só pede que seja. Compare com a cópia
guardada: o número novo é a memória **depois** da carga, e pode sair mais baixo que o verdadeiro. **Uma medição malfeita
não falha: devolve um número e soa a fato.** Por isso o `verifica` não julga os números contra nenhum limiar
(ficha 39), e por isso o método é escrito junto da tabela. Devolva o `while`.

## 5. Confira

```bash
./verifica
```

O `verifica` roda o `mio/medir`, que constrói as duas imagens a partir do zero e mede cada uma. Depois manda um pedido a
cada imagem e confere no log da API que chegou, confere que a nativa não traz JVM e confere que `cifras.md`
foi escrito **nesta execução** com os seis números de cada modo. Está concluída quando aparecem as marcas verdes:

```
✅ La imagen jvm arrancó, contestó y su orden (…) llegó a la API
✅ La imagen nativo arrancó, contestó y su orden (…) llegó a la API
✅ Control: la imagen nativa no trae JVM; corre un ejecutable de (…) MB
✅ Cifras de jvm medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
✅ Cifras de nativo medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
```

Os números são **de quem mede**. Na máquina em que testamos, a JVM ficou saudável em cerca de 1.700 ms com cerca de 108 MiB em
repouso, e o nativo em cerca de 240 ms com cerca de 16 MiB. Em troca, o nativo demorou mais para ser construído. Em outra máquina serão
outros, e está tudo bem: compare com os próprios números, não com os nossos.

**Não roda?** `./verifica solucion` testa a nossa, e `diff mio/medir solucion/medir` mostra em que são
diferentes.

| O que aparece | O que fazer |
|---|---|
| `El puerto … está ocupado` | Desligue o que ficou rodando e tente de novo |
| `Tu mio/medir terminó con error` | Rode `./mio/medir` sozinho: ele diz em que passo parou |
| `No existe la imagen ni-una-orden-perdida/int-rest:nativo: no se construyó` | Leia `mio/build-nativo.log`. Se ficou sem memória, confira `CAPACIDAD.md`: o `Dockerfile` permite baixar `MEMORIA_NATIVO`, e demora mais |
| `La imagen … no entregó su orden … a la API` | As linhas que seguem são o log do int-rest: dizem por quê |
| `Tu medir no escribió mio/cifras.md` ou `no se escribió en esta corrida` | O `medir` parou antes de escrever a tabela: rode-o sozinho |
| `A las cifras de … les falta (o no es un número): hasta sano` | Falta o TODO 2, ou `/q/health/ready` nunca respondeu em 60 s |
| `… les falta …: memoria en reposo, memoria con carga` | Falta o TODO 1 (e com ele, o 3 também não mede) |

---

## O que isto ensina fora do curso

**Um número sem o método dele não se compara.** «Inicia em 50 ms» pode ser o que o programa diz de si mesmo ou o que
espera quem o usa. As duas coisas são verdadeiras e não são o mesmo número.

**O nativo troca um custo por outro.** Inicia rápido e usa pouca memória, e demora mais para ser construído e pede uma
máquina grande para compilar. Convém onde se iniciam muitas réplicas com frequência (como os picos da lição 31); em um
serviço que roda semanas sem reiniciar, a diferença de início quase não se nota.

**Os números publicados são de outra máquina.** Os de um artigo, os nossos e os de quem mede são de máquinas diferentes.
Servem para saber o que esperar; para decidir, mede-se na máquina onde vai rodar.

**O teste sair verde não prova que a medição foi bem feita.** O `verifica` pode conferir que há um número, não que o
número seja o correto. O método é o que o sustenta.

## Exercício: a mesma pista

1. Escreva em `mio/cifras.md`, embaixo da tabela, as previsões do passo 2 e em que estavam erradas.
2. Rode `./mio/medir` duas vezes seguidas. Quanto mudam `hasta sano` e `memoria con carga` entre uma execução e outra?
   O que isso diz sobre comparar uma única execução?
3. Em papel: a equipe tem um serviço que escala de 1 a 10 réplicas nos picos da manhã, e outro que roda
   sem reiniciar o mês inteiro. Com os números **próprios**, qual seria compilado para nativo e por quê?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O int-rest é exportado com camel export para Camel Quarkus, sem mudar a rota dele](../decisiones/36-int-rest-exportado-con-camel-export.md)
- [O nativo é compilado com Mandrel dentro de um contêiner](../decisiones/38-el-nativo-se-compila-en-un-contenedor.md)
- [JVM contra nativo se mede na própria máquina, com um método fixo e sem limiares](../decisiones/39-se-mide-en-tu-maquina-sin-umbrales.md)

## Revisão

1. O que é um executável nativo, e o que lhe falta que a JVM tem?
2. Por que `hasta sano` e «started in» são números diferentes?
3. Por que a memória com carga é lida a cada segundo durante a carga, e não uma vez no fim?
4. Por que os dois contêineres são medidos um por vez e com o mesmo limite?
5. **A pergunta de encerramento:** alguém diz «o nativo inicia 30 vezes mais rápido». O que perguntar antes de
   acreditar?

**Aqui termina o curso.** Para quem chegou até aqui, um pedido já atravessa a API, o ERP e o parceiro sem se perder, e
já se sabe onde olhar quando algo falha.
