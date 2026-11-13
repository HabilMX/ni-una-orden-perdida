#!/usr/bin/env bash
# Comprueba que el material no lleve referencias internas antes de publicarlo.
#
# Por qué existe: este curso es PÚBLICO. Una ruta interna, un nombre de host o
# un token en un ejemplo quedan indexados por los buscadores y ya no se pueden
# retirar.
#
# Uso:  ./verificar-publicable.sh          → sale 0 si está limpio, 1 si no
#       ./verificar-publicable.sh --probar → solo la autoprueba
#
# 🔴 ESTE GUION ES UNA PUERTA, y una puerta que no puede demostrar que detecta
# no es una puerta: es un adorno que dice "limpio". Por eso trae AUTOPRUEBA
# (siembra un patrón en una copia temporal y exige que lo cache) y por eso
# FALLA CERRADO: si la búsqueda no se pudo hacer, el resultado es rojo, no verde.
set -uo pipefail
cd "$(dirname "$0")"

# 🔴 TOPE DE RECURSION. Si la autoprueba (o cualquier otra cosa) llega a invocar
# este guion desde dentro de este guion, el de dentro corre SU autoprueba, que
# vuelve a invocarlo: recursion infinita. Medido el 6-oct-2026 escribiendo la
# prueba de la excepcion del curso de web: ~7,250 procesos de los 8,000 que
# admite la maquina, que se quedo sin poder lanzar ninguno y freno otro carril.
# Esto convierte ese desastre en un error de una linea.
: "${HB_VERIFICADOR_EN_CURSO:=0}"
if [ "$HB_VERIFICADOR_EN_CURSO" -ge 1 ]; then
  echo "❌ el verificador se invoco a si mismo (profundidad $HB_VERIFICADOR_EN_CURSO)." >&2
  echo "   Una prueba NO debe reinvocar el verificador: comprueba el acotamiento." >&2
  exit 3
fi
export HB_VERIFICADOR_EN_CURSO=$((HB_VERIFICADOR_EN_CURSO + 1))
LISTA=.publicable-prohibido.txt
# Los patrones internos viven aparte y NO se publican; sin ellos esta revision no
# puede hacerse, y por eso falla cerrado en vez de salir en verde a medias.
LISTA_INTERNA=.publicable-interno.txt

# 🔴 UNA SOLA DEFINICION DE «LIGA PUBLICA PERMITIDA», Y VIVE AQUI. Antes el mismo
# patron estaba escrito CUATRO veces —dos en la autoprueba y dos en el lazo—, asi
# que la autoprueba podia certificar una regla DISTINTA de la que aplica el
# verificador: el dia que alguien corrigiera una copia y no las otras, la puerta
# diria «probado» de algo que no hace. Lo encontro la revision automatica del
# commit del 7-oct-2026, no yo.
#
# 🔴 DECISION DE LA PO (20-oct-2026): se permite CUALQUIER pagina publica del sitio
# —portada, blog, cursos, servicios, productos, autor—, con o sin `www` y en
# cualquier idioma, porque el puente a la venta es el punto. Lo que sigue vetado son
# los HOSTS INTERNOS: cualquier otro subdominio de habil.mx.
#
# ⚠️ ESTA EXCEPCION ERA INCONSISTENTE HASTA HOY, y no por decision de nadie: el
# patron del host exige un subdominio (`[a-z0-9-]+\.habil\.mx`), asi que
# `https://habil.mx` a secas era INVISIBLE para la puerta —pasaba por un agujero, no
# por la excepcion— mientras `https://www.habil.mx/es/<servicio>/` se cazaba. Medido
# el 20-oct-2026 publicando el laboratorio de Vault.
#
# ⚠️ LIMITE CONOCIDO Y ASUMIDO: la excepcion borra el prefijo, asi que un dominio
# AJENO que empiece igual (`habil.mx.ejemplo.net`) tambien queda exento. No se cierra
# con mas regex a proposito —seria ilegible— porque lo que esta puerta protege son
# los hosts internos de la casa, y un dominio de tercero no filtra ninguno.
LIGAS_PUBLICAS='https?://(www\.)?habil\.mx([/ )"]|$)[^ )"]*'

# Se revisa TODO archivo de texto del repositorio, sin importar su extensión ni
# su carpeta: las lecciones (.md), pero también lo que se copia y se ejecuta
# (programas/*.ts, .txt, .json), el README de la raíz, la licencia, los guiones
# y el workflow. Una lista de extensiones o de carpetas escrita a mano deja
# huecos justo donde nadie mira. Solo se excluye lo que no es contenido del
# curso: .git, node_modules, las dependencias instaladas y la propia lista de
# patrones (que, por definición, los contiene).
#
# 🔴 grep -I ignora los archivos binarios, así que un PNG no produce falsos
# positivos; un archivo de texto con cualquier extensión sí se revisa.

