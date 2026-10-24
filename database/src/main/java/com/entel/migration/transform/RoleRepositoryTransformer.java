package com.entel.migration.transform;

import com.entel.migration.dump.ParsedDump;
import com.entel.migration.dump.model.Aplicacion;
import com.entel.migration.dump.model.Area;
import com.entel.migration.dump.model.Atributo;
import com.entel.migration.dump.model.Cargo;
import com.entel.migration.dump.model.Rol;
import com.entel.migration.dump.model.Suministro;
import com.entel.migration.dump.model.Ubicacion;
import com.entel.migration.schema.ItemAttributes;
import com.entel.migration.schema.RoleRepositoryKeys;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforma la salida de {@link com.entel.migration.dump.MysqlDumpParser}
 * (feature 3, {@link ParsedDump}) a los items DynamoDB del diseno canonico
 * de docs/architecture.md (feature 2, {@code com.entel.migration.schema.*}).
 *
 * <p><b>Funcion pura, sin acceso a red</b>: solo lee el {@link ParsedDump} en
 * memoria y devuelve un {@link TransformResult}. El subcomando {@code migrate}
 * ({@link com.entel.migration.command.MigrateCommand}) es quien escribe el
 * resultado a DynamoDB.
 *
 * <h2>Reescrito 2026-08-19 -- coordinacion cross-track con el backend real</h2>
 *
 * La version anterior de esta clase (feature 4, aprobada, 53/53 tests
 * propios de este track en verde) fue verificada exhaustivamente contra el
 * dump real pero **nunca contra el backend real**: sus items usaban
 * {@code PK}/{@code SK}/{@code GSI1PK}/{@code GSI1SK} en mayusculas (el
 * backend usa minusculas), {@code GSI1PK}/{@code GSI1SK} con IDs numericos
 * resueltos por nombre (el backend los construye con los *codigos* de
 * negocio de Aplicacion/Area/Cargo, ver
 * {@code AtributoRepository.guardar()}), nombres de atributo distintos en
 * los items {@code ATRIBUTO#} ({@code idAtributo}/{@code idRol}/{@code
 * idSuministro} en vez de {@code id}/{@code rolId}/{@code suministroId},
 * sin {@code detalleId}/{@code categoria}/{@code cargoNombre}/{@code
 * rolNombre} como atributos propios), y **nunca creaba items {@code ROL#}**
 * -- los 741 registros de la tabla legado {@code rol} no llegaban a la
 * tabla en absoluto, aunque el backend define un repositorio completo
 * ({@code RolRepository.java}) que los necesita. Se descubrio corriendo
 * `migrate` de verdad contra el backend real por primera vez esta sesion
 * (fallo con {@code DynamoDbException: key element does not match schema}
 * en el primer intento). Esta version corrige los cuatro problemas,
 * verificada campo a campo contra el codigo fuente real del backend
 * ({@code RoleRepositoryTable.java}, {@code RolRepository.java}, {@code
 * SuministroRepository.java}, {@code AtributoRepository.java}), no contra
 * suposiciones.
 *
 * <h2>Decisiones de mapeo (esquema real del dump, no el de {@code consulta.sql})</h2>
 *
 * Heredadas de la version anterior, siguen vigentes (no se reabrieron sin
 * motivo):
 *
 * <ul>
 *   <li><b>Patron 1 (catalogos)</b>: la PK usa el {@code codigo} de negocio
 *       de cada catalogo ({@code area.codigo}, {@code cargo.codigo},
 *       {@code ubicacion.codigo}, {@code aplicacion.codigo}).</li>
 *   <li><b>Patron 2b (Rol-&gt;Suministro)</b>: {@code suministro.id_rol} es
 *       FK numerica directa y confiable.</li>
 *   <li><b>{@code atributo.aplicacion}/{@code rol.area}/{@code rol.cargo}
 *       son *nombres*</b> (coinciden con {@code aplicacion.aplicacion} /
 *       {@code area.area} / {@code cargo.nombre}), nunca con las columnas
 *       {@code codigo} -- la resolucion nombre-&gt;codigo se hace con los
 *       indices construidos en este transformer. Verificado adicionalmente
 *       ahora (2026-08-19): {@code rol.ubicacion} SI es directamente el
 *       {@code codigo} de {@code ubicacion} (ej. {@code 'PR0015'} matchea
 *       {@code ubicacion.codigo='PR0015'} en la fila real), sin necesidad de
 *       resolucion -- confirmado con {@code grep} sobre el dump real, no
 *       asumido por simetria con area/cargo.</li>
 *   <li><b>Ambiguedad de {@code cargo.nombre}</b>: 2 de 695 nombres
 *       duplicados con ids distintos -- regla determinista: se toma la fila
 *       con el id numerico MENOR.</li>
 *   <li><b>Nombres sin catalogo correspondiente</b> (14 de 741 filas de
 *       {@code rol} con {@code rol.area} = {@code "LEGAL"}/{@code
 *       "PLANEAMIENTO"}, sin fila de {@code area} real): no se descartan,
 *       se les asigna un marcador de fallback deterministico ({@link
 *       #UNRESOLVED_NAME_PREFIX} + nombre) en vez del codigo real.</li>
 *   <li><b>{@code atributo.parametro}</b> ya es literalmente la categoria
 *       ({@code ROLES}/{@code GRUPOS}/{@code INBOX}/{@code DEFAULTINBOX}).</li>
 *   <li><b>{@code idDetalleAtributo}</b>: este dump no tiene columna propia
 *       -- se usa el propio {@code atributo.id} tambien como {@code
 *       detalleId} (determinista, sin inventar un id nuevo).</li>
 *   <li><b>Estado por defecto</b>: el dump real no trae columna de estado en
 *       {@code atributo} -- se asume {@link #DEFAULT_ESTADO}.</li>
 *   <li><b>Fuera de alcance (no se migran)</b>: {@code detalle}, {@code
 *       usuario}, {@code login}, {@code sequence}. {@code estado}/{@code
 *       menu} no existen como tablas en este dump concreto.</li>
 * </ul>
 *
 * <h2>Nuevo en esta version: items {@code ROL#&lt;id&gt;/METADATA}</h2>
 *
 * Un item por cada fila de {@code rol} (741), con el shape exacto de
 * {@code RolRepository.guardar()}: {@code ubicacionCodigo} = {@code
 * rol.ubicacion} tal cual (ya es codigo); {@code areaCodigo}/{@code
 * cargoCodigo} resueltos nombre-&gt;codigo con el mismo mecanismo e indices
 * que ya usaban los items {@code ATRIBUTO#} (mismo fallback determinista si
 * no resuelve); {@code rolTipo} = {@code rol.tipo} crudo (ej. {@code
 * "DINAMICO"}, sin traducir a BASE/ADICIONAL -- esa traduccion es una
 * decision de {@code service/ReporteService} del backend para el reporte,
 * no del dominio {@code Rol} en si, confirmado leyendo
 * {@code RolRepository.aDominio()} que no traduce nada). {@code situacion}
 * y {@code rolBase} no existen como columnas en este dump (confirmado
 * contra el {@code CREATE TABLE rol} real) -- se dejan como cadena vacia,
 * mismo criterio que ya usaba esta clase para valores ausentes ({@link #s}).
 */
public final class RoleRepositoryTransformer {

    /**
     * Valor de estado por defecto para los items {@code ATRIBUTO#} de este
     * dataset (ver docs/architecture.md, decision 2026-08-09): el dump real
     * de referencia no trae columna de estado en {@code atributo}. Debe
     * coincidir con un valor real de {@code EstadoAtributo} del backend
     * (verificado: {@code ACTIVO}/{@code INACTIVO}).
     */
    public static final String DEFAULT_ESTADO = "ACTIVO";

    /**
     * Prefijo de fallback determinista para {@code aplicacionCodigo}/{@code
     * areaCodigo}/{@code cargoCodigo} cuando el nombre de texto
     * correspondiente ({@code atributo.aplicacion}, {@code rol.area},
     * {@code rol.cargo}) no coincide con ninguna fila de catalogo real.
     * Mantiene la clave estable entre corridas (idempotencia) sin inventar
     * un codigo real inexistente.
     *
     * <p>Publico (no package-private) porque {@code command.VerifyCommand}
     * (feature 5) necesita distinguir estos marcadores de fallback
     * deterministas de una referencia realmente rota, sin duplicar el
     * literal en otro paquete.
     */
    public static final String UNRESOLVED_NAME_PREFIX = "NOMBRE#";

    /**
     * Fallback determinista cuando {@code atributo.id_rol} no tiene un
     * {@code Rol} correspondiente en el dump. Publico por el mismo motivo
     * que {@link #UNRESOLVED_NAME_PREFIX}.
     */
    public static final String UNRESOLVED_ROL_PREFIX = "ROLDESCONOCIDO#";

    private static final String TIPO_AREA = "AREA";
    private static final String TIPO_CARGO = "CARGO";
    private static final String TIPO_UBICACION = "UBICACION";
    private static final String TIPO_APLICACION = "APLICACION";

    public TransformResult transform(ParsedDump dump) {
        List<Aplicacion> aplicaciones = dump.rows("aplicacion").stream().map(Aplicacion::fromRow).toList();
        List<Area> areas = dump.rows("area").stream().map(Area::fromRow).toList();
        List<Cargo> cargos = dump.rows("cargo").stream().map(Cargo::fromRow).toList();
        List<Ubicacion> ubicaciones = dump.rows("ubicacion").stream().map(Ubicacion::fromRow).toList();
        List<Rol> roles = dump.rows("rol").stream().map(Rol::fromRow).toList();
        List<Suministro> suministros = dump.rows("suministro").stream().map(Suministro::fromRow).toList();
        List<Atributo> atributos = dump.rows("atributo").stream().map(Atributo::fromRow).toList();

        Map<String, Aplicacion> aplicacionByName = indexByNameMinId(aplicaciones, Aplicacion::aplicacion, Aplicacion::id);
        Map<String, Area> areaByName = indexByNameMinId(areas, Area::area, Area::id);
        Map<String, Cargo> cargoByName = indexByNameMinId(cargos, Cargo::nombre, Cargo::id);
        Map<Long, Rol> rolById = new HashMap<>();
        for (Rol rol : roles) {
            rolById.putIfAbsent(rol.id(), rol);
        }

        List<Map<String, AttributeValue>> roleRepositoryItems = new ArrayList<>();

        for (Area area : areas) {
            roleRepositoryItems.add(catalogItem(TIPO_AREA, area.codigo(), area.id(), area.area()));
        }
        for (Cargo cargo : cargos) {
            roleRepositoryItems.add(catalogItem(TIPO_CARGO, cargo.codigo(), cargo.id(), cargo.nombre()));
        }
        for (Ubicacion ubicacion : ubicaciones) {
            roleRepositoryItems.add(catalogItem(TIPO_UBICACION, ubicacion.codigo(), ubicacion.id(), ubicacion.ubicacion()));
        }
        for (Aplicacion aplicacion : aplicaciones) {
            roleRepositoryItems.add(catalogItem(TIPO_APLICACION, aplicacion.codigo(), aplicacion.id(), aplicacion.aplicacion()));
        }
        int catalogItemCount = areas.size() + cargos.size() + ubicaciones.size() + aplicaciones.size();

        int unresolvedAreaNamesRol = 0;
        int unresolvedCargoNamesRol = 0;
        for (Rol rol : roles) {
            String areaCodigo = resolveOrFallback(areaByName, rol.area(), Area::codigo);
            String cargoCodigo = resolveOrFallback(cargoByName, rol.cargo(), Cargo::codigo);
            if (isUnresolved(areaCodigo)) {
                unresolvedAreaNamesRol++;
            }
            if (isUnresolved(cargoCodigo)) {
                unresolvedCargoNamesRol++;
            }
            roleRepositoryItems.add(rolItem(rol, areaCodigo, cargoCodigo));
        }

        for (Suministro suministro : suministros) {
            roleRepositoryItems.add(suministroItem(suministro));
        }

        int unresolvedAplicacionNames = 0;
        int unresolvedAreaNames = 0;
        int unresolvedCargoNames = 0;
        int unresolvedRolReferences = 0;

        for (Atributo atributo : atributos) {
            Rol rol = rolById.get(atributo.idRol());

            String aplicacionCodigo = resolveOrFallback(aplicacionByName, atributo.aplicacion(), Aplicacion::codigo);
            if (isUnresolved(aplicacionCodigo)) {
                unresolvedAplicacionNames++;
            }

            String areaCodigo;
            String cargoCodigo;
            String cargoNombre;
            String rolTipo;
            String rolNombre;
            if (rol == null) {
                unresolvedRolReferences++;
                areaCodigo = UNRESOLVED_ROL_PREFIX + atributo.idRol();
                cargoCodigo = UNRESOLVED_ROL_PREFIX + atributo.idRol();
                cargoNombre = "";
                rolTipo = "";
                rolNombre = "";
            } else {
                areaCodigo = resolveOrFallback(areaByName, rol.area(), Area::codigo);
                cargoCodigo = resolveOrFallback(cargoByName, rol.cargo(), Cargo::codigo);
                cargoNombre = rol.cargo() == null ? "" : rol.cargo();
                rolTipo = rol.tipo() == null ? "" : rol.tipo();
                rolNombre = rol.nombre() == null ? "" : rol.nombre();
                if (isUnresolved(areaCodigo)) {
                    unresolvedAreaNames++;
                }
                if (isUnresolved(cargoCodigo)) {
                    unresolvedCargoNames++;
                }
            }

            roleRepositoryItems.add(
                    atributoItem(atributo, aplicacionCodigo, areaCodigo, cargoCodigo, cargoNombre, rolTipo, rolNombre));
        }

        TransformResult.Stats stats = new TransformResult.Stats(
                catalogItemCount,
                roles.size(),
                suministros.size(),
                atributos.size(),
                0,
                unresolvedAplicacionNames,
                unresolvedAreaNames + unresolvedAreaNamesRol,
                unresolvedCargoNames + unresolvedCargoNamesRol,
                unresolvedRolReferences);

        // RoleRepositoryMenu (Patron 5): la tabla `menu` no existe en el
        // dump real de referencia (ni en el de `estado`) - ver
        // docs/architecture.md y docs/migration-plan.md. Ninguno de los
        // records tipados de com.entel.migration.dump.model modela `menu`
        // porque nunca se vio su esquema real; en vez de adivinar nombres de
        // columna sin evidencia, se documenta este dataset con 0 items en
        // RoleRepositoryMenu como el resultado correcto (no un bug).
        return new TransformResult(roleRepositoryItems, List.of(), stats);
    }

    private static Map<String, AttributeValue> catalogItem(String tipo, String codigo, Long id, String nombre) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(ItemAttributes.PK, s(RoleRepositoryKeys.catalogPk(tipo, codigo)));
        item.put(ItemAttributes.SK, s(RoleRepositoryKeys.CATALOG_SK));
        item.put(ItemAttributes.TIPO, s(tipo));
        item.put(ItemAttributes.ID, s(String.valueOf(id)));
        item.put(ItemAttributes.CODIGO, s(codigo));
        item.put(ItemAttributes.NOMBRE, s(nombre));
        return item;
    }

    /**
     * Item {@code ROL#&lt;id&gt;/METADATA} -- shape exacto de
     * {@code RolRepository.guardar()} del backend (verificado leyendo el
     * archivo fuente real, no el documento canonico que no cubre esta
     * fila). {@code situacion}/{@code rolBase} quedan vacios: no existen
     * como columnas en el dump real (confirmado contra el {@code CREATE
     * TABLE rol}).
     */
    private static Map<String, AttributeValue> rolItem(Rol rol, String areaCodigo, String cargoCodigo) {
        String id = String.valueOf(rol.id());
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(ItemAttributes.PK, s(RoleRepositoryKeys.rolPk(id)));
        item.put(ItemAttributes.SK, s(RoleRepositoryKeys.CATALOG_SK));
        item.put(ItemAttributes.ID, s(id));
        item.put(ItemAttributes.UBICACION_CODIGO, s(rol.ubicacion()));
        item.put(ItemAttributes.AREA_CODIGO, s(areaCodigo));
        item.put(ItemAttributes.CARGO_CODIGO, s(cargoCodigo));
        item.put(ItemAttributes.NOMBRE, s(rol.nombre()));
        item.put(ItemAttributes.DESCRIPCION, s(rol.descripcion()));
        item.put(ItemAttributes.ROL_TIPO, s(rol.tipo()));
        item.put(ItemAttributes.REGLA_NEGOCIO, s(rol.reglaNegocio()));
        item.put(ItemAttributes.AMBITO, s(rol.ambito()));
        item.put(ItemAttributes.PROPIETARIO_ROL, s(rol.propietarioRol()));
        item.put(ItemAttributes.PROPIETARIO_PERSONA, s(rol.propietarioPersona()));
        item.put(ItemAttributes.SITUACION, s(null));
        item.put(ItemAttributes.ROL_BASE, s(null));
        return item;
    }

    private static Map<String, AttributeValue> suministroItem(Suministro suministro) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(ItemAttributes.PK, s(RoleRepositoryKeys.rolPk(String.valueOf(suministro.idRol()))));
        item.put(ItemAttributes.SK, s(RoleRepositoryKeys.suministroSk(String.valueOf(suministro.id()))));
        item.put(ItemAttributes.ID, s(String.valueOf(suministro.id())));
        item.put(ItemAttributes.ROL_ID, s(String.valueOf(suministro.idRol())));
        item.put(ItemAttributes.NOMBRE, s(suministro.nombre()));
        item.put(ItemAttributes.PRIORIDAD, s(suministro.prioridad()));
        return item;
    }

    private static Map<String, AttributeValue> atributoItem(
            Atributo atributo, String aplicacionCodigo, String areaCodigo, String cargoCodigo, String cargoNombre,
            String rolTipo, String rolNombre) {
        String idAtributo = String.valueOf(atributo.id());
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(ItemAttributes.PK, s(RoleRepositoryKeys.atributoPk(idAtributo)));
        // idDetalleAtributo == el propio atributo.id, ver Javadoc de la clase.
        item.put(ItemAttributes.SK, s(RoleRepositoryKeys.detalleSk(idAtributo, atributo.parametro())));
        item.put(ItemAttributes.GSI1_PK, s(RoleRepositoryKeys.gsi1Pk(aplicacionCodigo, areaCodigo)));
        item.put(ItemAttributes.GSI1_SK, s(RoleRepositoryKeys.gsi1Sk(cargoCodigo, String.valueOf(atributo.idRol()))));
        item.put(ItemAttributes.ID, s(idAtributo));
        item.put(ItemAttributes.DETALLE_ID, s(idAtributo));
        item.put(ItemAttributes.CATEGORIA, s(atributo.parametro()));
        item.put(ItemAttributes.APLICACION_CODIGO, s(aplicacionCodigo));
        item.put(ItemAttributes.AREA_CODIGO, s(areaCodigo));
        item.put(ItemAttributes.CARGO_CODIGO, s(cargoCodigo));
        item.put(ItemAttributes.CARGO_NOMBRE, s(cargoNombre));
        item.put(ItemAttributes.ROL_ID, s(String.valueOf(atributo.idRol())));
        item.put(ItemAttributes.ROL_TIPO, s(rolTipo));
        item.put(ItemAttributes.ROL_NOMBRE, s(rolNombre));
        item.put(ItemAttributes.SUMINISTRO_ID, s(String.valueOf(atributo.idSuministro())));
        item.put(ItemAttributes.ATRIBUTO_CODIGO, s(atributo.atributoCodigo()));
        item.put(ItemAttributes.ATRIBUTO_NOMBRE, s(atributo.atributoNombre()));
        item.put(ItemAttributes.ESTADO, s(DEFAULT_ESTADO));
        // Atributos extra sin equivalente en el backend real, mantenidos
        // solo por VerifyCommand -- ver Javadoc de ItemAttributes.
        item.put(ItemAttributes.ROL_CARGO_ID, s(cargoCodigo));
        item.put(ItemAttributes.DETALLE_PARAMETRO, s(atributo.parametro()));
        return item;
    }

    /**
     * Construye un indice nombre-&gt;fila a partir de una lista de filas de
     * catalogo. Si dos filas comparten el mismo nombre (ambiguedad real
     * encontrada en {@code cargo.nombre}), se queda con la fila de id
     * numerico MENOR -- regla determinista y estable entre corridas (no
     * depende del orden de iteracion de la lista de entrada).
     */
    private static <T> Map<String, T> indexByNameMinId(
            List<T> rows, java.util.function.Function<T, String> nameFn, java.util.function.Function<T, Long> idFn) {
        Map<String, T> index = new HashMap<>();
        for (T row : rows) {
            String name = nameFn.apply(row);
            index.merge(name, row,
                    (existing, candidate) -> idFn.apply(candidate) < idFn.apply(existing) ? candidate : existing);
        }
        return index;
    }

    private static <T> String resolveOrFallback(
            Map<String, T> nameToRow, String name, java.util.function.Function<T, String> codigoFn) {
        T row = name == null ? null : nameToRow.get(name);
        if (row != null) {
            return codigoFn.apply(row);
        }
        return UNRESOLVED_NAME_PREFIX + (name == null ? "" : name);
    }

    private static boolean isUnresolved(String codigoOrFallback) {
        return codigoOrFallback.startsWith(UNRESOLVED_NAME_PREFIX);
    }

    private static AttributeValue s(String value) {
        return AttributeValue.fromS(value == null ? "" : value);
    }
}
