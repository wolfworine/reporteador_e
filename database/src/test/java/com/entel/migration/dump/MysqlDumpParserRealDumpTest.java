package com.entel.migration.dump;

import com.entel.migration.dump.model.Aplicacion;
import com.entel.migration.dump.model.Atributo;
import com.entel.migration.dump.model.Login;
import com.entel.migration.dump.model.Rol;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Corre el parser contra el dump MySQL real de referencia
 * (repositorio_entel/reposrito_roles_jsf 20150814 1214.sql) y verifica los
 * conteos de filas por tabla.
 *
 * <p><b>Sobre los conteos esperados:</b> el brief original de esta feature
 * traia una tabla de conteos "ya verificados por el lider con grep directo
 * sobre el archivo, contando tuplas VALUES no solo sentencias INSERT"
 * (aplicacion=1, area=1, atributo=22, cargo=1, detalle=1, login=1, rol=3,
 * sequence=1, suministro=1, ubicacion=1, usuario=1, detalleinbox=0). Al
 * implementar el parser, esos numeros NO coincidieron con la salida real.
 * Investigando la causa (en vez de ajustar este test a ciegas para que
 * pasara): esos numeros son en realidad el numero de SENTENCIAS
 * {@code INSERT INTO} por tabla, no el numero de tuplas/filas - la mayoria
 * de tablas de este dump usan INSERT multi-row (una sola sentencia con
 * muchas tuplas separadas por coma), asi que ambos conteos difieren mucho
 * salvo en las tablas donde by coincidencia hay exactamente una tupla por
 * sentencia ({@code aplicacion}, {@code sequence}) o cero
 * ({@code detalleinbox}).
 *
 * <p>Los conteos de abajo fueron verificados de forma independiente con dos
 * metodos distintos sobre el archivo original (ver progress/current.md /
 * progress/history.md para el detalle completo):
 * <ol>
 *   <li>{@code grep -c "^ ("} sobre el archivo completo (cada tupla del
 *       dump real esta en su propia linea fisica) = 18566 lineas de tupla
 *       en total.</li>
 *   <li>Una pasada que asigna cada linea de tupla a la tabla mas reciente
 *       vista en el comentario {@code -- Dumping data for table `X`} (que
 *       aparece una vez por tabla, justo antes de su bloque de datos) - la
 *       suma por tabla coincide exactamente con el total de (1).</li>
 * </ol>
 */
class MysqlDumpParserRealDumpTest {

    private static final Path REAL_DUMP_PATH =
            Path.of("D:\\Proyecto textil\\repositorio_entel\\reposrito_roles_jsf 20150814 1214.sql");

    /** Conteos reales de filas, verificados de forma independiente (ver Javadoc de la clase). */
    private static final Map<String, Integer> EXPECTED_ROW_COUNTS = Map.ofEntries(
            Map.entry("aplicacion", 1),
            Map.entry("area", 69),
            Map.entry("atributo", 15565),
            Map.entry("cargo", 695),
            Map.entry("detalle", 684),
            Map.entry("detalleinbox", 0),
            Map.entry("login", 4),
            Map.entry("rol", 741),
            Map.entry("sequence", 1),
            Map.entry("suministro", 741),
            Map.entry("ubicacion", 61),
            Map.entry("usuario", 4));

    private static ParsedDump dump;

    @BeforeAll
    static void parseRealDumpOnce() throws IOException {
        assumeTrue(
                Files.isRegularFile(REAL_DUMP_PATH),
                () -> "El dump real de referencia no esta presente en " + REAL_DUMP_PATH
                        + " (vive fuera de este repo, en repositorio_entel/) - se omite este test.");
        dump = new MysqlDumpParser().parse(REAL_DUMP_PATH);
    }

    @Test
    void reconoceExactamenteLasDoceTablasDelDumpReal() {
        assertEquals(EXPECTED_ROW_COUNTS.keySet(), java.util.Set.copyOf(dump.tableNames()));
    }

    @Test
    void elConteoDeFilasPorTablaCoincideConLosNumerosVerificadosDeFormaIndependiente() {
        for (Map.Entry<String, Integer> expected : EXPECTED_ROW_COUNTS.entrySet()) {
            assertEquals(
                    expected.getValue(),
                    dump.rowCount(expected.getKey()),
                    "conteo de filas incorrecto para la tabla '" + expected.getKey() + "'");
        }
    }

    @Test
    void elTotalDeFilasEsLaSumaDeLosConteosPorTabla() {
        int expectedTotal = EXPECTED_ROW_COUNTS.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedTotal, dump.totalRowCount());
    }

    @Test
    void mapeaLaPrimeraFilaDeAplicacionATipadoCorrectamente() {
        Aplicacion aplicacion = Aplicacion.fromRow(dump.rows("aplicacion").get(0));
        assertEquals(1L, aplicacion.id());
        assertEquals("1", aplicacion.codigo());
        assertEquals("PORTAL", aplicacion.aplicacion());
    }

    @Test
    void mapeaLaPrimeraFilaDeLoginATipadoCorrectamente() {
        Login login = Login.fromRow(dump.rows("login").get(0));
        assertEquals(1L, login.id());
        assertEquals("admin@wisevisioncorp.com", login.email());
        assertEquals("admin", login.password());
    }

    @Test
    void mapeaLaPrimeraFilaDeRolATipadoCorrectamente() {
        Rol rol = Rol.fromRow(dump.rows("rol").get(0));
        assertEquals(2000L, rol.id());
        assertEquals("SUBUNIDAD", rol.ambito());
        assertEquals("ARQUITECTURA EMPRESARIAL", rol.area());
        assertEquals("GERENTE DE ARQUITECTURA EMPRESARIAL", rol.cargo());
        assertEquals("(&(UNIDAD=GA0001)(CARGO=PU0019))", rol.reglaNegocio());
        assertEquals("DINAMICO", rol.tipo());
        assertEquals("PR0015", rol.ubicacion());
    }

    @Test
    void mapeaLaPrimeraFilaDeAtributoATipadoCorrectamente() {
        Atributo atributo = Atributo.fromRow(dump.rows("atributo").get(0));
        assertEquals(41868L, atributo.id());
        assertEquals("PORTAL", atributo.aplicacion());
        assertEquals("36|36|351", atributo.atributoCodigo());
        assertEquals(2000L, atributo.idRol());
        assertEquals(1899L, atributo.idSuministro());
        assertEquals("ROLES", atributo.parametro());
    }

    @Test
    void detalleinboxEstaRegistradaConCeroFilasNoSeOmite() {
        assumeTrue(dump != null);
        assertEquals(0, dump.rowCount("detalleinbox"));
        List<ParsedRow> rows = dump.rows("detalleinbox");
        assertEquals(List.of(), rows);
    }
}
