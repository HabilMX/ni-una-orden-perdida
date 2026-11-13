#!/bin/sh
# socio-bot: hace las veces del sistema del socio (lección 21). Lee lo que le dejamos en entrada/ y, al cabo de
# ACUSE_SEGUNDOS, deja en salida/ su acuse funcional: un 997 X12 004010.
#
#   entrada/850-4001.edi  ──(ACUSE_SEGUNDOS)──→  salida/997-<GS06 del 850>.edi
#
# El 997 cita lo que acusa por SUS números de control: AK1 lleva el GS01 y el GS06 del grupo, y AK2 el ST01 y el
# ST02 del documento. El ISA del 997 es del socio, con su propio número: no repite el nuestro.
#
# Acepta todo (AK5*A, AK9*A), salvo un documento que pida un producto que el socio no conoce (RECHAZA_SKU, en el
# PO1, elemento 7): ese lo rechaza (AK3 y AK4 señalan el segmento y el elemento, AK5*R*5, AK9*R).
# Solo acusa archivos terminados en .edi (los .tmp siguen escribiéndose), y cada uno una vez: recuerda el nombre y
# la suma (cksum) de lo que acusó, mientras el archivo siga en entrada/. Si alguien lo borra, lo olvida: un archivo
# nuevo con el mismo nombre y el mismo contenido (como el de una corrida anterior de un verifica) se vuelve a acusar.
set -u
DIR="${SOCIO_DIR:-/home/socio}"
ESPERA="${ACUSE_SEGUNDOS:-8}"
SKU_DESCONOCIDO="${RECHAZA_SKU:-SKU-9999}"
ESTADO=/var/bot
mkdir -p "$ESTADO"
[ -f "$ESTADO/contador" ] || echo 700000000 > "$ESTADO/contador"
rm -f "$ESTADO"/visto-*          # la forma vieja de recordar, de antes de olvidar lo borrado
mkdir -p "$ESTADO/visto"
echo "socio-bot: acuso lo que llegue a $DIR/entrada a los ${ESPERA} s; rechazo el producto $SKU_DESCONOCIDO"

acusar() {
  archivo="$1"
  n=$(( $(cat "$ESTADO/contador") + 1 )); echo "$n" > "$ESTADO/contador"
  ahora=$(date -u +%Y%m%d%H%M)
  tr -d '\r\n' < "$archivo" | awk -v RS='~' -F'*' -v ctl="$n" -v ahora="$ahora" -v sku="$SKU_DESCONOCIDO" '
    { pos++ }
    $1 == "GS" { gs01 = $2; gs06 = $7 }
    $1 == "ST" { st01 = $2; st02 = $3; pos = 1 }
    $1 == "PO1" && $8 == sku && malo == "" { malo = pos }
    END {
      if (gs06 == "" || st02 == "") { exit 1 }
      d = substr(ahora, 3, 6); h = substr(ahora, 9, 4); f = substr(ahora, 1, 8)
      printf "ISA*00*          *00*          *ZZ*SOCIO01        *ZZ*TIENDANORTE    *%s*%s*U*00401*%09d*0*P*>~\n", d, h, ctl
      printf "GS*FA*SOCIO01*TIENDANORTE*%s*%s*%d*X*004010~\n", f, h, ctl
      printf "ST*997*0001~\n"
      printf "AK1*%s*%s~\n", gs01, gs06
      printf "AK2*%s*%s~\n", st01, st02
      segs = 3   # ST, AK1, AK2
      if (malo != "") {
        printf "AK3*PO1*%d**8~\n", malo
        printf "AK4*7*234*7*%s~\n", sku
        printf "AK5*R*5~\n"
        printf "AK9*R*1*1*0~\n"
        segs = segs + 4
      } else {
        printf "AK5*A~\n"
        printf "AK9*A*1*1*1~\n"
        segs = segs + 2
      }
      printf "SE*%d*0001~\n", segs + 1
      printf "GE*1*%d~\n", ctl
      printf "IEA*1*%09d~\n", ctl
      print gs06 > "/dev/stderr"
    }' > "$ESTADO/997.tmp" 2> "$ESTADO/gs06" || { echo "socio-bot: $archivo no es un X12 que entienda; no lo acuso"; return; }
  gs06=$(cat "$ESTADO/gs06")
  destino="$DIR/salida/997-$gs06.edi"
  # Como en la 07: se escribe con otro nombre y se renombra, para que nadie lea un 997 a medias
  cp "$ESTADO/997.tmp" "$destino.tmp" && chown 1001 "$destino.tmp" 2>/dev/null; mv "$destino.tmp" "$destino"
  echo "socio-bot: acusé $(basename "$archivo") con $(basename "$destino") ($(grep -o 'AK5\*[AR]' "$destino"))"
}

while true; do
  for archivo in "$DIR"/entrada/*.edi; do
    [ -f "$archivo" ] || continue
    marca="$ESTADO/visto/$(basename "$archivo")"
    suma=$(cksum < "$archivo")
    [ -f "$marca" ] && [ "$(cat "$marca")" = "$suma" ] && continue
    edad=$(( $(date +%s) - $(stat -c %Y "$archivo") ))
    [ "$edad" -ge "$ESPERA" ] || continue
    acusar "$archivo"
    echo "$suma" > "$marca"
  done
  # Lo que ya no está en entrada/ se olvida
  for marca in "$ESTADO"/visto/*; do
    [ -f "$marca" ] || continue
    [ -f "$DIR/entrada/$(basename "$marca")" ] || rm -f "$marca"
  done
  sleep 2
done
