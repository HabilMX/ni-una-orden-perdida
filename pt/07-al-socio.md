# Lição 07 — Ao parceiro

> **O que será alcançado:** deixar o 850 da lição 06 na caixa postal do parceiro comercial com quatro cuidados: que ele não pegue um
> arquivo pela metade, que o mesmo pedido tenha sempre o mesmo nome, que um segundo envio não apague o primeiro e que a rota
> só se conecte ao servidor cuja chave está em `known_hosts`.

**Antes de começar é preciso ter:** a lição 06 concluída e o ambiente da 00 ligado:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## A novidade de hoje, em uma frase

O parceiro não tem uma API: tem uma **caixa postal SFTP**, uma pasta no servidor dele para a qual se sobem arquivos e da
qual ele os lê **quando quer**. Não avisa quando lê nem responde. Por isso o que for deixado ali tem de estar
completo e não pode atrapalhar o que já havia.

**SFTP, o mínimo:** um programa para copiar arquivos para outro computador pela rede de forma segura. Pede um usuário
e uma senha (ou uma chave), e antes de se conectar confere que o servidor é quem diz ser. Essa conferência
é o passo 2.

---

## 1. Prepare a pasta

```bash
cd lecciones/07-al-socio
cp -R inicio mio
cd mio
```

A rota usa o mapeamento e o modelo do 850 da lição 06, que já vêm dentro de `mio/`.

## 2. Confie no parceiro

Na primeira vez que se fala com um servidor, é preciso saber que é **o do parceiro** e não outro que se faz passar por
ele. Cada servidor tem uma **chave pública**, como uma impressão digital. Ela é anotada uma vez em um arquivo chamado
`known_hosts`, e a partir daí a rota só se conecta se o servidor apresentar essa mesma chave.

```bash
../../../herramientas/confiar-en-socio
```

Na vida real, o parceiro manda a chave dele por outro canal (um e-mail, um portal) e ela é anotada à mão. Aqui o
script a pega direto do contêiner do parceiro, que faz as vezes do administrador dele.

⚠️ Se o ambiente for desligado com `down` e ligado de novo, o servidor do parceiro estreia chave: rode o
script outra vez. O `known_hosts` é de quem faz a lição e o git não o acompanha.

## 3. Leia a rota

Abra `orden-al-socio.camel.yaml`. É a rota da lição 06 com outra estação de saída:

```yaml
uri: sftp://socio@socio-sftp:22/entrada
```

| Parte | O que é |
|---|---|
| `socio@` | O usuário com que se entra na caixa postal |
| `socio-sftp:22` | O servidor do parceiro e a porta dele |
| `/entrada` | A pasta onde o parceiro recolhe o que lhe mandam |

Abaixo vêm os parâmetros dela:

| Parâmetro | O que faz |
|---|---|
| `knownHostsFile: known_hosts` e `strictHostKeyChecking: "yes"` | Só se conecta se a chave do servidor for a que foi anotada no passo 2 |
| `fileName: "850-${variable.orden}.edi"` | O nome final. O pedido 1001 sempre se chama `850-1001.edi` |

Esse nome se chama **determinístico**: depende só do pedido. Se levasse a hora ou um número ao acaso,
o mesmo pedido enviado duas vezes deixaria dois arquivos, e o parceiro atenderia duas vezes.

## 4. Faça a previsão

Responda em um papel:

1. Se o pedido 1001 for enviado duas vezes, quantos arquivos se esperam na caixa postal do parceiro?
2. Se na segunda vez o pedido trouxer **outra quantidade** (5 xícaras em vez de 2), o que o arquivo tem no fim?
3. Se alguém trocar o servidor do parceiro por outro com outra chave, o que a rota deve fazer?

## 5. Rode a rota duas vezes

Coloque o pedido 1001 em `entrada/` e rode a rota. `--max-messages=1` a faz parar sozinha depois de um pedido:

```bash
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
```

Pergunte à caixa postal do parceiro o que ela tem:

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp ls /home/socio/entrada
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Agora mande o **mesmo pedido com outra quantidade**, `orden-1001-cambiada.json`, e olhe a caixa postal de novo:

