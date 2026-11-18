# Lição 31 — De laboratório a projeto, no Kubernetes

> **Apêndice opcional.** O curso terminou na lição 30. Esta lição e a 32 são para quem trabalha com Kubernetes ou
> quer ver até onde chega o que foi feito. Podem ser **lidas** sem ser rodadas.
>
> **O que será alcançado:** levar o int-rest, o mesmo da lição 12, da pasta do laboratório para uma imagem de Camel
> Quarkus, rodá-lo em um Kubernetes de prática e fazer com que **as réplicas dele subam sozinhas** quando chegam muitos pedidos,
> e desçam quando acabam.

**Antes de começar, confira a máquina.** Medimos em um Mac com Apple M5 e Docker com 6 CPU e 16 GiB: a lição 31 pediu
cerca de 1,4 GiB de memória além do que já se usava, 2,7 núcleos no pico e cerca de 4,4 GB de imagens. A 32 pede
bem mais. O detalhe, e como foi medido, está em
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).
São números de uma única máquina: em outra podem ser diferentes.

**É preciso ter também:** a lição 12 concluída, e dois programas que até hoje não foram usados:

| Programa | Para quê | Onde se instala |
|---|---|---|
| `kind` | Um Kubernetes de prática que roda dentro do Docker | [kind.sigs.k8s.io, «Installation»](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) |
| `kubectl` | Falar com esse Kubernetes | [kubernetes.io, «Install Tools»](https://kubernetes.io/docs/tasks/tools/) |

Esta lição **não** usa o ambiente da 00: cria o próprio cluster, com o nome `ni-una-orden-perdida`, e o apaga
ao terminar, dê certo ou errado. Para quem não conhece o Kubernetes, vale ler antes
[`conceptos/antes-de-empezar/08-kubernetes.md`](../conceptos/antes-de-empezar/08-kubernetes.md).

## A novidade de hoje, em uma frase

Na lição 12 pôs-se `replicas: 3` à mão: três réplicas sempre, haja muito trabalho ou nenhum. Hoje essa decisão fica com o
Kubernetes, com um **HPA** (*HorizontalPodAutoscaler*): uma peça que olha quanta CPU as réplicas do int-rest usam e
põe mais quando se aproximam da meta, ou tira quando sobram. Para que ele possa medir, cada réplica tem de **reservar**
CPU: o HPA mede em porcentagem do que foi reservado.

**A imagem:** os caixas de um supermercado. Com pouca gente há um aberto. O gerente olha as filas e, quando cada
operadora de caixa atende mais do que lhe cabe, abre outro, até os quatro que tem. Quando a loja esvazia, vai
fechando-os. Mas para saber se uma operadora «está a 150%», primeiro é preciso dizer quanto cabe a cada uma. Isso é o que
se reserva.

---

## 1. Prepare a pasta

```bash
cd lecciones/31-de-laboratorio-a-kubernetes
cp -R inicio mio
```

Antes de escrever, leia o que já está feito:

| Arquivo | O que é | É para escrever? |
|---|---|---|
| `int-rest/int-rest.camel.yaml` | A rota do int-rest da lição 12, **sem mudar uma letra**, com os mapeamentos e os casos de teste de referência dela | Não |
| `Dockerfile` | Da pasta a uma imagem, em etapas: `camel export` a transforma em um projeto de Camel Quarkus, compila-se e fica uma imagem para a JVM (e outra nativa, para a lição 32). Tudo dentro de contêineres: não se instala Java nem Maven | Não, só leia |
| `cluster/` | O cluster do kind, o metrics-server (o que mede a CPU de cada réplica), a API de pedidos e um cliente que manda a carga | Não |
| `mio/k8s/int-rest.yaml` | O Deployment (as réplicas) e o Service (o nome fixo que distribui entre elas) | **Sim, TODO 1 e 2** |
| `mio/k8s/hpa.yaml` | O HPA | **Sim, TODO 3 e 4** |

Repare em duas coisas do Dockerfile e do Deployment. A configuração não vai em um `application.properties`: o que se
decide ao compilar vai no `pom.xml` e o que se decide ao rodar vai em variáveis de ambiente do Deployment
(`QUARKUS_SHUTDOWN_TIMEOUT`). E a imagem não é baixada de nenhum registro: `kind load` a sobe para o nó, por isso está escrito
`imagePullPolicy: Never`.

## 2. Faça a previsão

Responda em um papel:

1. Se uma réplica do int-rest reserva 200 milicores de CPU (a quinta parte de um núcleo) e está usando 300, a que
   porcentagem está? Com uma meta de 50%, quantas réplicas o HPA iria querer?
2. Se a réplica **não** reserva CPU, contra o que o HPA calcula a porcentagem?
3. A carga dura dois minutos e depois acaba. Quando o HPA deve tirar as réplicas a mais?

Veja como está hoje (demora alguns minutos na primeira vez: baixa imagens e constrói):

```bash
./verifica inicio
```

As primeiras marcas saem verdes: o cluster é criado, o int-rest é construído com `camel export` e o pedido 1001 chega
à API do cluster. Depois sai em vermelho a da CPU reservada, e o Kubernetes **rejeita** o HPA: falta o máximo de
réplicas.

## 3. Reserva, limite e meta

### TODO 1 e 2: o que cada réplica reserva, e o limite dela

Em `mio/k8s/int-rest.yaml`, dentro do contêiner, acrescente `resources`:

```yaml
resources:
  requests:            # lo que la copia RESERVA en el nodo
    cpu: 200m
    memory: 256Mi
  limits:              # el techo
    cpu: "1"
    memory: 512Mi
```

| | O que significa | Se passar |
|---|---|---|
| `requests` | O Kubernetes só põe a réplica em um nó que tenha isso livre, e o HPA mede contra isso | — |
| `limits.cpu` | Mais de um núcleo não lhe é dado | É estrangulada (throttling) |
| `limits.memory` | Mais de 512 MiB não lhe é dado | O Kubernetes reinicia a réplica |

`200m` lê-se «200 milicores»: um núcleo são `1000m`. Com `200m` reservados, uma réplica que usa `100m` está a 50%.

### TODO 3 e 4: o HPA

Em `mio/k8s/hpa.yaml`, embaixo de `scaleTargetRef`:

```yaml
minReplicas: 1
maxReplicas: 4
metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 50
```

A cada 15 segundos (o período que o Kubernetes traz por padrão; pode ser configurado), o HPA faz esta conta, e a
mantém entre o mínimo e o máximo:

```
copias que quiere = copias que hay × (CPU promedio ÷ meta), redondeado hacia arriba
ejemplo: 1 copia al 150 % con meta de 50 % → quiere 3
```

O máximo não é um detalhe: é o que impede que uma carga grande devore o nó inteiro. E o `behavior.scaleDown`
que o arquivo já traz faz com que desça aos 30 segundos sem carga, para que dê para ver no curso. Por padrão espera
5 minutos, para não subir e descer a toda hora.

## 4. Quebre um de propósito

Com os arquivos concluídos, tire `cpu: 200m` de `requests` (deixe a memória) e rode `./verifica`.

Antes, faça a previsão: o HPA está bem escrito e o Kubernetes o aceita. Ele escala?

Não. O `verifica` diz que o int-rest não reserva CPU e depois que o HPA não consegue lê-la: perguntando à mão
(`kubectl get hpa`), a meta sai como `<unknown>/50%`. O HPA não está quebrado: falta contra o que medir. **Uma
porcentagem sem base não é um número.** Devolva a linha.

## 5. Confira

```bash
./verifica
```

O `verifica` cria o cluster, constrói o int-rest, implanta-o com os arquivos **próprios**, manda o pedido 1001 como controle e
depois uma carga de 150 pedidos por segundo durante dois minutos. Conta as réplicas **prontas** do Deployment (não o que
a rota diz), confere que o Service distribuiu entre elas e que a API respondeu a todos os pedidos. Depois espera que
desçam. Na máquina de `CAPACIDAD.md` demorou cerca de 4 minutos; em outra, o tempo será outro. Está concluída quando aparecem as marcas verdes:

```
✅ Clúster kind «ni-una-orden-perdida» creado (Kubernetes …)
✅ int-rest salió de camel export a una imagen de Camel Quarkus, y está en el clúster junto con la API
✅ Kubernetes aceptó mio/k8s/int-rest.yaml (el Deployment y el Service)
✅ Control: la orden 1001 pasó por int-rest y la API la registró una vez (la ruta no cambió ni una letra)
✅ Cada copia de int-rest reserva CPU: requests.cpu=200m
✅ Kubernetes aceptó mio/k8s/hpa.yaml
✅ El HPA escala a int-rest entre 1 y 4 copias, con meta de CPU de 50 %
✅ El HPA ya lee la CPU de int-rest: (…) % de lo reservado, sin carga
✅ Con carga, el HPA subió int-rest de 1 a (…) copias listas (máximo 4)
✅ (…) copias de int-rest atendieron órdenes de la carga: el Service repartió
✅ Las (…) órdenes de la carga llegaron a la API y cada una tuvo respuesta: ninguna se perdió mientras crecía
✅ Sin carga, int-rest volvió a 1 copia(s) en (…) s
```

Nas nossas execuções o HPA subiu para 4 réplicas em uma e para 3 em outra; por isso o `verifica` pede «mais que o mínimo» e não
um número exato. Quantas ele sobe depende da máquina.

Enquanto roda, em outro terminal dá para ver o HPA trabalhar. O `verifica` guarda a chave do cluster em um arquivo
temporário, para não mexer na configuração do `kubectl` da máquina; ela é pedida ao kind:

```bash
kubectl --kubeconfig <(kind get kubeconfig --name ni-una-orden-perdida) get hpa,deploy -w
```

**Não roda?** `./verifica solucion` testa a nossa, e `diff -r mio solucion` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `No encuentro «kind»` ou `«kubectl»` | Instale-o com o link da tabela de «Antes de começar» |
| `kind no pudo crear el clúster` | Confira que o Docker esteja no ar e tenha memória livre (ver `CAPACIDAD.md`) |
| `No se pudo construir int-rest` | A primeira construção baixa dependências: confira a conexão. As linhas que seguem dizem onde parou |
| `Kubernetes rechazó mio/k8s/int-rest.yaml` | É um erro de YAML ou de nomes: a mensagem do Kubernetes sai embaixo |
| `int-rest no reserva CPU (resources.requests.cpu)` | Falta o TODO 1, ou `resources` ficou fora do contêiner |
| `Kubernetes rechazó mio/k8s/hpa.yaml (TODO 3 y 4)` | Falta `maxReplicas` ou `metrics` está mal indentado |
| `El HPA debe escalar a int-rest … con un máximo mayor que el mínimo` | Confira `minReplicas`, `maxReplicas` e que a meta seja `type: Utilization` |
| `El HPA no logra leer la CPU de int-rest` | Quase sempre, falta `requests.cpu`. O Kubernetes diz por quê na mesma mensagem |
| `Con carga, int-rest se quedó en 1 copia(s)` | A meta não é uma porcentagem do que foi reservado, ou é tão alta que nunca é atingida |
| `Sin carga, int-rest sigue en … copias` | O `behavior.scaleDown` foi alterado: devolva-o como vinha |

---

## O que isto ensina fora do curso

**A mesma rota, outro lugar.** O int-rest passou da pasta do laboratório para uma imagem de Camel Quarkus no
Kubernetes sem mudar o YAML dele. O que mudou é como ele é empacotado e quem decide quantas réplicas há.

**Escalar sozinho precisa de uma medida e de uma base.** O HPA não sabe nada de pedidos: sabe de CPU contra o que foi reservado. Se
a reserva está errada, a decisão de escalar também está.

**O máximo protege o vizinho.** Sem `maxReplicas`, uma carga grande põe réplicas até que o nó não aguente. E
vale lembrar a lição 24: se o int-bd escalasse sozinho, o teto em direção ao ERP teria de ser pensado com o **máximo** de réplicas, não com
as de hoje.

**Escalar sem perder se projeta, e se testa.** Nesta execução, todos os pedidos da carga tiveram resposta
enquanto apareciam réplicas novas: o Service não manda trabalho novo para uma réplica que ainda não diz que está pronta.
Ao tirar uma réplica, o Kubernetes lhe dá uma janela de tolerância e o Quarkus tenta terminar o que trazia, mas o `verifica`
não testa esse instante com pedidos no ar. Para ter segurança de que nada é cortado ao descer, é preciso testá-lo com
carga, com os tempos reais de cada serviço.

## Exercício: os caixas do supermercado

1. Em papel: com `requests.cpu: 200m`, meta de 50% e máximo 4, quantas réplicas o HPA quer se 2 réplicas estão a
   180%? E se estão a 20%?
2. Mude a meta para 80% e rode `./verifica`. Subiu para menos réplicas? Quanto demorou para descer?
3. Se amanhã fosse preciso escalar o int-bd assim, o que teria de ser recalculado da lição 24, e com que número: o mínimo
   ou o máximo de réplicas?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Kubernetes de prática com kind, com versões fixas, e um cluster que é apagado ao terminar](../decisiones/35-kubernetes-de-practica-con-kind.md)
- [Para o Kubernetes vai o int-rest, exportado com camel export para Camel Quarkus, sem mudar a rota dele](../decisiones/36-int-rest-exportado-con-camel-export.md)
- [O HPA escala por CPU, medida como porcentagem do que cada réplica reserva](../decisiones/37-el-hpa-escala-por-cpu-reservada.md)

## Revisão

1. O que faz um HPA, e o que ele mede?
2. Que diferença há entre `requests` e `limits`?
3. Para que serve `maxReplicas`?
4. Por que o `verifica` conta as réplicas prontas do Deployment e não o que a rota diz?
5. **A pergunta de encerramento:** o HPA diz `<unknown>/50%`. Explique por quê, e o que falta ao Deployment.

**A seguir:** na lição 32 o mesmo int-rest é compilado também para nativo, e mede-se na própria máquina o que muda:
quanto demora para ser construído, para iniciar e quanta memória usa.
