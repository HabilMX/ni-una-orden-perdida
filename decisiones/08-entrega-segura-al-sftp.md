# Cuatro cuidados al dejar un archivo al socio

> **Dónde aparece:** lección 07.

## El problema

Si el socio lee la carpeta mientras escribimos, se lleva un archivo a medias.

## Qué decidimos

El 850 se deposita en el SFTP del socio (SFTP: el envío de archivos cifrado sobre SSH) con cuatro cuidados:
1. se escribe con **nombre temporal** (`.tmp`) y se renombra al terminar;
2. el **nombre final es determinístico** (`850-<orden>.edi`);
3. **no se sobrescribe** uno que ya está: si existe, falla;
4. solo se conecta si el servidor presenta **la llave anotada en `known_hosts`** (la lista de servidores en los que confías, cada uno con su huella).

## Por qué

- El socio lee su carpeta cuando quiere: sin el temporal podría leer un archivo a medias.
- La misma orden siempre da el mismo nombre, así un reenvío choca en vez de duplicarse en silencio.
- Pisar un archivo que el socio quizá ya procesó borra evidencia; que falle y alguien decida.
- Sin revisar la llave, cualquiera que se haga pasar por el servidor recibiría los documentos.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Escribir directo con el nombre final | El socio puede leer un archivo incompleto |
| Nombre con fecha y hora | Cada reenvío crea un archivo nuevo y el socio recibe la orden dos veces |
| strictHostKeyChecking apagado | Es lo más rápido para que «funcione», y deja la puerta abierta a un impostor |

## Qué nos haría reabrirla

- Que el socio pida otro mecanismo (AS2, que es otro protocolo de envío de documentos entre empresas, o una API): los cuidados cambian de forma, pero no de intención.

## Fuentes

- Componente SFTP de Camel (tempFileName, fileExist, knownHostsFile): https://camel.apache.org/components/next/sftp-component.html
