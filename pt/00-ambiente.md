# Lição 00 — Ambiente pronto

> **O que será alcançado:** subir com um único comando os quatro sistemas com os quais se vai integrar durante todo
> o curso, e conferir que cada um responde. Ainda não se programa nada: prepara-se a oficina.

## A história em uma frase

A tarefa é conectar uma **loja online** aos sistemas de que ela precisa para vender: uma **API de pedidos**, o **ERP**
da empresa e um **parceiro comercial** que recebe arquivos. Esses sistemas já existem e são de outros: o trabalho é
fazer a informação viajar entre eles sem se perder. Nesta lição eles são ligados.

## Antes de começar é preciso ter

| O quê | Para quê | Como conferir que está disponível |
|---|---|---|
| Um terminal | Escrever os comandos | No Mac, o app *Terminal*; no Windows, o *Ubuntu* dentro do WSL; no Linux, o que vier instalado |
| **git** | Baixar o repositório do curso | `git --version` responde com um número |
| **Docker** com Compose | Rodar cada sistema no próprio contêiner, sem instalá-lo na máquina | `docker compose version` responde com um número |
| Memória livre para o Docker | Os quatro sistemas e o Camel rodam ao mesmo tempo | `docker compose up --wait` termina sem que a máquina trave |

Não se instala Java, nem Maven, nem Camel, nem banco de dados algum. **Tudo roda dentro do Docker.**

Se alguma destas palavras for nova (terminal, git, contêiner), leia primeiro as fichas de
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/). São curtas.

⚠️ **Trabalhe dentro da pasta pessoal** (`~`), nunca em `/tmp`. No Mac com colima, o Docker só enxerga as pastas
da home.

---

## 1. Baixe o curso

```bash
cd ~                                  # ve a tu carpeta personal
git clone https://github.com/HabilMX/ni-una-orden-perdida.git   # baja una copia a tu máquina
cd ni-una-orden-perdida               # entra a la carpeta que se creó
```

## 2. Faça a previsão

Abra `infra/compose.yaml` com o editor. É a lista dos sistemas que serão ligados. Sem rodar nada, responda
em um papel:

1. Quantos serviços há, e como se chama cada um?
2. Que número de porta cada um usa na máquina? *(Dica: procure `ports:`. O número da esquerda é o da
   máquina; o da direita, o de dentro do contêiner.)*
3. Qual deve demorar mais para iniciar na primeira vez, e por quê?

## 3. Suba o ambiente

A partir da pasta raiz do curso:

```bash
docker compose -f infra/compose.yaml up -d --wait
```

- `-f infra/compose.yaml` diz ao Docker qual lista de serviços usar.
- `up` os inicia.
- `-d` os deixa rodando em segundo plano, e assim o terminal fica livre de novo.
- `--wait` espera até que todos digam que estão saudáveis.

**Na primeira vez demora vários minutos**, principalmente por causa da API de pedidos: o Docker a constrói a partir do código antes
de iniciá-la. Sabe-se que terminou quando `--wait` devolve o terminal e `docker compose ps` diz `healthy` em
cada sistema. Nas vezes seguintes é muito mais rápido.

## 4. Compare com a previsão

```bash
docker compose -f infra/compose.yaml ps
```

Devem aparecer quatro serviços, todos em `healthy` (saudável):

| Serviço | O que é na história | Na máquina | O que é, tecnicamente |
|---|---|---|---|
| `api-pedidos` | O sistema de pedidos da empresa, com o qual se fala pela internet | `localhost:8081` | Uma API REST feita em Quarkus. Guarda em memória: ao ser reiniciada, começa vazia |
| `erp` | O **ERP**: o sistema central onde a empresa registra o que vende | `localhost:13306` | Uma base MariaDB com duas tabelas (cabeçalho e detalhe) e um **procedimento armazenado** (`registrar_pedido`): um programa guardado dentro da base |
| `socio-sftp` | A caixa postal do **parceiro comercial**, onde se deixam arquivos para ele | `localhost:2222` | Um servidor **SFTP**, para copiar arquivos de forma segura, com as pastas `entrada/` e `salida/` |
| `estado` | A caderneta onde a integração anota como vai cada pedido | `localhost:15432` | Uma base PostgreSQL com três tabelas. Só se mexe nela a partir da lição 15 |

