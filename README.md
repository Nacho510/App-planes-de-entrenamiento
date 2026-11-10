# Ritmo · App de entrenamientos

Prototipo web para que un entrenador planifique y un atleta registre su entrenamiento. Esta primera entrega incluye inicio de sesión, una relación entrenador–atleta configurada localmente, vista semanal, creación de sesiones con pasos anidados y registro manual de resultados por paso. Usa Java 21, Spring Boot, PostgreSQL, React y Axios.

> Es una versión preliminar para desarrollo local. Todavía no está lista para publicarse ni para manejar datos de atletas reales: revisa las limitaciones de seguridad y operación en [docs/arquitectura.md](docs/arquitectura.md).

## Requisitos

- JDK 21 y Maven 3.9 o superior.
- Node.js 20.19+ o 22.12+ y npm.
- PostgreSQL en ejecución, con acceso desde pgAdmin o psql.

### 1. Preparar PostgreSQL

En pgAdmin, conectado como administrador, abre el **Query Tool** y ejecuta estos comandos. Reemplaza la contraseña de ejemplo por una propia:

```sql
CREATE USER entrenos_app WITH PASSWORD 'CambiaEstaClavePorUnaSegura';
CREATE DATABASE entrenos OWNER entrenos_app;
```

Ejecuta `CREATE DATABASE` fuera de una transacción. Las tablas se crean al iniciar el backend. Este esquema inicial sirve para desarrollo, pero no actualiza esquemas ya existentes: antes de evolucionarlo se incorporarán migraciones versionadas.

### 2. Iniciar el backend

Abre PowerShell en `C:\Users\ignab\IdeaProjects\plan_entrenamientos\backend` y ejecuta, sustituyendo las tres contraseñas por valores propios:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/entrenos'
$env:DB_USER = 'entrenos_app'
$env:DB_PASSWORD = 'CambiaEstaClavePorUnaSegura'
$env:DEMO_COACH_PASSWORD = 'ClaveLargaEntrenador123!'
$env:DEMO_ATHLETE_PASSWORD = 'ClaveLargaAtleta456!'
mvn spring-boot:run
```

Las cuentas de demostración son opcionales: puedes omitir ambas variables `DEMO_COACH_PASSWORD` y `DEMO_ATHLETE_PASSWORD` y crear una cuenta desde el formulario de acceso. Si configuras las dos, sus contraseñas deben ser distintas y tener al menos 12 caracteres. Se crean solo si no existen; cambiar esas variables después **no cambia** la contraseña guardada. No uses estas cuentas ni estos ejemplos en producción. El backend queda en `http://localhost:8080`.

### 3. Iniciar el frontend

En otra ventana de PowerShell:

```powershell
cd C:\Users\ignab\IdeaProjects\plan_entrenamientos\frontend
npm install
npm run dev
```

Abre la dirección local que indique Vite, normalmente `http://127.0.0.1:5173`. Durante desarrollo Vite redirige `/api` al backend; abre la interfaz con la URL de Vite, no con la del backend.

### 4. Probar el flujo completo

También puedes seleccionar **Crear cuenta de entrenador** en la pantalla de acceso e ingresar con esa cuenta. Ya dentro, usa **Agregar atleta** para crear una cuenta vinculada y después inicia sesión como atleta con su correo y contraseña. Las contraseñas se guardan como hash y no dependen de las variables demo. Es una función preliminar de uso local: no incluye recuperación de contraseña, invitaciones ni verificación de correo.

1. Ingresa como `coach@entrenos.local` con la contraseña del entrenador.
2. Selecciona el atleta y crea una sesión. Los grupos repiten sus hijos en el mismo orden; las pausas se agregan como pasos explícitos. Para un rodaje de 14 km, agrega una carrera con distancia `14000`, ritmo rápido `3:54` y ritmo suave `4:08` min/km. Puedes agregar un ejemplo editable de `2 × 6 × 300 m` con sus pausas.
3. Cierra sesión. Entra como `atleta@entrenos.local` con su contraseña.
4. Abre la sesión correspondiente, registra tiempos o distancias por paso y añade el resumen. El ritmo medio se calcula visualmente si se indican tiempo y distancia totales.
5. Vuelve a ingresar como entrenador para revisar el resultado. La edición del atleta reemplaza el registro previo completo.

Los campos de tiempo usan minutos:segundos, por ejemplo `45:30` o `0:51.9` para una serie. El tiempo total admite segundos enteros; los objetivos y tiempos por repetición permiten hasta dos decimales. La API sigue usando segundos internamente.

La fecha inicial del formulario corresponde al lunes de la semana visible; cámbiala si quieres otro día. El token se mantiene únicamente en memoria del navegador: recargar la página o reiniciar el backend obliga a ingresar otra vez.

