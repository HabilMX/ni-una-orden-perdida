# Kubernetes de práctica con kind, con metrics-server y versiones fijas, y un clúster que se borra al terminar

> **Dónde aparece:** lección 31 (apéndice opcional).

## El problema

La lección 31 tiene que mostrar un HPA que sube y baja copias con la carga. Para eso hace falta un Kubernetes de
verdad: Compose no tiene un escalado automático por CPU. Pero el alumno trabaja en su laptop, sin una nube y sin
pagar nada, y en la misma máquina pueden vivir otros clústeres y las lecciones de Compose. Además, un HPA por CPU no
funciona solo: necesita que alguien mida la CPU de cada Pod, y un clúster recién creado no trae quién lo haga.

## Qué decidimos

- **kind** («Kubernetes in Docker»): el clúster es un contenedor de Docker que hace de nodo. Un solo nodo, que es
  jefe y trabajador a la vez (`cluster/kind.yaml`).
- **Versiones fijas:** kind v0.33.0 con la imagen de nodo `kindest/node:v1.37.0` fijada por su huella (sha256), la
  que publica esa versión de kind. metrics-server v0.9.1, tal como lo publica su proyecto.
- **metrics-server con un solo ajuste para kind:** `--kubelet-insecure-tls`, aplicado con kustomize
  (`cluster/metrics-server/kustomization.yaml`) sobre el manifiesto oficial, sin copiarlo al repo. En kind, el
  certificado del kubelet no lo firma una autoridad que metrics-server conozca.
- **Un clúster con nombre propio, `ni-una-orden-perdida`, y un kubeconfig propio** en una carpeta temporal. El
  `verifica` no toca el `~/.kube/config` del alumno ni el contexto de otros clústeres, y llama a `kubectl` siempre con
  `--context kind-ni-una-orden-perdida`.
- **El `verifica` crea el clúster y lo borra al salir**, también si algo falla o si el alumno lo interrumpe (con
  `trap`). Si quedó uno con ese nombre de una corrida anterior, lo borra antes de empezar: es de la práctica.
- **Las imágenes se suben al nodo con `kind load docker-image`** y los Pods usan `imagePullPolicy: Never`: no hace
  falta un registro de imágenes.
- **Nada del clúster depende del ambiente de Compose:** la API de pedidos corre DENTRO del clúster, con el mismo
  nombre (`api-pedidos`), y el cliente que manda la carga también. Así la 31 no choca con las lecciones 00 a 30.

## Por qué

- kind corre donde ya corre la práctica (Docker), en macOS, Linux y Windows con WSL, sin una máquina virtual más.
  Es el que usa el propio proyecto de Kubernetes para probarse.
- Fijar las versiones hace que todos corran el mismo Kubernetes y el mismo metrics-server: si una cifra o un
  comportamiento cambia, es por el cambio que hiciste tú, no porque bajó otra versión.
- metrics-server es la pieza que el HPA consulta para la CPU (la API `metrics.k8s.io`). Sin ella, el HPA dice
  `<unknown>` y nunca escala.
- Un clúster de práctica que se queda prendido ocupa memoria y disco aunque no lo uses (lecciones/31-…/CAPACIDAD.md).
  Borrarlo al salir deja la máquina como estaba.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| minikube | Funciona igual de bien, pero en macOS suele pedir su propia máquina virtual o un controlador más; kind usa el Docker que ya tienes |
| k3d (k3s en Docker) | Es otra distribución de Kubernetes, con piezas propias; kind corre el Kubernetes del proyecto tal cual |
| Un clúster en la nube | Pide cuenta y tarjeta, y cuesta mientras está prendido |
| Copiar el manifiesto de metrics-server al repo | Serían ~200 líneas ajenas que mantener; kustomize toma el oficial de su versión y le aplica el ajuste |
| Usar el contexto de kubectl que el alumno tenga activo | Podría ser otro clúster, de otro proyecto: un `apply` ahí haría daño |
| Reusar la API de Compose (lección 00) desde el clúster | Ataría la 31 a la red de Compose y a las lecciones que corren ahí al mismo tiempo |

## Qué nos haría reabrirla

- Que kind deje de publicar imágenes de nodo para la versión de Kubernetes que fijamos: se sube a la siguiente.
- Que el alumno ya tenga un clúster de su escuela o su empresa: la lección se puede correr ahí, cambiando el contexto,
  y sin `kind load` (las imágenes irían a un registro).
- Que metrics-server cambie la forma de confiar en el kubelet en kind.

## Fuentes

- kind, inicio rápido (crear, cargar imágenes, borrar): https://kind.sigs.k8s.io/docs/user/quick-start/
- kind, configuración del clúster: https://kind.sigs.k8s.io/docs/user/configuration/
- kind v0.33.0 y sus imágenes de nodo: https://github.com/kubernetes-sigs/kind/releases/tag/v0.33.0
- metrics-server (y `--kubelet-insecure-tls`): https://github.com/kubernetes-sigs/metrics-server
- Kubernetes, la tubería de métricas de recursos: https://kubernetes.io/docs/tasks/debug/debug-cluster/resource-metrics-pipeline/
- Kubernetes, kustomize: https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/