Deu para acertar qual demorava mais?

## 5. Confira

```bash
cd lecciones/00-ambiente
./verifica
```

Está concluída quando aparecem as nove marcas verdes:

```
✅ estado está arriba
✅ erp está arriba
✅ socio-sftp está arriba
✅ api-pedidos está arriba
✅ La base de estado tiene sus tres tablas
✅ El ERP tiene su procedimiento registrar_pedido
✅ El ERP rechaza lo que no cuadra
✅ El SFTP del socio tiene entrada/ y salida/
✅ La API de pedidos contesta y exige su llave (400 sin ella)
```

Repare na sétima. O `verifica` pede ao ERP que registre um pedido que não existe, e **espera que o ERP se recuse**. A nona faz o mesmo com a API: manda um pedido sem
a chave e espera que ela o rejeite.
Os sistemas deste curso não dizem sim a tudo, assim como os de verdade: validam, rejeitam e às vezes respondem tarde.
Se um destino aceitasse qualquer coisa, a integração pareceria funcionar mesmo estando errada.

## 6. Desligue e ligue de novo

```bash
docker compose -f infra/compose.yaml down        # apaga y quita los contenedores
docker compose -f infra/compose.yaml up -d --wait
```

Ao desligar com `down`, apaga-se o que havia dentro: o ambiente volta limpo a cada vez. No início de cada lição
ele será ligado assim.

## Se algo falhar

| O que aparece | O que acontece | O que fazer |
|---|---|---|
| `port is already allocated` | Já há algo usando essa porta. Por isso o curso usa 15432 e 13306 em vez das de sempre (5432 e 3306), mas outro programa pode estar na 8081 ou na 2222 | Pare esse programa, ou pergunte no canal como trocar a porta |
| `Cannot connect to the Docker daemon` | O Docker não está rodando | Abra o Docker Desktop, ou no Mac `colima start` |
| Um serviço fica em `starting` | Falta memória ou ele ainda está sendo construído | Espere mais um minuto; se não mudar, `docker compose -f infra/compose.yaml logs <servicio>` |
| `Source file does not exist` | O repositório foi clonado fora da home (Mac com colima) | Clone-o dentro de `~` |

## Uma nota sobre as senhas

Em `infra/compose.yaml` as senhas estão escritas à vista (`practica`). Aqui pode porque é um ambiente
de prática na própria máquina. **Em um sistema real isto não se faz nunca:** as senhas chegam por variáveis de
ambiente ou por um gerenciador de segredos, e não ficam guardadas no repositório.

---

## Por que foi feito assim

Cada decisão do curso tem a própria ficha: o que decidimos, por quê, o que descartamos e o que nos faria
mudar de ideia. Estão em [`decisiones/`](../decisiones/), e cada lição traz o link da ficha correspondente. Hoje ainda não há
nenhuma: o ambiente é o piso, não uma decisão de design.

## Revisão

1. O que cada uma das quatro peças faz na história da loja?
2. O que significam `up`, `-d` e `--wait`?
3. Por que é bom que o ERP rejeite um pedido que não existe?
4. Por que a base de estado usa a porta 15432 na máquina e não a 5432 de sempre? O que aconteceria se usasse a 5432
   e já houvesse um PostgreSQL instalado?
5. O que acontece com os dados do ERP quando o ambiente é desligado com `down` e ligado de novo? Por que
   isso convém em um curso prático?

**A seguir:** na lição 01 roda-se a primeira rota de Camel. Ela ainda não usa estes sistemas: move um arquivo de
uma pasta para outra.
