# Lição 12 — Crescer em réplicas

> **O que será alcançado:** rodar **três réplicas** do int-bd sem mexer nos demais serviços, medir como os
> pedidos se distribuem entre elas, e descobrir por que no começo eles não se distribuem.

**Antes de começar é preciso ter:** a lição 11 concluída e o ambiente da 00 ligado (com o ERP recriado na
10).

## A novidade de hoje, em uma frase

Chega o Buen Fin, o grande fim de semana de ofertas no México, e a loja manda o triplo de pedidos. O gargalo é o int-bd, que escreve no ERP.
Hoje **escala-se um único serviço**: em vez de uma réplica do int-bd rodam três, e o router continua chamando-as pelo
mesmo nome, `int-bd`.

**A imagem:** uma agência de banco que abre mais dois guichês. Os clientes continuam entrando na mesma fila,
e o banco coloca alguém para mandá-los a um guichê livre. Hoje se vê o que acontece quando esse alguém não faz
bem o trabalho.

Uma **réplica** é uma cópia idêntica do mesmo serviço: a mesma imagem, a mesma pasta, as mesmas rotas.
O int-bd pode ser replicado sem que um pedido seja registrado duas vezes porque **não guarda nada em memória**: tudo o que importa fica no ERP.

---

## 1. Prepare a pasta

```bash
cd lecciones/12-crecer-en-replicas
cp -R inicio mio
```

Hoje **não se mexe em nenhuma rota**. Todo o trabalho vai em um único arquivo, `mio/replicas.yaml`. O Compose o junta ao
`compose.yaml` da lição, no qual não se mexe: o Compose os funde campo por campo: um
`command` do segundo arquivo substitui o do primeiro, e em `environment` cada variável é acrescentada ou
substitui a de mesmo nome.

```bash
DONDE=mio docker compose -f compose.yaml -f mio/replicas.yaml up -d
```

É a mesma ideia do `franquicias.yaml` da 11: mudar como um serviço roda sem mudar o serviço.

## 2. Faça a previsão

Hoje se usa um lote novo: `datos/ordenes/lote-30/`, o lote de 30 pedidos, todos de lojas próprias (o parceiro não
recebe nenhum). Responda em um papel:

1. Se rodarem três réplicas do int-bd e os 30 pedidos forem mandados, quantos cada réplica registra?
2. Quem decide para qual réplica vai cada pedido: o router, o Camel, o Docker ou o ERP?

Confira como está hoje:

```bash
./verifica inicio
```

Saem duas marcas vermelhas: `int-bd corre en 1 copia(s) y se esperaban 3`, e `Con una sola copia no hay nada que
repartir`. O verifica imprime a distribuição, e hoje ela é `int-bd-1=30`: uma única réplica faz tudo.

## 3. Peça três réplicas

Em `mio/replicas.yaml`, o TODO 1. Peça três réplicas do int-bd com `deploy` → `replicas`:

```yaml
services:
  int-bd:
    deploy:
      replicas: 3
```

Rode o verifica:

```bash
./verifica
```

Veja a linha `Reparto:`. O que vimos ao testá-la:

```
   Reparto: practica-retail-l12-int-bd-1=0 practica-retail-l12-int-bd-2=0 practica-retail-l12-int-bd-3=30
❌ 2 de 3 copias no registraron nada: el router sigue usando la misma conexión.
```

As três réplicas estão no ar, mas **uma faz tudo e duas não fazem nada**. Deu para acertar o 10/10/10? Quase ninguém
acerta, porque parece óbvio. Antes de continuar, **escreva a explicação**: por que eles não se distribuíram?

## 4. Por que não se distribuem

Há duas razões, e elas andam juntas.

**A conexão é reutilizada.** Abrir uma conexão HTTP custa tempo, então o router abre **uma** para o `int-bd` e a
deixa aberta (*keep-alive*). Todos os pedidos seguintes viajam por essa mesma conexão, e uma conexão vai para uma única
réplica. O Docker não distribui requisições: distribui **conexões**.

**A JVM se lembra do endereço.** Quando o router pergunta «onde está o `int-bd`?», o DNS do Docker responde os
três endereços, em ordem diferente a cada vez. Mas a JVM do router guarda a primeira resposta e não volta a
perguntar por um bom tempo.

| | O que faz hoje | O que é preciso |
|---|---|---|
| A conexão | Vive para sempre | Que vença logo, para abrir outra |
| O endereço do `int-bd` | A JVM se lembra dele | Que ela o esqueça logo, para perguntar de novo |

Com as duas curtas, cada conexão nova pergunta de novo o endereço e pode cair em outra réplica.

## 5. Faça o router distribuir

O TODO 2, no mesmo `mio/replicas.yaml`, dentro de `orden-router`. São duas coisas:

**a) Que a conexão viva no máximo um segundo.** É a opção `connectionTimeToLive` do componente `http`, em
milissegundos. Ela é passada ao router na inicialização:

```yaml
    command: ["run", ".", "--prop=camel.component.http.connectionTimeToLive=1000"]
```

**b) Que a JVM se lembre do endereço por um segundo.** Vai em uma variável de ambiente que a JVM lê ao iniciar:

```yaml
    environment:
      JDK_JAVA_OPTIONS: "-Dsun.net.inetaddr.ttl=1"
```

Antes de colocar a b, faça o teste: **coloque só a a** e rode `./verifica`. Ao testar a lição, só com a a a
distribuição continuou em `0/0/30`. A conexão já vencia, mas a conexão nova ia para o mesmo endereço que a JVM tinha
guardado. Agora acrescente a b e rode de novo.

