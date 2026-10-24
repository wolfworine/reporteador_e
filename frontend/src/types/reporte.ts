import type { Detalle } from './detalle'

/**
 * Espejo TypeScript de las clases DTO (no-JPA, `Serializable` plano)
 * legacy:
 * `pe.edu.cibertec.proyemp.model.DetalleRol`
 * (.../model/DetalleRol.java)
 *
 * Representa un Rol dentro del reporte, con sus suministros ya separados
 * en las 4 categorías (Roles/Grupos/Inbox/DefaultInbox) — ver
 * `docs/architecture.md` (jerarquía Cargo→Rol→4 categorías, contrato
 * funcional central del sistema que no se debe reinventar) y
 * `paginas/reportes/vista.xhtml` (tablas anidadas PrimeFaces que este tipo
 * reemplaza).
 */
export interface DetalleRol {
  /**
   * Java: `idRol`. Corregido tras coordinación cross-track con el backend
   * (feature 10/11 del backend, `domain/DetalleRol.idRol`): es `String`
   * en el dominio real (id opaco, no autonumérico) — el `number` original
   * era una extrapolación propia del frontend, nunca confirmada, ver nota
   * histórica más abajo en este archivo.
   */
  idRol: string
  /** Nombre del rol. Java: `rol`. */
  rol: string
  /** Java: `tipo`. */
  tipo: string
  /** Java: `aplicacion`. */
  aplicacion: string
  /**
   * Java: `tiporol` (`ArrayList<String>`). **Resuelto tras coordinación
   * cross-track (2026-08-19):** confirmado en el código fuente legacy
   * (`ReporteManagedBean.java` y su respaldo `CopyOfReporteManagedBean.java`,
   * los dos únicos lugares donde se construye un `DetalleRol`) que
   * `setTiporol(...)` **nunca se invoca** — la columna "Titularidad" de
   * `paginas/reportes/vista.xhtml` (`p:dataTable
   * value="#{detallerol.tiporol}"`) siempre renderizó una tabla vacía en
   * el legacy, es dead code sin dato real que preservar. El backend
   * (`domain/DetalleRol.tiposConDatos`, feature 7) reutiliza esta misma
   * forma (`List<String>` tras serializar) para un campo con semántica
   * distinta y sí real: qué categorías de suministro (ROLES/GRUPOS/
   * INBOX/DEFAULTINBOX) tienen datos para este rol. Se mantiene el
   * nombre `tipoRol` en este tipo (sin renombrar, para no romper
   * `ReporteTabla.tsx`/tests ya aprobados), pero el contenido real que
   * llega del backend son códigos de categoría, no valores de
   * titularidad — considerar renombrar la columna "Titularidad" a algo
   * como "Categorías con datos" en una futura iteración de UI.
   */
  tipoRol: string[]
  /** Categoría "Roles". Java: `suministroRoles`. */
  suministroRoles: Detalle[]
  /** Categoría "Grupos". Java: `suministroGrupos`. */
  suministroGrupos: Detalle[]
  /** Categoría "Inbox". Java: `suministroInbox`. */
  suministroInbox: Detalle[]
  /** Categoría "DefaultInbox". Java: `suministroDefaultinbox`. */
  suministroDefaultinbox: Detalle[]
  /** Java: `cantidadSuministroRol`. */
  cantidadSuministroRol: number
  /**
   * Java: `cantidadSuministros`. El backend (`domain/DetalleRol`, feature
   * 7) documenta que este campo era dead code en el legacy (declarado
   * pero nunca poblado en `ReporteManagedBean.reporte()`) y por eso solo
   * conserva `cantidadSuministroRol`. La capa de mapeo del backend
   * (feature 10/11, coordinación cross-track 2026-08-19) expone este
   * campo con el mismo valor que `cantidadSuministroRol` en vez de
   * omitirlo, para no romper este tipo ya aprobado del frontend.
   */
  cantidadSuministros: number
}

/**
 * Espejo TypeScript de:
 * `pe.edu.cibertec.proyemp.model.Reporte`
 * (.../model/Reporte.java)
 *
 * DTO no persistente armado por `ReporteManagedBean.reporte()` recorriendo
 * `Cargo → Atributo → (Aplicacion, Rol, Suministro, Detalle)` (ver
 * `../backend/docs/architecture.md`). Un `Reporte` agrupa todos los
 * `DetalleRol` (roles) de un mismo Cargo dentro de una misma Área.
 */
