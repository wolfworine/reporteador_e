-- MySQL Administrator dump 1.4 (fixture reducido, escrito a mano para tests
-- de la feature 3 - parse_legacy_mysql_dump. Imita el formato real de
-- repositorio_entel/reposrito_roles_jsf 20150814 1214.sql: comentarios de
-- linea, comentarios condicionales /*!...*/, DROP TABLE IF EXISTS, CREATE
-- TABLE, e INSERT INTO multi-row.
--
-- Deliberadamente incluye:
--  - `aplicacion`: tabla simple de 3 columnas, 1 fila (igual forma que el
--    dump real).
--  - `rol`: prueba escape de comilla simple (\') y un valor con parentesis
--    anidados dentro de un string citado (para validar que el conteo de
--    profundidad de parentesis del parser no se confunde con contenido
--    citado), igual que `rol.regla_negocio` en el dump real.
--  - `estado`: NO existe en el dump real de referencia, pero se usa aqui
--    para probar el manejo explicito del literal NULL sin comillas (el
--    dump real no trae ningun NULL en sus datos, asi que este fixture es
--    la unica forma de probarlo con datos representativos del formato).
--  - `detalleinbox`: solo CREATE TABLE, sin INSERT (0 filas) - igual que
--    en el dump real, para probar que una tabla vista sin filas se
--    reporta con conteo 0, no se omite.

/*!40101 SET NAMES utf8 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;

CREATE DATABASE IF NOT EXISTS repositorio_roles_jsf_fixture;
USE repositorio_roles_jsf_fixture;

--
-- Definition of table `aplicacion`
--

DROP TABLE IF EXISTS `aplicacion`;
CREATE TABLE `aplicacion` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `codigo` varchar(20) NOT NULL,
  `aplicacion` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;

--
-- Dumping data for table `aplicacion`
--

/*!40000 ALTER TABLE `aplicacion` DISABLE KEYS */;
INSERT INTO `aplicacion` (`id`,`codigo`,`aplicacion`) VALUES
 (1,'1','PORTAL');
/*!40000 ALTER TABLE `aplicacion` ENABLE KEYS */;

--
-- Definition of table `rol`
--

DROP TABLE IF EXISTS `rol`;
CREATE TABLE `rol` (
  `id` bigint(20) unsigned NOT NULL,
  `nombre` varchar(200) NOT NULL,
  `regla_negocio` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `rol`
--

/*!40000 ALTER TABLE `rol` DISABLE KEYS */;
INSERT INTO `rol` (`id`,`nombre`,`regla_negocio`) VALUES
 (2000,'RO_GERENTE DE ARQUITECTURA','(&(UNIDAD=GA0001)(CARGO=PU0019))'),
 (2001,'RO_ANALISTA\'S TEAM','(&(UNIDAD=GA0002)(CARGO=PU0020))');
/*!40000 ALTER TABLE `rol` ENABLE KEYS */;

--
-- Definition of table `estado`
--

DROP TABLE IF EXISTS `estado`;
CREATE TABLE `estado` (
  `id` bigint(20) NOT NULL,
  `nombre` varchar(200) NOT NULL,
  `observacion` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `estado`
--

/*!40000 ALTER TABLE `estado` DISABLE KEYS */;
INSERT INTO `estado` (`id`,`nombre`,`observacion`) VALUES
 (1,'ACTIVO',NULL),
 (2,'INACTIVO','revisar manualmente');
/*!40000 ALTER TABLE `estado` ENABLE KEYS */;

--
-- Definition of table `detalleinbox`
--

DROP TABLE IF EXISTS `detalleinbox`;
CREATE TABLE `detalleinbox` (
  `id_atributo` varchar(50) NOT NULL,
  `id_detalleatributo` bigint(20) unsigned NOT NULL,
  `id` bigint(20) unsigned NOT NULL,
  `tipo_nombre` varchar(200) NOT NULL,
  `vista` varchar(200) NOT NULL,
  `tipo` varchar(50) NOT NULL,
  PRIMARY KEY (`id_atributo`,`id_detalleatributo`,`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `detalleinbox`
--

/*!40000 ALTER TABLE `detalleinbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `detalleinbox` ENABLE KEYS */;


/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
