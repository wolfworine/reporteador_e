# Plan de migración — entel-role-repository-frontend

> Depende de `../backend/docs/migration-plan.md` (mismo dominio real,
> mismas decisiones de alcance). Fuentes: `D:\Capacitacion\IA\Migracion_Reporte\05_VALIDACION_Y_HALLAZGOS_REALES.md`,
> `06_PLAN_MIGRACION_REACT_JAVA21.md`, y las vistas reales `paginas/*.xhtml`
> extraídas en `D:\Capacitacion\IA\Migracion_Reporte\JSF_RepositorioV3.9.2_extraido\war_contenido\paginas\`.

## 1. Qué hace el proyecto hoy

Interfaz JSF 2.2 + PrimeFaces 5.2, renderizada 100% en servidor, sin
separación front/back. 8 módulos de mantenimiento (Área, Cargo, Ubicación,
Aplicación, Rol, Suministro, Atributo/Detalle, Usuario) + un módulo de
Reportes con exportación PDF/Excel. Menú lateral dinámico según tipo de
usuario (`MenuController`/`Menu`).

## 2. Stack actual vs. objetivo

| | Actual | Objetivo |
|---|---|---|
| Renderizado | Server-side (JSF/Facelets, `.xhtml`) | **SPA en React 19** + TypeScript |
| Build | Maven empaqueta el `.war` completo | **Vite** |
| Componentes UI | PrimeFaces (`p:dataTable`, `p:commandButton`, etc.) | Componentes React propios + **TanStack Table** para las tablas anidadas |
| Estado de servidor | Implícito en el managed bean `@SessionScoped` | **TanStack Query** (cache, invalidación, reintentos) |
| Comunicación con backend | Llamadas de método directas dentro del mismo proceso JVM (JSF → Service → Repository) | **HTTP/JSON** contra la API REST reactiva de `entel-role-repository-backend` (ver `../backend/docs/architecture.md`) |
| Reportes async | No existe (todo síncrono, con el bug de ruta local ya documentado) | Polling con TanStack Query sobre `GET /api/reportes/{id}/estado` (el backend resuelve el asincronismo real vía Kafka) |

## 3. Deuda/carencias detectadas en las vistas legacy (a no repetir)

- Tablas PrimeFaces con anidamiento profundo (`vista.xhtml`: `p:dataTable` →
  `p:subTable` → hasta 4 `p:dataTable` internas para Roles/Grupos/Inbox/
  DefaultInbox) — es difícil de leer/mantener en Facelets; en React se
  modela como una jerarquía de componentes explícita (`ReporteTabla` →
  `RolRow` → `CategoriaSuministroList`), más testeable.
  Ver: `paginas/reportes/vista.xhtml`.
- No hay manejo de errores visible al usuario en las vistas actuales — los
  `catch (Exception ex) { System.out.println(...) }` del backend legacy no
  llegan a la UI de ninguna forma. El frontend nuevo debe mostrar estados
  de error explícitos (toast/inline) para cada mutación fallida.
- Exportar a PDF/Excel es un `p:commandLink` con `ajax="false"` (descarga
  de página completa) — en el frontend nuevo, la descarga se dispara desde
  un botón que llama al endpoint REST y usa el `Content-Disposition` de la
  respuesta; para el flujo asíncrono (Kafka), el botón debe reflejar los
  estados "Generando… / Listo, descargar" sin recargar la página.

## 4. Decisiones de alcance ya tomadas con el usuario (09-ago-2026)

- **Frontend:** React, **última versión estable** (React 19). ✅ Decidido.
- **Backend consumido:** API REST reactiva de Quarkus (ver
  `../backend/docs/architecture.md`) — el frontend no conoce ni le importa
  que el backend sea reactivo internamente, solo consume JSON sobre HTTP.
- **Gestión de estado de servidor:** TanStack Query (equivalente al rol que
  cumplían los "Signals" en la propuesta Angular descartada).
- Todas las demás decisiones de dominio (módulos reales, corrección del
  bug de contraseñas, etc.) son compartidas con el backend — no se repiten
  aquí, ver `../backend/docs/migration-plan.md`.

## 5. Plan por fases

1. **Fase 0 — Bootstrap.** Proyecto Vite + React 19 + TypeScript
   compilable, lint/test configurados.
2. **Fase 1 — Cliente API + tipos.** Cliente HTTP tipado + interfaces
   TypeScript espejo de los DTOs del backend.
3. **Fase 2 — Layout, login y menú dinámico.** Consume
   `GET /api/menu/{tipoUsuario}` del backend (feature 7 del track backend).
4. **Fase 3 — CRUDs simples.** Área, Cargo, Ubicación, Aplicación, Usuario.
5. **Fase 4 — Cadena compleja.** Rol (VerSuministro), Suministro
   (VerAtributo), Atributo con las 4 pestañas.
6. **Fase 5 — Reportes.** Filtro + tabla anidada + exportación, con estado
   async si el backend ya expone Kafka (feature 9 del track backend); si
   no está listo aún, se implementa primero contra el endpoint síncrono
   (feature 8 del backend) y se agrega el polling después.
7. **Fase 6 — Tests.** Cobertura de los flujos críticos.

Ver `feature_list.json` para el desglose ejecutable. Este track puede
avanzar en paralelo al backend usando datos mock hasta que cada endpoint
real exista — documentar en cada feature si depende de un endpoint backend
específico (por id de feature del otro `feature_list.json`).