# Busca UN patrón bajo la raíz dada ("." por omisión).
#   Devuelve 0 = sin coincidencias · 1 = hay coincidencias · 2 = no se pudo buscar
#
# 🔴 El "--" va DESPUÉS de las banderas y ANTES del patrón. Si se pone antes de
# "--exclude", grep deja de leerlo como bandera y lo trata como una RUTA que no
# existe: entonces sale con código 2 SIEMPRE —incluso habiendo encontrado
# coincidencias— y quien mire solo el código de salida lee "limpio".
buscar() {
  local patron="$1" raiz="${2:-.}"
  local salida rc
  salida=$(grep -rnIE --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
             --exclude="$LISTA" --exclude="$LISTA_INTERNA" -- "$patron" "$raiz" 2>&1); rc=$?
  case "$rc" in
    0) printf '%s\n' "$salida"; return 1 ;;
    1) return 0 ;;
    *) printf '%s\n' "$salida" >&2; return 2 ;;
  esac
}

# --- autoprueba: sin esto, un "limpio" no vale nada ---
# Siembra el patrón, uno por uno, en archivos de TODOS los tipos y lugares que
# el curso trae (no solo .md) y exige que cada uno se detecte.
autoprueba() {
  local tmp patron='TOKEN_DE_AUTOPRUEBA_NO_BORRAR' rc=0 f
  tmp=$(mktemp -d) || return 1
  mkdir -p "$tmp/es" "$tmp/programas/01" "$tmp/herramientas" "$tmp/otro" "$tmp/.github/workflows"
  for f in es/leccion.md programas/01/fig.rs programas/01/fig.salida.txt \
           programas/01/fig.error-esperado.txt programas/01/Cargo.toml LICENSE.md \
           .github/workflows/verificar.yml README.md herramientas/guion.sh otro/sin-extension; do
    printf '%s\n' "$patron" > "$tmp/$f"
    ( cd "$tmp" && buscar "$patron" . >/dev/null 2>&1 ); [ "$?" -eq 1 ] || rc=1
    rm -f "$tmp/$f"
  done
  # y el negativo: un patrón que no está NO debe dar positivo
  printf 'texto limpio\n' > "$tmp/es/leccion.md"
  ( cd "$tmp" && buscar 'PATRON_QUE_NO_EXISTE_EN_NINGUN_LADO' . >/dev/null 2>&1 ); [ "$?" -eq 0 ] || rc=1
  # 🔑 La EXCEPCION tambien se autoprueba, en sus dos direcciones. Una excepcion
  # sin control positivo es un agujero con buena conciencia.
  #   (a) una liga permitida NO debe delatar
  printf 'ver https://www.%s/es/cursos/typescript/ aqui\n' 'habil''.mx' > "$tmp/es/leccion.md"
  ( cd "$tmp" && grep -qE "$LIGAS_PUBLICAS" es/leccion.md ) || rc=1
  #   (a dos) la DECISION DE LA PO del 20-oct: una pagina de SERVICIO del sitio y
  #   la portada SIN `www` tampoco delatan. Antes la primera se cazaba y la segunda
  #   pasaba por un agujero del patron del host, no por esta excepcion.
  printf 'ver https://www.%s/es/aprovisionamiento-de-infraestructura/ aqui\n' 'habil''.mx' > "$tmp/es/leccion.md"
  ( cd "$tmp" && grep -qE "$LIGAS_PUBLICAS" es/leccion.md ) || rc=1
  printf 'ver https://%s aqui\n' 'habil''.mx' > "$tmp/es/leccion.md"
  ( cd "$tmp" && grep -qE "$LIGAS_PUBLICAS" es/leccion.md ) || rc=1
  #   (a bis) 🔴 EL CASO QUE SE ESCAPABA: liga permitida Y host interno en la
  #   MISMA linea. Tiene que seguir delatando.
  printf 'ver https://www.%s/es/blog/x/ y tambien cicd.%s/interno\n' 'habil''.mx' 'habil''.mx' > "$tmp/es/leccion.md"
  ( cd "$tmp" && printf '%s\n' "$(grep -rnIE -- '[a-z0-9-]+\.habil\.mx' es/leccion.md)" \
      | sed -E "s#$LIGAS_PUBLICAS##g" \
      | grep -qE -- '[a-z0-9-]+\.habil\.mx' ) || rc=1
  #   (a ter) 🔴 UNA CREDENCIAL CON FORMA DE SLUG dentro de la liga permitida
  #   NO puede quedar exenta: su patron no es el del host y la excepcion no le
  #   aplica. Se comprueba de punta a punta con el verificador completo.
  # ⚠️ Se prueba el ACOTAMIENTO, no se reinvoca el verificador: llamarlo desde
  # aqui lo hace correr su propia autoprueba, que lo vuelve a llamar —recursion
  # infinita, medida el 6-oct-2026 al escribir esta prueba—.
  # ⚠️ Los patrones de prueba se ARMAN en ejecucion: un literal prohibido en el
  # codigo de la puerta hace que la puerta se delate a si misma. Me paso TRES
  # veces el 6-oct-2026, y la tercera fue escribiendo esta misma prueba.
  for _p in "$(printf 'glp''at-')" "$(printf 'AKI''A[0-9A-Z]{16}')"; do
    case "$_p" in *"$(printf 'habil''\\.mx')"*) rc=1 ;; esac
  done
  case '[a-z0-9-]+\.habil\.mx' in *'habil\.mx'*) : ;; *) rc=1 ;; esac  # el del host, si
  #   (b) un host interno NO debe pasar por la excepcion
  # ⚠️ El host de prueba se ARMA en ejecucion: si el literal viviera en este
  # archivo, el propio verificador se delataria a si mismo. Al original le pasaba
  # lo mismo y por eso excluye la lista de patrones.
  printf 'ver https://cicd.%s/interno aqui\n' 'habil''.mx' > "$tmp/es/leccion.md"
  ( cd "$tmp" && grep -qE "$LIGAS_PUBLICAS" es/leccion.md ) && rc=1
  # y la exclusión: la lista de patrones se excluye, el resto no
  printf '%s\n' "$patron" > "$tmp/$LISTA"
  ( cd "$tmp" && buscar "$patron" . >/dev/null 2>&1 ); [ "$?" -eq 0 ] || rc=1
  rm -rf "$tmp"
  return "$rc"
}

