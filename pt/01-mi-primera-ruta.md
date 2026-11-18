# Lição 01 — Minha primeira rota roda

> **O que será alcançado:** rodar uma rota de Camel, prever o que ela faz antes de ver e dar nome às três
> peças dela. É a única lição em que não se escreve nada do zero: a rota já vem pronta.

**Antes de começar é preciso ter:** a lição 00 concluída e o Docker funcionando. Nada mais: não se instala Java nem Camel.

**O que é o Camel, em uma frase:** um programa que move informação de um sistema para outro seguindo as
instruções que alguém escreve para ele. Essas instruções se chamam **rota**. Hoje a rota move um arquivo de uma pasta
para outra, que é o mais simples que existe. Nas próximas lições, a mesma ideia vai mover pedidos de uma
loja entre sistemas que não se conhecem.

⚠️ **Trabalhe dentro da pasta pessoal** (`~`, por exemplo `~/practicas/`), nunca em `/tmp`. No Mac com colima,
o Docker só consegue enxergar as pastas que estão dentro da home. Se o repositório foi clonado em outro lugar, a rota falha com
«Source file does not exist».

---

## 1. Prepare a pasta

Todo o trabalho próprio vai em `mio/`. É uma cópia de `inicio/` e o git não a acompanha, então ali se pode quebrar o que quiser.

```bash
cd lecciones/01-mi-primera-ruta   # entra a la carpeta de la lección
cp -R inicio mio                  # copia inicio/ completa (-R: con todo lo de adentro) y la llama mio/
cd mio                            # entra a tu copia
```

## 2. Leia a rota e faça a previsão

Abra `primera-ruta.camel.yaml` com o editor. Está escrita em **YAML**, um formato de texto em que a indentação
(os espaços no início de cada linha) indica o que vai dentro de quê, como as pastas dentro de pastas. Sem os
comentários, é isto:

```yaml
- route:
    id: primera-ruta
    from:
      uri: file:entrada
      parameters:
        include: ".*\\.json"
      steps:
        - log:
            message: "Llegó la orden del archivo ${header.CamelFileName}: ${body}"
        - to:
            uri: file:salida
```

**Antes de rodá-la, escreva as respostas em um papel** (não há respostas erradas; o que importa é compará-las
depois):

1. Se um arquivo `orden-1001.json` for colocado na pasta `entrada/`, o que deve aparecer na tela?
2. Onde o arquivo vai terminar?
3. O que vai ficar em `entrada/` quando a rota terminar com ele?

## 3. Rode a rota

Coloque um pedido em `entrada/` e inicie a rota:

```bash
mkdir -p entrada                                     # crea la carpeta entrada/ (si ya existe, no pasa nada)
cp ../../../datos/ordenes/orden-1001.json entrada/   # copia la orden de ejemplo; ../ sube una carpeta
../../../herramientas/camel run primera-ruta.camel.yaml
```

O pedido é um arquivo **JSON**: texto com pares de nome e valor (`"orden": "1001"`). Abra-o antes de rodar:
é o que vai viajar.

Na primeira vez demora um pouco, porque o Docker baixa a imagem do Camel. Quando aparecer uma linha com
`Started primera-ruta`, a rota já está escutando. Alguns segundos depois aparece o anúncio do pedido. Para
pará-la, pressione `Ctrl+C`.

## 4. Compare com a previsão

Veja as três respostas e confira as pastas:

```bash
ls salida/       # lista lo que hay en salida/
ls -a entrada/   # -a muestra también lo escondido (lo que empieza con punto)
```

- **A tela:** o log diz `Llegó la orden del archivo orden-1001.json:` e depois o pedido completo. `${header.CamelFileName}`
  foi trocado pelo nome do arquivo e `${body}` pelo conteúdo dele.
- **O arquivo:** está em `salida/`, idêntico a como entrou.
- **`entrada/`:** parece vazia, mas `ls -a` mostra uma pasta escondida, `.camel/`, e dentro dela está o pedido.
  **O Camel não apaga o que lê:** separa para não processar de novo. Isso estava na previsão?

