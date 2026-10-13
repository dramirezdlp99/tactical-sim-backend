# TacticAI — Backend

API Spring Boot 3.3 (Java 21) del simulador táctico: autenticación JWT con verificación en dos pasos, historial de simulaciones, proxy hacia el motor de IA y relevo WebSocket para duplicar la pantalla.

## Endpoints principales

| Ruta | Descripción |
|---|---|
| `POST /api/v1/auth/register`, `/login`, `/verify-2fa` | Registro e inicio de sesión |
| `POST /api/v1/simulation/run` | Ejecuta una simulación (consulta al motor de IA) |
| `POST/GET /api/v1/simulation/history`, `GET .../history/export` | Historial y exportación CSV |
| `WS /ws/mirror` | Duplicación de pantalla en tiempo real |
| `GET /actuator/health` | Estado del servicio |

## Uso local

Requiere **JDK 21** (con JDK 23 falla Lombok) y Maven 3.9.

```powershell
$env:JAVA_HOME = "C:\ruta\a\jdk-21"
mvn test
mvn spring-boot:run     # puerto 9096, base H2 en memoria por defecto
```

## Variables de entorno

| Variable | Descripción |
|---|---|
| `DATABASE_URL`, `DATABASE_DRIVER`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JPA_DIALECT` | Base de datos (por defecto H2 en memoria) |
| `JWT_SECRET` | **Obligatoria en producción**: el valor por defecto del repositorio es solo para desarrollo |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (HTTP y WebSocket); debe incluir la URL del frontend |
| `AI_ENGINE_URL` | URL del motor de IA |
| `AI_INTERNAL_KEY` | Clave compartida con el motor de IA (cabecera `X-Internal-Key`). Opcional; debe ser igual a la del servicio de IA |
| `H2_CONSOLE_ENABLED` | Poner `false` en producción |

## Pruebas

18 pruebas JUnit (controlador y WebSocket del espejo), sin base de datos. CI en `.github/workflows/ci.yml`.

## Despliegue

Render con el `Dockerfile` del repositorio (build multi-etapa con JDK 21).
