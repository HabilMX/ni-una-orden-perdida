"""Convierte las expresiones compactas de las rutas YAML de Camel a la notación canónica, CONSERVANDO los comentarios.

    python3 herramientas/mantenimiento/a-canonica.py lecciones/NN-*/solucion/*.camel.yaml

    setVariable:                    setVariable:
      name: orden          →          name: orden
      jsonpath: "$.orden"             expression:
                                        jsonpath:
                                          expression: "$.orden"

Por qué no se usa «camel validate normalize» (plugin validate de Camel 4.22.1): produce la misma forma, pero
BORRA todos los comentarios (medido: 8 → 0 en la ruta de la 06), y en las rutas de la práctica los comentarios
son la explicación para el alumno. Es para quien mantiene el curso; el alumno no lo usa.
"""
import re, sys
pat = re.compile(r'^(\s*)(- )?(simple|jsonpath|constant):\s*(.+?)(\s+#.*)?$')
for p in sys.argv[1:]:
    out=[]
    for line in open(p, encoding='utf-8').read().split('\n'):
        m = pat.match(line)
        if m:
            ind, dash, lang, val, com = m.group(1), m.group(2) or '', m.group(3), m.group(4), m.group(5) or ''
            base = ind + dash
            pad = ' ' * len(base)
            out.append(f"{base}expression:{com}")
            out.append(f"{pad}  {lang}:")
            out.append(f"{pad}    expression: {val}")
        else:
            out.append(line)
    open(p,'w',encoding='utf-8').write('\n'.join(out))
    print(p)