Se alguma resposta não coincidiu, anote por que isso deve ter acontecido. Essa anotação vale mais do que ter acertado.

## 5. Dê nome ao que apareceu

A rota tem três peças. Nomeie-as com uma imagem e depois com o nome formal:

| O que aparece no YAML | A imagem | O nome nos padrões de integração (EIP) |
|---|---|---|
| A rota inteira: de `from` a `to`, passando por `log` | **A tubulação** | *Pipes and Filters* |
| `file:entrada` e `file:salida`, por onde se entra e se sai | **As estações** | *Message Endpoint* |
| O pedido que viaja, com o nome de arquivo e o conteúdo dele | **A carta** | *Message* |

Os EIP (*Enterprise Integration Patterns*) são nomes que qualquer equipe de integração do mundo reconhece. Não
é preciso memorizá-los hoje: em cada lição aparece um novo, bem na hora em que é usado.

**Exercício:** no `mio/primera-ruta.camel.yaml`, escreva acima de cada parte um comentário com o nome dela
(`# estación`, `# tubería`, `# carta`). Na carta há um detalhe: ela não aparece escrita no YAML. Por quê?

## 6. Mude a rota

Agora modifique a rota. Depois de cada mudança, coloque de novo o pedido em `entrada/` e rode outra vez:

1. **Mude a mensagem do log** para que diga `Recibí la orden ${header.CamelFileName}`. Antes de rodar: o que
   se espera ver?
2. **Mude a estação de saída** para `file:procesadas`. Que pasta nova se espera ver?
3. **Coloque um arquivo que não seja `.json`** em `entrada/`, por exemplo `nota.txt`. A rota o pega? Que linha do YAML
   decide isso?

## 7. Confira

A partir da pasta da lição, não de `mio/`:

```bash
cd ..
./verifica
```

⚠️ O `verifica` **esvazia e volta a encher** `mio/entrada/` e `mio/salida/` para testar do zero. Na rota ele não mexe.

Está concluída quando aparecem as cinco marcas verdes:

```
✅ La ruta arrancó
✅ El log anunció la orden
✅ La orden llegó a salida/
✅ entrada/ quedó vacía
✅ Salió idéntica a como entró
```

Se a saída foi trocada para `procesadas/` no passo 6, volte para `salida/` antes de conferir.

**Não roda?** Tente com a nossa: `./verifica solucion`. Se a nossa sai em verde e a própria não, compare as duas
com `diff mio/primera-ruta.camel.yaml solucion/primera-ruta.camel.yaml`. Usar a nossa não é trapaça: serve para que
um erro de hoje não atrapalhe as lições seguintes.

## 8. O mapa da viagem

**Não é preciso entender isto hoje.** O mapa do que será construído é o programa do
[README do curso](../README.md): segue a ordem em que o pedido viaja, de quando é criado até que não se perca. Cada
peça chega na própria lição; hoje bastam a rota, o endpoint e a mensagem.

*Por que Camel:* partimos de premissas, não de gostos. Buscamos algo leve, que escale por serviço, que se defina por
configuração e que tenha licença aberta. No fim do curso, quem o fez mede por conta própria o que se ganha e o que se cede. O
detalhe está em [Por que Camel](../conceptos/por-que-camel.md).

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Apache Camel como motor de integração](../decisiones/01-apache-camel-como-motor.md)
- [Rotas em YAML, na forma canônica](../decisiones/02-rutas-en-yaml-canonico.md)

## Revisão

Sem olhar o YAML, responda:

1. O que faz `from`, e o que faz `to`?
2. O que é a carta nesta rota, e por que não está escrita no YAML?
3. O que o Camel faz com um arquivo que já leu?
4. Para que a rota só pegasse arquivos `.xml`, que linha seria preciso mudar?
5. O `nota.txt` foi colocado em `entrada/`. A rota o pegou? Que parte do YAML decidiu isso?

**A seguir:** na lição 02, a estação de entrada deixa de ser uma pasta: o pedido chega por HTTP e sai para
uma API.
