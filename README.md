# ⚙️ TacticAI — Backend (Spring Boot)

> **API REST segura que autentica usuarios, orquesta la IA y persiste el historial de simulaciones.**
> Caso de Estudio 2 · Programación Orientada a la Web

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-6DB33F?logo=springboot&logoColor=white)
![Postgres](https://img.shields.io/badge/Neon-Postgres-336791?logo=postgresql&logoColor=white)
![Tests](https://img.shields.io/badge/tests-18%20passing-brightgreen)
![Docker](https://img.shields.io/badge/Docker-multi--stage-2496ED?logo=docker&logoColor=white)

---

## 👨‍🎓 Autoría

| | |
|---|---|
| **Desarrollado por** | David Fernando Ramírez de la Parra |
| **Profesor** | Jhonatan Andrés Mideros Narváez |
| **Asignatura** | Programación Orientada a la Web |
| **Institución** | Universidad Cooperativa de Colombia |

---

## 🌐 Servicios

| Servicio | URL |
|---|---|
| ⚙️ Backend | https://tactical-sim-backend.onrender.com |
| ❤️ Salud | https://tactical-sim-backend.onrender.com/actuator/health |
| 🖥️ Frontend | https://tactical-sim-frontend.vercel.app |
| 🧠 Motor de IA | https://tactical-sim-ai-engine.onrender.com |

> 🔒 La ruta raíz `/` responde **403 por diseño** (`anyRequest().authenticated()`). Solo `/actuator/health` y las rutas de autenticación son públicas.
> ⏳ Plan gratuito de Render: la primera petición tras inactividad tarda ~50 s.

---

## 📚 Rol en el caso de estudio

TacticAI simula jugadas de baloncesto y tenis. Este backend es la **capa de aplicación y de borde (gateway)**:

- 🔐 Autentica con **JWT + 2FA por correo**.
- 🚪 Es el **único punto de entrada**: el cliente nunca llama a la IA ni a la base de datos.
- 🧠 Reenvía las simulaciones al motor de IA con una **clave interna**.
- 🗄️ Guarda y exporta el historial en Postgres.
- 🪞 Retransmite el **espejo de pantalla** por WebSocket.

## 🏗️ Arquitectura

```
Frontend (Vercel) ──REST/WS──▶ Backend (Render) ──HTTP + X-Internal-Key──▶ IA (Render)
                                     │
                                     └──JDBC──▶ Neon Postgres
```

| Paquete | Responsabilidad |
|---|---|
| `config` | Configuración (WebClient, beans, etc.) |
| `core` | Elementos transversales del núcleo |
| `gateway` | **Seguridad**: filtro JWT, CORS, lista de rutas públicas (`SecurityConfig`) |
| `modules/user` | Registro, login, verificación 2FA, correo (Brevo) |
| `modules/simulation` | Ejecutar simulación, historial, exportación CSV |
| `modules/mirror` | Salas de espejo por WebSocket |
| `shared` | DTOs y utilidades compartidas |

Roles: `ROLE_USER`, `ROLE_COACH`, `ROLE_ANALYST`, `ROLE_ADMIN`. Contraseñas con **BCrypt**.

---

## 🔌 Endpoints

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 🌍 público | Crear usuario |
| POST | `/api/v1/auth/login` | 🌍 público | Credenciales → desafío 2FA |
| POST | `/api/v1/auth/verify-2fa` | 🌍 público | Código 2FA → **JWT** |
| POST | `/api/v1/simulation/run` | 🔐 JWT | Ejecuta la simulación (llama a la IA) |
| POST | `/api/v1/simulation/history` | 🔐 JWT | Guarda una simulación |
| GET | `/api/v1/simulation/history` | 🔐 JWT | Lista el historial |
| GET | `/api/v1/simulation/history/export` | 🔐 JWT | Exporta a **CSV** |
| POST | `/api/v1/mirror/rooms` | 🔐 JWT | Crea una sala de espejo |
| WS | `/ws/mirror` | según sala | Retransmisión en tiempo real |
| GET | `/actuator/health` | 🌍 público | Estado del servicio |

---

## 🔐 Seguridad

- **JWT** firmado con `JWT_SECRET` (obligatorio en producción).
- **2FA** por correo con la API HTTP de **Brevo**.
- **CORS** configurable (`CORS_ALLOWED_ORIGINS`).
  ⚠️ Los orígenes se separan por coma y **sin espacios** (no se hace `trim`).
- **Clave interna** `AI_INTERNAL_KEY`: se envía en la cabecera `X-Internal-Key` hacia la IA; si no coincide, la IA responde 401.
- Consola H2 desactivada en producción (`H2_CONSOLE_ENABLED=false`).
- 🚫 **Ningún secreto va en el repositorio**: se configuran en el panel de Render.

---

## 🔧 Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DATABASE_URL` | prod ✅ | URL JDBC de Postgres (Neon) |
| `DATABASE_DRIVER` | prod ✅ | `org.postgresql.Driver` |
| `DATABASE_USERNAME` | prod ✅ | Usuario de la BD |
| `DATABASE_PASSWORD` | prod ✅ | Contraseña de la BD |
| `JPA_DIALECT` | prod ✅ | Dialecto Hibernate para Postgres |
| `JWT_SECRET` | prod ✅ | Secreto para firmar JWT (el valor por defecto es solo para desarrollo) |
| `CORS_ALLOWED_ORIGINS` | ✅ | Orígenes permitidos, separados por coma **sin espacios** |
| `AI_ENGINE_URL` | ✅ | URL del motor de IA |
| `AI_INTERNAL_KEY` | ➖ | Clave compartida con la IA (cabecera `X-Internal-Key`) |
| `H2_CONSOLE_ENABLED` | ➖ | `false` en producción |
| `MAIL_ENABLED` | ➖ | `true` para enviar el 2FA por correo |
| `BREVO_API_KEY` | ➖ | Clave de la API de Brevo |
| `MAIL_FROM_ADDRESS` | ➖ | Remitente del correo |
| `PORT` | ➖ | Puerto (por defecto 9096) |

Ejemplo de CORS:

```
https://tactical-sim-frontend.vercel.app,http://localhost:3000
```

---

## 🚀 Uso local

Requisitos: **JDK 21** y **Maven 3.9+**.

> ⚠️ Si tu Java por defecto es otro (p. ej. 23), Lombok falla al compilar. Fuerza el JDK 21:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version        # debe decir 21
```

```powershell
git clone https://github.com/TU-USUARIO/tactical-sim-backend.git
cd tactical-sim-backend
mvn spring-boot:run
```

Sin variables de BD, usa **H2 en memoria** (solo desarrollo). Corre en http://localhost:9096.
También puedes abrirlo en **IntelliJ** y usar el panel de Maven.

## 🧪 Pruebas

```powershell
mvn test
```

**18 pruebas JUnit** (`MirrorControllerTest`, `MirrorWebSocketIntegrationTest`, entre otras), que **no necesitan base de datos**. Se ejecutan en GitHub Actions en cada push.

## 🐳 Docker y despliegue (Render)

`Dockerfile` multi-stage: `maven:3.9-eclipse-temurin-21` (build) → `eclipse-temurin:21-jre-alpine` (runtime). Puerto 9096 (o `PORT`).

1. Crea un **Web Service** en Render con entorno **Docker** apuntando a este repo.
2. Añade las variables de entorno de la tabla.
3. Healthcheck: `/actuator/health`.
4. Cada `push` a `main` puede redesplegar.

---

## ⚠️ Límites conocidos

- Las **salas de espejo** están en memoria (se pierden al reiniciar).
- Un único módulo `gateway` dentro del backend (no un API Gateway regional dedicado).
- No hay broker de mensajes; el WebSocket y los hilos cubren lo asíncrono.
- Render gratis duerme el servicio tras inactividad.

## 🗺️ Próximos pasos

- `trim` de los orígenes CORS.
- *Rate limiting* en login y 2FA.
- Salas de espejo persistentes (Redis).

---

<p align="center">
Hecho por <b>David Fernando Ramírez de la Parra</b><br>
Profesor: Jhonatan Andrés Mideros Narváez · Programación Orientada a la Web · Universidad Cooperativa de Colombia
</p>
