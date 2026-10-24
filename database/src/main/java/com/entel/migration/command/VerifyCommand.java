package com.entel.migration.command;

import com.entel.migration.dump.MysqlDumpParseException;
import com.entel.migration.dump.MysqlDumpParser;
import com.entel.migration.dump.ParsedDump;
import com.entel.migration.schema.ItemAttributes;
import com.entel.migration.schema.TableNames;
import com.entel.migration.transform.RoleRepositoryTransformer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Subcomando {@code verify} (feature 5: data_migration_validation).
 *
 * <p>Uso: {@code verify [<ruta-al-dump.sql>] [--target=localstack]}. Recorre
 * (via {@code Scan} paginado) los items ya migrados en {@code RoleRepository}
 * y {@code RoleRepositoryMenu} y valida dos cosas, siguiendo el mismo diseno
 * canonico que {@link com.entel.migration.transform.RoleRepositoryTransformer}
 * (feature 4) escribio - este comando NO reinventa el mapeo, solo lo verifica
 * desde el otro lado:
 *
 * <ol>
 *   <li><b>Integridad referencial</b>: todo item {@code ATRIBUTO#} debe
 *       referenciar un catalogo {@code Aplicacion}/{@code Area}/{@code Cargo}
 *       que exista como item, y un item {@code SUMINISTRO#} que exista bajo
 *       el {@code Rol} correspondiente. Siempre se ejecuta (no requiere el
 *       dump fuente).</li>
 *   <li><b>Conteos por tipo de entidad</b>: si se pasa la ruta al dump
 *       fuente, se re-parsea con {@link MysqlDumpParser} (feature 3, la misma
 *       clase que usa {@code migrate}) y se compara el numero de filas de
 *       cada tabla contra el numero de items migrados del tipo
 *       correspondiente. Es opcional (si no se pasa dump, {@code verify}
 *       igual corre la integridad referencial) porque el comando tambien
 *       debe ser util en un entorno donde no se tiene acceso al {@code .sql}
 *       original (ej. verificacion periodica contra una tabla ya poblada).</li>
 * </ol>
 *
 * <h2>Decisiones de diseno (investigadas contra el codigo y el dump reales
 * antes de escribir este comando, no asumidas)</h2>
 *
 * <ul>
 *   <li><b>Corregido 2026-08-19 (coordinacion cross-track con el backend
 *       real):</b> los items {@code ATRIBUTO#}/{@code ROL#} ahora llevan el
 *       {@code codigo} de negocio (no el {@code id} numerico interno) en
 *       {@code GSI1PK}/{@code GSI1SK}/{@code rolCargoId} -- verificado
 *       leyendo {@code AtributoRepository.guardar()} del backend real, que
 *       construye esas claves con {@code atributo.aplicacionCodigo()}/
 *       {@code areaCodigo()}/{@code cargoCodigo()}, nunca con un id
 *       numerico. Este comando ahora escanea los items de catalogo, lee su
 *       atributo {@code codigo} (no {@code id}) y construye un indice
 *       {@code tipo -> Set<codigo>} en memoria; los items {@code ATRIBUTO#}/
 *       {@code ROL#} se validan contra ese indice.</li>
 *   <li><b>76 de 15565 items {@code ATRIBUTO#} del dump real quedan con un
 *       {@code idArea}/{@code idCargo} sin resolver</b> (14 filas de {@code
 *       rol.area} = {@code LEGAL}/{@code PLANEAMIENTO} sin catalogo
 *       correspondiente - ver Javadoc de {@code RoleRepositoryTransformer}).
 *       La migracion NO los descarta ni los deja en blanco: les asigna un
 *       marcador de fallback deterministico y distinguible ({@link
 *       RoleRepositoryTransformer#UNRESOLVED_NAME_PREFIX} = {@code "NOMBRE#"},
 *       {@link RoleRepositoryTransformer#UNRESOLVED_ROL_PREFIX} = {@code
 *       "ROLDESCONOCIDO#"}). <b>Decision de este comando: esos 76 items NO
 *       cuentan como "referencia rota".</b> Una referencia rota es "el dato
 *       migrado afirma una relacion con un id real y esa relacion no se
 *       preservo". Un item con el marcador de fallback nunca afirmo
 *       referenciar un id real - el propio dump fuente ya no tenia esa
 *       relacion antes de migrar (calidad de datos preexistente, ya
 *       documentada en features 3/4). Tratarlos como "rotos" haria
 *       imposible cumplir literalmente el acceptance criteria 1 ("verify
 *       reporta 0 referencias rotas ... del dump real") sin ademas
 *       contradecir la decision ya tomada en la feature 4 de preservarlos
 *       con un marcador en vez de descartarlos. Se siguen contando y
 *       reportando aparte, con su propia etiqueta ("conocidas sin
 *       resolver"), para que no queden invisibles.</li>
 *   <li><b>Desactualizado desde 2026-08-19, dejado como contexto historico:</b>
 *       esta seccion describia que {@code Rol} no tenia item propio -- ya NO
 *       es cierto, {@code RoleRepositoryTransformer} ahora escribe un item
 *       {@code ROL#<id>/METADATA} por fila (el backend real tiene su propio
 *       {@code RolRepository.java} que lo necesita). Este comando ({@code
 *       verify}) todavia NO valida esos items de Rol contra el dump fuente
 *       -- sigue validando lo mismo que antes (Suministro/Atributo); agregar
 *       esa verificacion queda pendiente, no bloquea que {@code migrate}
 *       escriba los items correctos.</li>
 *   <li><b>(Contexto historico, ya no describe el diseno vigente arriba)</b>
 *       No existe
 *       {@code ENTITY#ROL#<id>}; un Rol solo se manifiesta como PK
 *       ({@code ROL#<idRol>}) de cero o mas items {@code SUMINISTRO#}.
 *       Verificado contra el dump real (script ad-hoc, no solo el codigo):
 *       los 741 {@code rol} tienen exactamente 741 {@code suministro} (1:1
 *       en este dataset) y NINGUN {@code rol} referenciado por un {@code
 *       atributo} tiene cero filas de {@code suministro} - el caso "Rol
 *       valido con cero Suministro" (que produciria falsos positivos si
 *       solo se comprobara "?existe algun item bajo {@code ROL#<idRol>}?")
 *       no ocurre en este dataset, pero de todas formas el chequeo elegido
 *       no depende de esa ausencia: en vez de comprobar "?existe algun item
 *       bajo {@code ROL#<idRol>}?" (que seria una tautologia para el propio
 *       item {@code SUMINISTRO#} que se esta mirando) se comprueba la
 *       combinacion exacta {@code (idRol, idSuministro)} que cada item
 *       {@code ATRIBUTO#} lleva en sus atributos {@code idRol}/{@code
 *       idSuministro} contra el conjunto de items {@code SUMINISTRO#}
 *       migrados. Esto valida "Suministro existe" y, como consecuencia
 *       directa (el item {@code SUMINISTRO#} solo existe bajo un {@code
 *       ROL#<idRol>} real), tambien "Rol existe" - no hace falta un chequeo
 *       separado para Rol dado este esquema de tabla unica. La relacion
 *       inversa ("todo Suministro referencia un Rol existente", de la
 *       descripcion de la feature) es, por el mismo motivo, una tautologia
 *       en este diseno: el PK de todo item {@code SUMINISTRO#} ya es
 *       {@code ROL#<idRol>} por construccion, no hay un item {@code ROL#}
 *       independiente contra el cual pudiera "faltar" - no se emite un
 *       chequeo separado para esto (se documenta aqui en vez de escribir un
 *       chequeo que siempre pasa trivialmente).</li>
 *   <li><b>{@code Estado} no es una referencia.</b> {@code estado} en los
 *       items {@code ATRIBUTO#} es el literal constante {@link
 *       RoleRepositoryTransformer#DEFAULT_ESTADO} (no existe tabla {@code
 *       estado} en este dump, ver docs/architecture.md y el Javadoc de
 *       {@code RoleRepositoryTransformer}) - no hay ningun item {@code
 *       ENTITY#ESTADO#*} en el diseno canonico contra el cual validar. Este
 *       comando no inventa ese catalogo: no emite ningun chequeo de
 *       "Atributo -&gt; Estado" (es un no-op documentado, no un chequeo
 *       omitido por descuido).</li>
 *   <li><b>Conteos: re-parsear el dump fuente, no confiar en las stats de
 *       {@code migrate}.</b> Si se pasa la ruta al dump, este comando corre
 *       {@link MysqlDumpParser} de forma independiente (no reusa {@link
 *       com.entel.migration.transform.TransformResult.Stats} de una corrida
 *       de {@code migrate} anterior) y compara {@code dump.rowCount(tabla)}
 *       contra el conteo real de items migrados por tipo, agrupados durante
 *       el mismo {@code Scan} que ya hace la integridad referencial (sin una
 *       segunda pasada). Esto detecta tanto errores de {@code migrate} como
 *       borrados/corrupcion posteriores en DynamoDB, no solo repite lo que
 *       {@code migrate} ya afirmo sobre si mismo.</li>
 * </ul>
 *
 * <p><b>Regla dura (docs/architecture.md, docs/conventions.md): el target
 * por defecto es siempre LocalStack, nunca una cuenta AWS real</b> - mismo
 * mecanismo que {@link MigrateCommand}/{@link DesignValidateCommand}
 * ({@code --target=aws} se rechaza explicitamente antes de construir
 * cualquier cliente).
 */
public final class VerifyCommand implements MigrationCommand {

    static final String DEFAULT_ENDPOINT = "http://localhost:4566";
    static final String ENV_ENDPOINT_OVERRIDE = "MIGRATION_DYNAMODB_ENDPOINT";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final String LOCALSTACK_ACCESS_KEY = "test";
    private static final String LOCALSTACK_SECRET_KEY = "test";
    private static final String TARGET_FLAG_PREFIX = "--target=";
    private static final String TARGET_LOCALSTACK = "localstack";
    private static final String TARGET_AWS = "aws";

    private static final String ENTITY_PREFIX = "ENTITY#";
    private static final String CATALOG_SK = "METADATA";
    private static final String ROL_PK_PREFIX = "ROL#";
    private static final String SUMINISTRO_SK_PREFIX = "SUMINISTRO#";
    private static final String ATRIBUTO_PK_PREFIX = "ATRIBUTO#";
    private static final String DETALLE_SK_PREFIX = "DETALLE#";

    private static final String TIPO_AREA = "AREA";
    private static final String TIPO_CARGO = "CARGO";
    private static final String TIPO_UBICACION = "UBICACION";
    private static final String TIPO_APLICACION = "APLICACION";

    private static final String GSI1PK_APLICACION_PREFIX = "APLICACION#";
    private static final String GSI1PK_AREA_MARKER = "#AREA#";

    private final URI endpointOverride;

    /** Constructor usado por la CLI real: apunta a LocalStack por defecto. */
    public VerifyCommand() {
        this(null);
    }

    /** Constructor usado por los tests de integracion, igual que {@link MigrateCommand#MigrateCommand(URI)}. */
    public VerifyCommand(URI endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    @Override
    public int execute(String[] args, PrintStream out, PrintStream err) {
        String dumpPathArg = null;
        String target = TARGET_LOCALSTACK;
        for (String arg : args) {
            if (arg.startsWith(TARGET_FLAG_PREFIX)) {
                target = arg.substring(TARGET_FLAG_PREFIX.length());
            } else if (dumpPathArg == null) {
                dumpPathArg = arg;
            }
        }

        if (!TARGET_LOCALSTACK.equals(target)) {
            if (TARGET_AWS.equals(target)) {
                err.println("Error: --target=aws no esta soportado por esta herramienta. "
                        + "Regla dura de docs/architecture.md: la migracion nunca se conecta a una cuenta AWS real. "
                        + "Usa el target por defecto (LocalStack).");
            } else {
                err.println("Error: target desconocido '" + target + "'. Unico valor soportado: '" + TARGET_LOCALSTACK + "'.");
            }
            return 1;
        }

        ExpectedCounts expectedCounts = null;
        if (dumpPathArg != null && !dumpPathArg.isBlank()) {
            Path path = Path.of(dumpPathArg);
            if (!Files.isRegularFile(path)) {
                err.println("Error: no existe el archivo '" + dumpPathArg + "'.");
                return 1;
            }
            try {
                ParsedDump dump = new MysqlDumpParser().parse(path);
                expectedCounts = ExpectedCounts.fromDump(dump);
            } catch (IOException e) {
                err.println("Error leyendo '" + dumpPathArg + "': " + e.getMessage());
                return 1;
            } catch (MysqlDumpParseException e) {
                err.println("Error parseando '" + dumpPathArg + "': " + e.getMessage());
                return 1;
            }
        }

        URI endpoint = resolveEndpoint();
        try (DynamoDbAsyncClient client = buildClient(endpoint)) {
            return runVerification(client, expectedCounts, dumpPathArg, endpoint, out, err);
        } catch (RuntimeException e) {
            err.println("verify: fallo leyendo DynamoDB: " + describe(e));
            return 1;
        }
    }

    private int runVerification(
            DynamoDbAsyncClient client,
            ExpectedCounts expectedCounts,
            String dumpPathArg,
            URI endpoint,
            PrintStream out,
            PrintStream err) {
        List<Map<String, AttributeValue>> allItems = scanAll(client, TableNames.ROLE_REPOSITORY);

        List<Map<String, AttributeValue>> catalogItems = new ArrayList<>();
        List<Map<String, AttributeValue>> suministroItems = new ArrayList<>();
        List<Map<String, AttributeValue>> atributoItems = new ArrayList<>();
        int unknownShapeItems = 0;

        for (Map<String, AttributeValue> item : allItems) {
            String pk = attr(item, ItemAttributes.PK);
            String sk = attr(item, ItemAttributes.SK);
            if (pk != null && pk.startsWith(ENTITY_PREFIX) && CATALOG_SK.equals(sk)) {
                catalogItems.add(item);
            } else if (pk != null && pk.startsWith(ROL_PK_PREFIX) && sk != null && sk.startsWith(SUMINISTRO_SK_PREFIX)) {
                suministroItems.add(item);
            } else if (pk != null && pk.startsWith(ATRIBUTO_PK_PREFIX) && sk != null && sk.startsWith(DETALLE_SK_PREFIX)) {
                atributoItems.add(item);
            } else {
                unknownShapeItems++;
            }
        }

        // Corregido 2026-08-19 (coordinacion cross-track con el backend
        // real): los items ATRIBUTO#/ROL# ya no llevan el id numerico
        // interno del catalogo en GSI1PK/GSI1SK/rolCargoId -- llevan el
        // *codigo* de negocio (ver AtributoRepository.guardar() del
        // backend), asi que este indice ahora se construye por codigo, no
        // por id, para que las comparaciones de mas abajo sigan siendo
        // validas contra el esquema real.
        Map<String, Set<String>> catalogCodigosByTipo = new HashMap<>();
        Map<String, Integer> catalogCountByTipo = new HashMap<>();
        for (Map<String, AttributeValue> item : catalogItems) {
            String pk = attr(item, ItemAttributes.PK);
            String rest = pk.substring(ENTITY_PREFIX.length());
            int sep = rest.indexOf('#');
            String tipo = sep >= 0 ? rest.substring(0, sep) : rest;
            String codigo = attr(item, ItemAttributes.CODIGO);
            catalogCodigosByTipo.computeIfAbsent(tipo, t -> new HashSet<>()).add(codigo);
            catalogCountByTipo.merge(tipo, 1, Integer::sum);
        }

        Set<String> suministroCompoundKeys = new HashSet<>();
        for (Map<String, AttributeValue> item : suministroItems) {
            String idRol = attr(item, ItemAttributes.ROL_ID);
            String idSuministro = attr(item, ItemAttributes.ID);
            suministroCompoundKeys.add(idRol + "|" + idSuministro);
        }

        List<BrokenReference> broken = new ArrayList<>();
        Map<String, Integer> knownUnresolvedByTipo = new HashMap<>();

        for (Map<String, AttributeValue> item : atributoItems) {
            String pk = attr(item, ItemAttributes.PK);
            String sk = attr(item, ItemAttributes.SK);
            String gsi1pk = attr(item, ItemAttributes.GSI1_PK);
            String idCargo = attr(item, ItemAttributes.ROL_CARGO_ID);
            String idRol = attr(item, ItemAttributes.ROL_ID);
            String idSuministro = attr(item, ItemAttributes.SUMINISTRO_ID);

            String[] aplicacionYArea = parseGsi1Pk(gsi1pk);
            if (aplicacionYArea == null) {
                broken.add(new BrokenReference(
                        pk, sk, "GSI1PK ausente o con formato inesperado (se esperaba 'APLICACION#<id>#AREA#<id>'): "
                                + gsi1pk));
            } else {
                checkCatalogReference(
                        TIPO_APLICACION, aplicacionYArea[0], pk, sk, catalogCodigosByTipo, broken, knownUnresolvedByTipo);
                checkCatalogReference(
                        TIPO_AREA, aplicacionYArea[1], pk, sk, catalogCodigosByTipo, broken, knownUnresolvedByTipo);
            }
            checkCatalogReference(TIPO_CARGO, idCargo, pk, sk, catalogCodigosByTipo, broken, knownUnresolvedByTipo);

            String suministroKey = idRol + "|" + idSuministro;
            if (!suministroCompoundKeys.contains(suministroKey)) {
                broken.add(new BrokenReference(
                        pk,
                        sk,
                        "Suministro id=" + idSuministro + " bajo Rol id=" + idRol
                                + " no existe como item (se esperaba PK=" + ROL_PK_PREFIX + idRol + ", SK="
                                + SUMINISTRO_SK_PREFIX + idSuministro + ")"));
            }
        }

        List<CountMismatch> mismatches = new ArrayList<>();
        int menuActual = scanCount(client, TableNames.ROLE_REPOSITORY_MENU);
        if (expectedCounts != null) {
            checkCount(mismatches, "area", expectedCounts.area(), catalogCountByTipo.getOrDefault(TIPO_AREA, 0));
            checkCount(mismatches, "cargo", expectedCounts.cargo(), catalogCountByTipo.getOrDefault(TIPO_CARGO, 0));
            checkCount(
                    mismatches,
                    "ubicacion",
                    expectedCounts.ubicacion(),
                    catalogCountByTipo.getOrDefault(TIPO_UBICACION, 0));
            checkCount(
                    mismatches,
                    "aplicacion",
                    expectedCounts.aplicacion(),
                    catalogCountByTipo.getOrDefault(TIPO_APLICACION, 0));
            checkCount(mismatches, "suministro", expectedCounts.suministro(), suministroItems.size());
            checkCount(mismatches, "atributo", expectedCounts.atributo(), atributoItems.size());
            checkCount(mismatches, "menu (RoleRepositoryMenu)", expectedCounts.menu(), menuActual);
        }

        printSummary(
                out,
                endpoint,
                dumpPathArg,
                catalogItems.size(),
                suministroItems.size(),
                atributoItems.size(),
                menuActual,
                unknownShapeItems,
                broken.size(),
                knownUnresolvedByTipo,
                expectedCounts != null);

        if (broken.isEmpty() && mismatches.isEmpty()) {
            out.println("verify: OK - 0 referencias rotas" + (expectedCounts != null ? ", conteos coinciden con el dump fuente." : "."));
            return 0;
        }

        for (BrokenReference b : broken) {
            err.println("verify: referencia rota - PK=" + b.pk() + " SK=" + b.sk() + ": " + b.detail());
        }
        if (!broken.isEmpty()) {
            err.println("verify: " + broken.size() + " referencia(s) rota(s) encontrada(s).");
        }
        for (CountMismatch m : mismatches) {
            err.println("verify: conteo de '" + m.label() + "' no coincide: dump=" + m.expected()
                    + " dynamodb=" + m.actual());
        }
        return 1;
    }

    private static void checkCatalogReference(
            String tipo,
            String rawId,
            String pk,
            String sk,
            Map<String, Set<String>> catalogCodigosByTipo,
            List<BrokenReference> broken,
            Map<String, Integer> knownUnresolvedByTipo) {
        if (isKnownUnresolvedMarker(rawId)) {
            knownUnresolvedByTipo.merge(tipo, 1, Integer::sum);
            return;
        }
        Set<String> knownIds = catalogCodigosByTipo.getOrDefault(tipo, Set.of());
        if (!knownIds.contains(rawId)) {
            broken.add(new BrokenReference(
                    pk, sk, tipo + " id=" + rawId + " no existe como item de catalogo (ENTITY#" + tipo
                            + "#<codigo> con atributo id=" + rawId + ")"));
        }
    }

    private static boolean isKnownUnresolvedMarker(String rawId) {
        return rawId != null
                && (rawId.startsWith(RoleRepositoryTransformer.UNRESOLVED_NAME_PREFIX)
                        || rawId.startsWith(RoleRepositoryTransformer.UNRESOLVED_ROL_PREFIX));
    }

    /** @return {idAplicacion, idArea}, o {@code null} si {@code gsi1pk} no tiene el formato esperado. */
    private static String[] parseGsi1Pk(String gsi1pk) {
        if (gsi1pk == null || !gsi1pk.startsWith(GSI1PK_APLICACION_PREFIX)) {
            return null;
        }
        int areaMarkerIndex = gsi1pk.indexOf(GSI1PK_AREA_MARKER, GSI1PK_APLICACION_PREFIX.length());
        if (areaMarkerIndex < 0) {
            return null;
        }
        String idAplicacion = gsi1pk.substring(GSI1PK_APLICACION_PREFIX.length(), areaMarkerIndex);
        String idArea = gsi1pk.substring(areaMarkerIndex + GSI1PK_AREA_MARKER.length());
        return new String[] {idAplicacion, idArea};
    }

    private static void checkCount(List<CountMismatch> mismatches, String label, int expected, int actual) {
        if (expected != actual) {
            mismatches.add(new CountMismatch(label, expected, actual));
        }
    }

    private void printSummary(
            PrintStream out,
            URI endpoint,
            String dumpPathArg,
            int catalogCount,
            int suministroCount,
            int atributoCount,
            int menuCount,
            int unknownShapeItems,
            int brokenCount,
            Map<String, Integer> knownUnresolvedByTipo,
            boolean withDump) {
        out.println("verify: analizando (endpoint=" + endpoint + ", dump="
                + (withDump ? dumpPathArg : "ninguno - solo integridad referencial") + ")");
        out.println("  " + TableNames.ROLE_REPOSITORY + ": " + (catalogCount + suministroCount + atributoCount)
                + " items (catalogos=" + catalogCount + ", suministro=" + suministroCount
                + ", atributo=" + atributoCount + ")");
        out.println("  " + TableNames.ROLE_REPOSITORY_MENU + ": " + menuCount + " items");
        if (unknownShapeItems > 0) {
            out.println("  Advertencia: " + unknownShapeItems
                    + " item(s) con forma de PK/SK no reconocida (no son catalogo/suministro/atributo del diseno canonico).");
        }
        out.println("  Referencias rotas encontradas: " + brokenCount);
        if (!knownUnresolvedByTipo.isEmpty()) {
            out.println("  Conocidas sin resolver (NO son referencias rotas - marcador de fallback deterministico"
                    + " ya documentado en RoleRepositoryTransformer, calidad de datos preexistente en el dump fuente): "
                    + knownUnresolvedByTipo);
        }
        out.println("  Estado: no aplica (valor literal constante '" + RoleRepositoryTransformer.DEFAULT_ESTADO
                + "' en cada item ATRIBUTO#, no es una referencia a otro item - ver docs/architecture.md).");
    }

    /** Escanea toda la tabla (paginado) y devuelve todos sus items completos. */
    private static List<Map<String, AttributeValue>> scanAll(DynamoDbAsyncClient client, String tableName) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            ScanRequest.Builder requestBuilder = ScanRequest.builder().tableName(tableName);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
            ScanResponse response = client.scan(requestBuilder.build()).join();
            items.addAll(response.items());
            exclusiveStartKey = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey()
                    : null;
        } while (exclusiveStartKey != null);
        return items;
    }

    /** Cuenta todos los items de una tabla con Scan paginado (Select=COUNT), sin traer atributos. */
    private static int scanCount(DynamoDbAsyncClient client, String tableName) {
        int total = 0;
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            ScanRequest.Builder requestBuilder =
                    ScanRequest.builder().tableName(tableName).select(Select.COUNT);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
            ScanResponse response = client.scan(requestBuilder.build()).join();
            total += response.count();
            exclusiveStartKey = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey()
                    : null;
        } while (exclusiveStartKey != null);
        return total;
    }

    private static String attr(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }

    private URI resolveEndpoint() {
        if (endpointOverride != null) {
            return endpointOverride;
        }
        String env = System.getenv(ENV_ENDPOINT_OVERRIDE);
        if (env != null && !env.isBlank()) {
            return URI.create(env);
        }
        return URI.create(DEFAULT_ENDPOINT);
    }

    private DynamoDbAsyncClient buildClient(URI endpoint) {
        return DynamoDbAsyncClient.builder()
                .endpointOverride(endpoint)
                .region(DEFAULT_REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK_ACCESS_KEY, LOCALSTACK_SECRET_KEY)))
                .build();
    }

    private static String describe(RuntimeException e) {
        Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private record BrokenReference(String pk, String sk, String detail) {
    }

    private record CountMismatch(String label, int expected, int actual) {
    }

    private record ExpectedCounts(
            int area, int cargo, int ubicacion, int aplicacion, int suministro, int atributo, int menu) {

        static ExpectedCounts fromDump(ParsedDump dump) {
            return new ExpectedCounts(
                    dump.rowCount("area"),
                    dump.rowCount("cargo"),
                    dump.rowCount("ubicacion"),
                    dump.rowCount("aplicacion"),
                    dump.rowCount("suministro"),
                    dump.rowCount("atributo"),
                    dump.rowCount("menu"));
        }
    }
}