export interface Reporte {
  /** Nombre del cargo. Java: `cargo`. */
  cargo: string
  /** Nombre del área. Java: `area`. */
  area: string
  /** Código de negocio del área (`Area.id`). Java: `codigoarea`. */
  codigoArea: string
  /** Código de negocio del cargo (`Cargo.id`). Java: `codigocargo`. */
  codigoCargo: string
  /** Java: `detallerol` (`ArrayList<DetalleRol>`). */
  detalleRol: DetalleRol[]
  /** Java: `cantidadRol`. */
  cantidadRol: number
  /** Java: `cantidadSuministro`. */
  cantidadSuministro: number
}

/**
 * Tipos del flujo asíncrono de generación/notificación/descarga de reportes.
 *
 * **Corregido 2026-08-20** (bug real reportado por el usuario: "presionar
 * Generar devuelve 'formato debe ser pdf o excel, recibido: null'"): este
 * bloque llevaba desde su creación marcado como "extrapolación propia, no
 * confirmada" -- las features 9/10 del backend llevan `done` desde hace
 * días, pero nunca se reconcilió este contrato contra el código fuente
 * real (`web/dto/ReporteGenerarRequest.java`), mismo patrón que login/menú
 * hoy: se probó la app de verdad y recién ahí apareció. El body real es
 * `{idAplicacion, idArea, formato}` -- nombres de campo DISTINTOS
 * (`areaCodigo`/`aplicacionCodigo` no existían del lado del backend, así
 * que esos dos valores tampoco llegaban nunca) y **`formato` no tenía
 * ningún selector en la UI en absoluto**, así que siempre viajaba
 * `undefined` -> `null` en el JSON -> el backend lo rechazaba con 400.
 */

/**
 * Body de `POST /api/reportes/generar` -- espejo exacto de
 * `web/dto/ReporteGenerarRequest.java` (`record ReporteGenerarRequest(String
 * idAplicacion, String idArea, String formato)`).
 */
export interface ReporteGenerarRequest {
  idAplicacion: string
  idArea: string
  formato: ReporteFormato
}

/** Respuesta `202 Accepted` de `POST /api/reportes/generar`: solo el id, nunca el binario. */
export interface ReporteGenerarResponse {
  id: string
}

/**
 * Estados posibles de un evento SSE de `/api/reportes/eventos`. `FALLIDO`
 * modela el camino de error documentado en
 * `../backend/docs/kafka-reactive-guidance.md` (evento `report.failed` tras
 * agotar reintentos) — se decide cubrirlo ya en esta feature (no diferirlo
 * a la feature 9) porque el costo de modelarlo es bajo y le da a
 * `useReportEvents` un camino de error real que probar, tal como sugiere la
 * feature.
 */
export type ReporteEstadoEvento = 'LISTO' | 'FALLIDO'

/**
 * Shape de cada evento JSON que llega por el stream SSE de
 * `GET /api/reportes/eventos`. El stream es compartido por todos los
 * reportes en curso de la sesión (una sola conexión `EventSource`, ver
 * `docs/architecture.md`), así que cada consumidor debe filtrar por `id`
 * para quedarse solo con el evento que le interesa — ver
 * `modules/reporte/useReportEvents.ts`.
 */
export interface ReporteEvento {
  id: string
  estado: ReporteEstadoEvento
  /** Mensaje humano-legible opcional, útil sobre todo cuando `estado === 'FALLIDO'`. */
  mensaje?: string
}

/**
 * Formato de exportación pedido a `GET /api/reportes/{id}/descargar`.
 * Decisión de diseño documentada (no confirmada en backend real, que aún no
 * existe): un único endpoint con un query param `?formato=pdf|excel`, en
 * vez de dos paths separados — más simple de mantener en el cliente y
 * replica la intención de los dos `p:commandLink` (`pdf`/`xml`) de
 * `paginas/reportes/vista.xhtml`, que llaman a acciones distintas del mismo
 * managed bean (`imprimir()`/`excel()`) sobre el mismo reporte ya generado.
 */
export type ReporteFormato = 'pdf' | 'excel'