## 6. Confira

```bash
./verifica
```

O `verifica` reinicia a API, esvazia o ERP, sobe os serviços com o `replicas.yaml` de `mio/` e manda os 30 pedidos com uma
pausa entre cada um, para que o router tenha ocasião de abrir conexões novas. Está concluída quando aparecem as seis marcas verdes:

```
✅ int-bd corre en 3 copias
✅ Arrancaron el router, int-rest, int-edi y las copias de int-bd
✅ ERP: tiene las 30 órdenes REGISTRADO, cada una una sola vez
✅ API: tiene las 30 órdenes (crecer int-bd no tocó a los demás)
   Reparto: practica-retail-l12-int-bd-1=12 practica-retail-l12-int-bd-2=12 practica-retail-l12-int-bd-3=6
✅ Entre todas las copias registraron 30 órdenes: ni una de más, ni una de menos
✅ (…) de 3 copias de int-bd registraron órdenes: el trabajo se repartió
```

**A distribuição vai sair diferente**, e muda a cada execução: ao testá-la vimos 12/12/6 e 6/11/13. Quem escolhe a réplica
é a rede, e a rede não distribui de forma exata. Por isso o verifica não pede 10/10/10: pede que **pelo menos duas réplicas recebam
trabalho** e que **nenhum pedido se perca nem se repita**. Pode acontecer de uma réplica ficar sem pedidos em uma
execução: se as outras duas dividiram os 30 entre si, está certo. Cada réplica conta o que fez no próprio log, de modo que a
distribuição é dita pelas réplicas, não pelo router.

**Não roda?** `./verifica solucion` testa a nossa, e `diff mio/replicas.yaml solucion/replicas.yaml` diz
em que elas são diferentes.

| O que aparece | O que fazer |
|---|---|
| `int-bd corre en 1 copia(s)` | Falta o `deploy` → `replicas: 3`, ou ele ficou embaixo de outro serviço. Confira a indentação |
| `2 de 3 copias no registraron nada` | Falta a a, a b ou as duas (passo 5) |
| `No pude levantar los servicios. Revisa que mio/replicas.yaml sea YAML válido` | Um erro de indentação ou de aspas; o verifica mostra as últimas linhas do erro |
| `CAMEL_COMPONENT_HTTP_CONNECTION_TIME_TO_LIVE` foi colocada em `environment` e não distribui | Nesta versão essa variável não surte efeito (medimos: 0/29/0). Use o `--prop` do passo 5 |
| `Tu ERP no tiene la columna correlacion` | Recrie o ERP como diz a 10 |

---

## O que isto ensina fora do curso

**Distribui-se por conexão, e uma conexão eterna não se distribui.** No Docker quem distribui é o DNS. No Kubernetes, um
Service também escolhe uma réplica a cada conexão nova, de modo que o mesmo problema aparece igual. Muda quem
distribui; a regra não.

**O segundo é para ver o efeito aqui**, não um número para um sistema real. Quanto uma conexão deve viver depende
de quanto tráfego há e de com que frequência as réplicas mudam. Fechá-la depois de cada pedido também distribui, mas
paga-se o custo de abrir uma conexão a cada chamada.

## Exercício: crescer sem mexer em ninguém

Com a versão própria concluída, responda só com o que aparece na tela:

1. Troque `replicas: 3` por `replicas: 5` e rode `./verifica`. Que marca fica vermelha, e por quê? (Dica: o
   verifica espera 3.) Quantos pedidos cada uma das cinco registrou?
2. Volte para 3. Em que arquivos foi preciso mexer para passar de uma réplica a três? Foi preciso mexer em alguma rota, em algum mapeamento ou no
   `compose.yaml` da lição?
3. Por que o int-bd pode ser replicado três vezes sem que um pedido seja registrado duas vezes? O que aconteceria se cada réplica
   guardasse na própria memória os pedidos que já viu?

---

## O que já funciona

Com esta lição fecha-se a parte 1. Um pedido que a loja cria já faz tudo isto:

- chega ao router por HTTP e o router responde o que cada destino disse;
- chega à API uma única vez, mesmo que seja reenviado, graças à chave dele;
- entra no ERP completo ou não entra, e o int-bd pode escalar para várias réplicas;
- vira um 850 que chega ao parceiro sem ser sobrescrito nem ficar pela metade, só se a loja for dele;
- leva um fio que o segue por todos os serviços;
- e se um destino cair, os demais o recebem do mesmo jeito.

O que ele ainda não faz: lembrar em que ponto está cada pedido. Isso começa na parte 2.

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Três réplicas não distribuem sozinhas: a distribuição é por conexão](../decisiones/15-repartir-por-conexion.md)
- [Uma única imagem; cada serviço é uma pasta](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)

## Revisão

1. O que é uma réplica, e por que o int-bd pode ser replicado sem que um pedido seja registrado duas vezes?
2. Com `replicas: 3` e mais nada, como os pedidos se distribuíram? Por quê?
3. O que faz `connectionTimeToLive`, e o que faz `sun.net.inetaddr.ttl`? Por que só uma não basta?
4. Por que o verifica não pede 10/10/10, e o que pede no lugar?
5. **A pergunta de encerramento:** se o router tivesse réplicas, onde viveria o que diz respeito a cada pedido? Escreva a resposta:
   ela será comparada com a lição 15.

**A seguir:** na lição 13 chega o embarque. O armazém deixa um arquivo, e agora que
já se sabe rodar réplicas, a pergunta é outra: se várias réplicas leem a mesma pasta, quem pega o arquivo?