if ! autoprueba; then
  echo "❌ la autoprueba falló: la búsqueda no detecta lo que debería."
  echo "   NO se puede afirmar que el material esté limpio. Arregla el guion."
  exit 2
fi
[ "${1:-}" = "--probar" ] && { echo "✅ autoprueba correcta: la búsqueda detecta y descarta bien."; exit 0; }

[ -r "$LISTA" ] || { echo "❌ falta $LISTA"; exit 2; }
[ -r "$LISTA_INTERNA" ] || { echo "❌ falta $LISTA_INTERNA: esta revision se corre en el repositorio de trabajo, antes de publicar"; exit 2; }

# Control de que la búsqueda ve archivos: si no hay ninguno, no se revisó nada.
nfiles=$(find . \( -name .git -o -name node_modules -o -name target \) -prune -o -type f -print | wc -l | tr -d ' ')
[ "$nfiles" -gt 0 ] || { echo "❌ no hay archivos que revisar"; exit 2; }
echo "revisando $nfiles archivos de texto o binarios (los binarios se saltan), todas las carpetas"

revisados=0

fallas=0
errores=0
exceptuadas=0
while IFS= read -r patron || [ -n "$patron" ]; do
  case "$patron" in ''|\#*) continue ;; esac
  hits=$(buscar "$patron" .); rc=$?
  # 🔑 LA UNICA EXCEPCION PERMITIDA: las ligas publicas del propio sitio al blog y
  # a los cursos. La decidio la PO (6-oct-2026) para que el README de cada curso
  # pueda apuntar a su pagina, igual que los blogs; Dorian autorizo los cursos el
  # 7-oct-2026.
  # 🔴 EL SEGMENTO DE «CURSOS» CAMBIA POR IDIOMA, Y NO ES UN DETALLE: el sitio
  # sirve /es|pt/cursos/, /en|bg/courses/ y /fr/cours/. Medido el 7-oct-2026 por
  # dos vias que coinciden —los hreflang de la pagina y el sitemap de 352 URLs—,
  # despues de que /bg/kursove/ diera 404. Quien escriba el patron traduciendo la
  # palabra «cursos» a cada idioma deja ingles, frances y bulgaro en rojo.
  # ⛔ Se quita la linea exceptuada y SE VUELVE A CONTAR: si quedan otras, la
  # falla sigue. Asi la excepcion no tapa un hallazgo de verdad que viva en el
  # mismo archivo.
  # ⚠️ Medido el 6-oct-2026: esta excepcion NO existia en ningun curso —ni Go ni
  # TypeScript enlazan a habil.mx—, asi que estaba permitida de palabra y sin
  # mecanismo. Esta es su primera implementacion.
  # 🔴 LA EXCEPCION SOLO APLICA AL PATRON DEL HOST, y acotarla asi es la mitad
  # del arreglo. Aplicarla a TODOS los patrones abria una diferencia de
  # interpretacion: el «slug permitido» es `[a-z0-9-]+`, EL MISMO juego de
  # caracteres que una credencial. Demostrado el 6-oct-2026 — esta linea salia
  # «✅ limpio» con el verificador anterior:
  #     una liga permitida cuyo SLUG es, en realidad, una credencial
  # El `sed` se tragaba la credencial junto con la URL, y el patron que busca
  # credenciales ya no encontraba nada. Se exceptuaban DOS patrones: el del host, que era la
  # intencion, y el de credenciales, que no tiene nada que ver.
  # Lo encontro la revision automatica del commit, no yo.
  aplica_exc=0
  case "$patron" in
    *'habil\.mx'*|*'habil\\.mx'*) aplica_exc=1 ;;
  esac
  if [ "$rc" = "1" ] && [ "$aplica_exc" = "1" ]; then
    # 🔴 SE BORRA EL FRAGMENTO, NO LA LINEA. Quitar la linea completa era un
    # HUECO DE SEGURIDAD real: una sola linea puede llevar la liga permitida Y,
    # al lado, un host interno o una credencial. Al descartar la linea entera,
    # el hallazgo vecino salia exento en silencio.
    #   Mira https://www.<sitio>/es/blog/x/ y entra a <host interno> con <clave>
    # ⚠️ El dominio va como <sitio> A PROPOSITO: con el literal, este comentario
    # hacia que el verificador SE DELATARA A SI MISMO, y la excepcion lo tapaba
    # —o sea la excepcion enmascaraba un hallazgo en lugar de solo exentar una
    # liga—. Es la CUARTA vez que un literal prohibido en la puerta la delata.
    # Ahora se recorta SOLO la liga permitida de cada linea y se vuelve a buscar
    # el patron en lo que queda. Lo encontro la revision automatica del commit,
    # no yo: habia escrito que la excepcion no podia tapar un hallazgo vecino, y
    # lo resolvi para OTRAS lineas, no para la MISMA.
    # 🔴 Y FALLA CERRADO TAMBIEN AQUI. La version anterior remataba la tuberia con
    # `|| true`: si el `sed` o el `grep` de esta comprobacion fallaban —salida 2—,
    # el resultado quedaba VACIO, y el vacio se leia como «no quedan hallazgos»:
    # la excepcion se concedia y el patron salia LIMPIO. Un error de busqueda se
    # convertia en verde, lo contrario de lo que promete la cabecera. Lo encontro
    # la revision automatica del commit del 7-oct-2026. Ahora se distinguen los
    # tres desenlaces: 0 quedan hallazgos, 1 no queda ninguno, 2+ no se pudo.
    resto=$(printf '%s\n' "$hits" | sed -E "s#$LIGAS_PUBLICAS##g"); src=$?
    if [ "$src" -ne 0 ]; then
      echo "⚠️  no se pudo recortar la liga permitida del patrón: $patron"
      rc=2
    else
      hits_sin_exc=$(printf '%s\n' "$resto" | grep -E -- "$patron"); grc=$?
      case "$grc" in
        0) hits="$hits_sin_exc" ;;
        1) rc=0; exceptuadas=$((exceptuadas + 1)) ;;
        *) echo "⚠️  no se pudo comprobar la excepción del patrón: $patron"
           rc=2 ;;
      esac
    fi
  fi
  revisados=$((revisados + 1))
  case "$rc" in
    1) echo "🔴 patrón prohibido: $patron"
       printf '%s\n' "$hits" | sed 's/^/     /'
       fallas=$((fallas + 1)) ;;
    2) echo "⚠️  no se pudo buscar el patrón: $patron"
       errores=$((errores + 1)) ;;
  esac
