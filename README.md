# JettraKanban

JettraKanban es un cliente JavaFX con estilo futurista para gestionar un tablero Kanban/Scrum sincronizado con GitHub Projects (v2).

## Funcionalidades

- Sincroniza tarjetas desde GitHub Project.
- Conecta directamente con un Project v2 usando su URL de GitHub.
- Crea tarjetas nuevas como Draft Issues dentro del Project.
- Edita tarjetas (titulo y descripcion).
- Mueve tarjetas entre columnas (drag and drop o selector de columna).
- Mapeo automatico de columnas Kanban con el campo `Status` del Project.
- Puede guardar username, password y token cifrados en `credentials.md` si activas el boton `Recordar credenciales`.
- Incluye el boton `Olvidar credenciales` para borrar `credentials.md` y limpiar los campos guardados, con confirmacion previa.

## Requisitos

- JDK 21+
- Maven 3.9+
- Credenciales GitHub: username+password o token con permisos sobre Projects e Issues

## Configuracion

Puedes configurar variables de entorno:

```bash
export GITHUB_USERNAME=tu_usuario
export GITHUB_PASSWORD=tu_password
export GITHUB_TOKEN=ghp_xxx
export GITHUB_PROJECT_URL=https://github.com/users/tu_usuario/projects/1
```

Si defines `GITHUB_TOKEN`, JettraKanban prioriza token (Bearer).
Si no hay token, usa `GITHUB_USERNAME` + `GITHUB_PASSWORD` (Basic).

O crear un archivo `config.properties` en la raiz del proyecto con:

```properties
github.token=ghp_xxx
github.username=tu_usuario
github.password=tu_password
github.projectUrl=https://github.com/users/tu_usuario/projects/1
```

## Ejecutar

```bash
mvn clean javafx:run
```

## Flujo de uso

1. Ingresa URL del proyecto GitHub v2.
2. Ingresa credenciales (token o username+password).
3. Activa `Recordar credenciales` si quieres persistirlas cifradas en `credentials.md`.
4. Pulsa **Conectar**.
5. El tablero queda sincronizado con el proyecto indicado en la URL.

Si quieres borrar lo almacenado, pulsa `Olvidar credenciales`.

## Notas de GitHub Projects

- El Project debe ser **Projects v2**.
- Debe existir el campo single-select llamado **Status**.
- Formatos de URL soportados:
  - `https://github.com/users/<usuario>/projects/<numero>`
  - `https://github.com/orgs/<organizacion>/projects/<numero>`
- Las opciones de Status recomendadas para mejor mapeo:
  - Backlog
  - To Do
  - In Progress
  - Review
  - Done

JettraKanban intenta mapear automaticamente variantes comunes de nombres de estado.

## Error de scopes del token

Si al conectar aparece este error:

GraphQL error your token has not been granted the required scopes to execute this query

usa un token con permisos suficientes:

- Personal access token (classic): scope project y, para repos privados, repo.
- Fine-grained token: Projects (Read and write) e Issues (Read and write).

Despues actualiza el token en la app y vuelve a pulsar Conectar.