```bash
cp ../../../datos/ordenes/orden-1001-cambiada.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Procure o primeiro `PO1`. Diz `PO1*1*5*…`: **o segundo envio apagou o primeiro sem avisar**. Há um único arquivo, como
na previsão da pergunta 1, mas já não é o pedido que o parceiro tinha. Se ele já o havia lido, agora tem duas
versões diferentes do pedido 1001 e não sabe qual vale.

## 6. Acrescente os dois cuidados que faltam

Na rota há um comentário **TE TOCA** («a parte de quem faz a lição»). Ali vão mais dois parâmetros da estação `sftp:`:

**Nome temporário.** Subir um arquivo leva tempo. Se o parceiro lê a pasta bem no meio, leva um arquivo
cortado. A solução é escrevê-lo com outro nome e trocá-lo no fim, o que é instantâneo:

```yaml
tempFileName: "${file:onlyname.noext}.tmp"
```

Enquanto é escrito ele se chama `850-1001.tmp`; quando termina, é renomeado para `850-1001.edi`. Se o parceiro só recolhe
arquivos `.edi`, é muito difícil que pegue um incompleto: a renomeação é a última coisa que acontece. Não é uma garantia
total (um parceiro que recolhe qualquer arquivo, ou uma pasta compartilhada mal configurada, a quebram), e por isso na
lição 21 a confirmação de que chegou bem quem dá é o 997, não o arquivo.

**Não sobrescrever.** Por padrão, se o arquivo já existe, o Camel o substitui. O que se quer é o contrário: que falhe e alguém
decida.

```yaml
fileExist: Fail
```

Cuidado com a indentação: os dois vão no mesmo nível de `fileName`.

Esvazie a caixa postal e repita o passo 5:

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp rm /home/socio/entrada/850-1001.edi
```

Agora o segundo envio não passa. No terminal da rota aparece:

```
File already exist: entrada/850-1001.edi. Cannot write new file.
```

E o pedido alterado **ficou na pasta `entrada/`**, sem ser separado em `.camel/` como os que saem bem. Não se
perdeu: está esperando que alguém decida o que fazer com ele. Isso se resolve na lição 18.

## 7. Troque a chave

Agora a pergunta 3. Faça um backup do `known_hosts`, coloque uma chave falsa e mande um pedido:

```bash
cp known_hosts known_hosts.bueno
echo "socio-sftp ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHVuYWxsYXZlZGVvdHJvc2Vydmlkb3JxdWVub2Vz" > known_hosts
cp ../../../datos/ordenes/orden-1003.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1 --max-seconds=40
```

Entre os erros vai aparecer:

```
HostKey has been changed: socio-sftp
```

A rota **se recusou a conectar**: o servidor apresentou uma chave diferente da anotada. Pode ser que o parceiro a tenha
trocado (e teria de avisar por outro canal), ou que alguém esteja no meio fazendo-se passar por ele. Nos dois
casos, o correto é não mandar nada. Devolva a chave boa:

```bash
mv known_hosts.bueno known_hosts
```

## 8. Confira

```bash
cd ..
./verifica
```

⚠️ O `verifica` **esvazia a caixa postal do parceiro** antes de testar.

Está concluída quando aparecem as sete marcas verdes:

```
✅ Llegó 850-1001.edi al SFTP del socio
✅ No quedó ningún archivo .tmp
✅ Lo que recibió el socio es el 850 correcto
✅ Un segundo envío de la 1001 no pisó el archivo que ya estaba
✅ La ruta escribe con nombre temporal
✅ La ruta intentó conectar y el servidor no presentó la llave anotada
✅ Con otra llave en known_hosts, la ruta se negó a conectar
```

A quinta é diferente das demais: o `.tmp` vive alguns milissegundos e não há como vê-lo de fora, então o
`verifica` o confere na rota. A sexta procura no log o `HostKey has been changed` do passo 7: demonstra que a
rota de fato tentou se conectar e que quem a barrou foi a chave, não outra falha. As demais quem confirma é a caixa postal do parceiro.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-al-socio.camel.yaml solucion/orden-al-socio.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `PISÓ el archivo del socio` | Falta `fileExist: Fail` |
| `no usa nombre temporal` | Falta `tempFileName` |
| `HostKey has been changed` em todas as execuções | O ambiente foi recriado: rode de novo `confiar-en-socio` (passo 2) |
| `No pude hablar con el SFTP del socio` | Ligue o ambiente: `docker compose -f infra/compose.yaml up -d --wait` |

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Quatro cuidados ao deixar um arquivo para o parceiro](../decisiones/08-entrega-segura-al-sftp.md)

## Revisão

1. Por que o arquivo é escrito com um nome temporário e renomeado no fim?
2. O que faz o nome `850-1001.edi` ser determinístico, e o que aconteceria se levasse a hora?
3. Sem `fileExist: Fail`, o que aconteceu com o primeiro envio? Por que é pior do que um erro?
4. Para que serve o `known_hosts`, e o que a rota faz se o servidor apresenta outra chave?
5. O pedido que não pôde ser enviado ficou em `entrada/`. Por que isso é melhor do que apagá-lo?

**A seguir:** os três destinos já estão prontos: a API, o ERP e o parceiro. Na lição 08 eles são reunidos: o pedido entra
por um **router** que o distribui entre serviços que se chamam pelo nome.