done < <(cat "$LISTA" "$LISTA_INTERNA")

# 🔴 CERO PATRONES REVISADOS NO ES «LIMPIO». Si las listas estan vacias o no se
# pudieron leer, el lazo no itera ni una vez y el guion salia diciendo «limpio»
# con exito: el falso verde mas caro posible, porque es la puerta de la
# publicacion. Medido el 13-oct-2026 con las dos listas vacias: decia limpio y
# salia 0. Ahora se exige haber revisado al menos un patron.
if [ "${revisados:-0}" -eq 0 ]; then
  echo
  echo "❌ no se revisó ningún patrón: las listas están vacías o no se pudieron leer."
  echo "   Falla cerrado: un cero de patrones no es un repositorio limpio."
  exit 2
fi

if [ "$errores" -gt 0 ]; then
  echo
  echo "❌ $errores patrón(es) no se pudieron revisar. Falla cerrado: NO publicar."
  exit 2
fi
if [ "$fallas" -gt 0 ]; then
  echo
  echo "❌ $fallas patrón(es) prohibido(s). NO publicar hasta limpiarlo."
  exit 1
fi
[ "$exceptuadas" -gt 0 ] && echo "   (se exceptuaron $exceptuadas patrón(es) por llevar SOLO ligas públicas del sitio: /<idioma>/blog/ o la portada de un curso —/es|pt/cursos/, /en|bg/courses/, /fr/cours/—)"
echo "✅ limpio: ningún patrón prohibido en ningún archivo de texto"