## Organización

### Preparación de Docker y Render (pruebas)

El `Dockerfile` de la raíz construye React y Java, ejecuta las pruebas Maven y empaqueta el frontend como recurso estático de Spring Boot. La aplicación usa el mismo dominio para `/api`. Es una preparación: no se crea ni despliega ningún servicio automáticamente.

1. Inicia Docker Desktop y espera a que el motor Linux esté disponible. Desde la raíz del proyecto ejecuta:

   ```powershell
   docker build -t ritmo:local .
   ```

2. Crea fuera del repositorio, por ejemplo en una carpeta privada, un archivo `ritmo-local.env` con los valores siguientes. Sustituye la contraseña por la real; no subas ese archivo a Git. `host.docker.internal` corresponde al equipo anfitrión en Docker Desktop; PostgreSQL debe permitir la conexión desde el contenedor.

   ```text
   DB_URL=jdbc:postgresql://host.docker.internal:5432/entrenos
   DB_USER=entrenos_app
   DB_PASSWORD=TU_CONTRASEÑA
   ```

3. Ejecuta el contenedor, cambiando la ruta por la ubicación real del archivo:

   ```powershell
   docker run --rm --name ritmo-local -p 8080:8080 --env-file C:\ruta-privada\ritmo-local.env ritmo:local
   ```

   Abre `http://localhost:8080`. Si el puerto está ocupado, detén el backend local o usa `-p 8081:8080` y abre el puerto 8081. Para detenerlo, usa `Ctrl+C` en esa terminal. La aplicación ejecuta `schema.sql` al iniciar: prueba con una base de desarrollo, no una base de producción.

Para una futura configuración en Render, crea un Web Service con runtime Docker, raíz del repositorio, `Dockerfile` en la raíz y contexto de compilación en la raíz. Configura `DB_URL` con una URL **JDBC** (`jdbc:postgresql://HOST:PUERTO/BASE`), `DB_USER` y `DB_PASSWORD` por separado; Render asigna `PORT`. Usa PostgreSQL administrado accesible desde el servicio, no `localhost`, y configura TLS de base de datos según el proveedor. Las variables demo son opcionales y deben omitirse para la prueba en nube. Como health check preliminar puede usarse `/`; confirma solo que se sirve la página, no que la base de datos esté disponible.

La aplicación sigue sin estar preparada para usuarios reales en internet: hay registro abierto, tokens en memoria, sin migraciones ni restauración verificadas. El uso de Docker no corrige estas limitaciones. Consulta la ruta del piloto en [arquitectura.md](docs/arquitectura.md).

### Progreso y seguridad

En el menú lateral selecciona **Mi progreso** o **Progreso de atletas**; en pantallas pequeñas está en la navegación superior. El entrenador puede elegir un atleta vinculado. Filtra por periodo (hasta 366 días) y tipo de sesión; las tarjetas muestran feedback registrado, distancia de carrera, tiempo y ritmo global. El historial abre la sesión para revisar sus resultados.

El porcentaje indica sesiones con feedback sobre sesiones previstas hasta hoy, excluyendo descansos: no mide el cumplimiento de ritmos. Los kilómetros proceden de la distancia total registrada en rodajes, series y cambios de ritmo. El ritmo global usa solo carreras con tiempo y distancia, sumando ambos antes de dividir. Las fechas son las programadas y los datos incompletos no se inventan.

Progreso también muestra el promedio de esfuerzo percibido (RPE) de las sesiones con este dato y una carga estimada, sumando `minutos × RPE` solo cuando ambos están disponibles. Se indica cuántos registros contribuyen a cada cálculo. Es una referencia subjetiva, no un diagnóstico ni un indicador de recuperación.

En **Seguridad** puedes cambiar tu contraseña con la actual; el cambio revoca las sesiones almacenadas en el proceso y debes volver a ingresar. No hay recuperación de contraseña ni correo de verificación. Estas funciones son para desarrollo local y aún no son adecuadas para producción.

El atleta también puede usar **Crear sesión**, seleccionar un entrenador vinculado y añadirla a su semana. El entrenador seleccionado podrá verla. Desde el detalle de cualquier sesión propia, el atleta puede cambiar la fecha conservando sus resultados, o eliminarla tras confirmar: se borran también sus resultados y la sesión deja de aparecer para el entrenador. Se necesita al menos un entrenador vinculado para crearla.

```text
backend/        API Spring Boot, acceso SQL y esquema PostgreSQL
frontend/       React, Axios y estilos adaptables
docs/           Modelo, API, ejemplos, integraciones y próximos pasos
```

Consulta [la arquitectura y los ejemplos JSON](docs/arquitectura.md) para el detalle del modelo, la API, las pausas y el plan de integración futura.
