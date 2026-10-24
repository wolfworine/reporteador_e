import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ReporteTabla } from './ReporteTabla'
import type { Reporte } from '../../types/reporte'
import type { Detalle } from '../../types/detalle'

function detalle(overrides: Partial<Detalle>): Detalle {
  return {
    id: '1',
    atributoCodigo: 'AT1',
    atributoNombre: 'Atributo Uno',
    ...overrides,
  }
}

/**
 * Fixture representativo con 2 Cargos, varios Roles por Cargo, y al menos
 * 2 de las 4 categorías de suministro pobladas -- acceptance criterio 1
 * exige verificar la jerarquía Cargo→Rol→(Roles/Grupos/Inbox/DefaultInbox)
 * "con un fixture de datos representativo", no un caso trivial de 1 fila.
 */
function fixtureReportes(): Reporte[] {
  return [
    {
      area: 'Sistemas',
      codigoArea: 'A1',
      cargo: 'Analista',
      codigoCargo: 'C1',
      cantidadRol: 2,
      cantidadSuministro: 3,
      detalleRol: [
        {
          idRol: 'R1',
          rol: 'Rol Analista Uno',
          tipo: 'FIJO',
          aplicacion: 'SAP',
          tipoRol: ['Directo'],
          suministroRoles: [
            detalle({ id: '1', atributoCodigo: 'R-COD-1', atributoNombre: 'Rol Suministro Uno' }),
            detalle({ id: '2', atributoCodigo: 'R-COD-2', atributoNombre: 'Rol Suministro Dos' }),
          ],
          suministroGrupos: [
            detalle({ id: '3', atributoCodigo: 'G-COD-1', atributoNombre: 'Grupo Suministro Uno' }),
          ],
          suministroInbox: [],
          suministroDefaultinbox: [],
          cantidadSuministroRol: 3,
          cantidadSuministros: 3,
        },
        {
          idRol: 'R2',
          rol: 'Rol Analista Dos',
          tipo: 'TEMPORAL',
          aplicacion: 'CRM',
          // `tipoRol` en producción son códigos de categoría reales
          // (`ROLES`/`GRUPOS`/`INBOX`/`DEFAULTINBOX`), siempre en
          // correspondencia 1:1 con qué `suministroX` viene poblado (ver
          // Javadoc de `types/reporte.ts#DetalleRol.tipoRol` y de
          // `TitularidadCell` en `ReporteTabla.tsx`) -- este fixture antes
          // usaba valores inventados ("Heredado"/"Directo") sin ningún
          // `suministroX` poblado, una combinación que el backend real
          // nunca produce.
          tipoRol: ['GRUPOS', 'INBOX'],
          suministroRoles: [],
          suministroGrupos: [
            detalle({ id: '6', atributoCodigo: 'G-COD-2', atributoNombre: 'Grupo Suministro Dos' }),
          ],
          suministroInbox: [
            detalle({ id: '7', atributoCodigo: 'I-COD-2', atributoNombre: 'Inbox Suministro Dos' }),
          ],
          suministroDefaultinbox: [],
          cantidadSuministroRol: 2,
          cantidadSuministros: 2,
        },
      ],
    },
    {
      area: 'Sistemas',
      codigoArea: 'A1',
      cargo: 'Supervisor',
      codigoCargo: 'C2',
      cantidadRol: 1,
      cantidadSuministro: 2,
      detalleRol: [
        {
          idRol: 'R3',
          rol: 'Rol Supervisor Uno',
          tipo: 'FIJO',
          aplicacion: 'SAP',
          tipoRol: ['Directo'],
          suministroRoles: [],
          suministroGrupos: [],
          suministroInbox: [
            detalle({ id: '4', atributoCodigo: 'I-COD-1', atributoNombre: 'Inbox Suministro Uno' }),
          ],
          suministroDefaultinbox: [
            detalle({ id: '5', atributoCodigo: 'D-COD-1', atributoNombre: 'DefaultInbox Suministro Uno' }),
          ],
          cantidadSuministroRol: 0,
          cantidadSuministros: 2,
        },
      ],
    },
  ]
}

describe('ReporteTabla', () => {
  it('should render one section per Cargo, each with its Área/Cargo header', () => {
    render(<ReporteTabla reportes={fixtureReportes()} />)

    const cargoUno = screen.getByRole('region', { name: 'Cargo C1' })
    expect(within(cargoUno).getByText('Área: A1 - Sistemas')).toBeInTheDocument()
    expect(within(cargoUno).getByText('Cargo: C1 - Analista')).toBeInTheDocument()

    const cargoDos = screen.getByRole('region', { name: 'Cargo C2' })
    expect(within(cargoDos).getByText('Área: A1 - Sistemas')).toBeInTheDocument()
    expect(within(cargoDos).getByText('Cargo: C2 - Supervisor')).toBeInTheDocument()
  })

  it('should list every Rol row under its own Cargo with Rol/Tipo/Aplicacion columns', () => {
    render(<ReporteTabla reportes={fixtureReportes()} />)

    const cargoUno = screen.getByRole('region', { name: 'Cargo C1' })
    expect(within(cargoUno).getByText('Rol Analista Uno')).toBeInTheDocument()
    expect(within(cargoUno).getByText('Rol Analista Dos')).toBeInTheDocument()
    expect(within(cargoUno).getByText('FIJO')).toBeInTheDocument()
    expect(within(cargoUno).getByText('TEMPORAL')).toBeInTheDocument()
    expect(within(cargoUno).getByText('SAP')).toBeInTheDocument()
    expect(within(cargoUno).getByText('CRM')).toBeInTheDocument()

    const cargoDos = screen.getByRole('region', { name: 'Cargo C2' })
    expect(within(cargoDos).getByText('Rol Supervisor Uno')).toBeInTheDocument()
    expect(within(cargoDos).queryByText('Rol Analista Uno')).not.toBeInTheDocument()
  })

  it('should render Titularidad as its own list of values per Rol', () => {
    render(<ReporteTabla reportes={fixtureReportes()} />)

    const titularidadDos = screen.getByRole('list', {
      name: 'Titularidad Rol Analista Dos',
    })
    expect(within(titularidadDos).getByText('GRUPOS')).toBeInTheDocument()
    expect(within(titularidadDos).getByText('INBOX')).toBeInTheDocument()
  })

  it('should render only the categories with data, one nested table per populated category (ROLES/GRUPOS/INBOX/DEFAULTINBOX), once its Rol row is expanded', () => {
    render(<ReporteTabla reportes={fixtureReportes()} />)

    // Rediseño 2026-08-25 (Dirección A -- progressive disclosure): cada fila
    // de Rol arranca colapsada, el detalle de Atributos solo se monta al
    // expandir (clic en la cabecera, `role="button"` con
    // `aria-label="Ver detalle de <rol>"`) -- antes las 4 sub-tablas
    // estaban siempre desplegadas.
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalle de Rol Analista Uno' }))
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalle de Rol Analista Dos' }))
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalle de Rol Supervisor Uno' }))

    // Rol Analista Uno: ROLES y GRUPOS pobladas, INBOX y DEFAULTINBOX vacías (no se renderizan).
    expect(
      screen.getByText('Rol Suministro Uno'),
    ).toBeInTheDocument()
    expect(screen.getByText('Rol Suministro Dos')).toBeInTheDocument()
    expect(screen.getByText('Grupo Suministro Uno')).toBeInTheDocument()

    // Rol Analista Dos: GRUPOS e INBOX pobladas (ver fixture -- Titularidad
    // solo puede listar categorías con datos reales, ver Javadoc de
    // `TitularidadCell`).
    expect(screen.getByText('Grupo Suministro Dos')).toBeInTheDocument()
    expect(screen.getByText('Inbox Suministro Dos')).toBeInTheDocument()

    // Rol Supervisor Uno: INBOX y DEFAULTINBOX pobladas.
    expect(screen.getByText('Inbox Suministro Uno')).toBeInTheDocument()
    expect(screen.getByText('DefaultInbox Suministro Uno')).toBeInTheDocument()

    // Categorías vacías no aparecen como tabla en absoluto (paridad con
    // `rendered="#{not empty ...}"` del legacy). ROLES solo la puebla "Rol
    // Analista Uno"; GRUPOS e INBOX las pueblan 2 Roles cada una (Analista
    // Uno/Dos y Analista Dos/Supervisor Uno respectivamente);
    // DEFAULTINBOX solo la puebla "Rol Supervisor Uno".
    expect(
      screen.queryAllByRole('table', { name: 'Categoría ROLES' }),
    ).toHaveLength(1)
    expect(
      screen.queryAllByRole('table', { name: 'Categoría GRUPOS' }),
    ).toHaveLength(2)
    expect(
      screen.queryAllByRole('table', { name: 'Categoría INBOX' }),
    ).toHaveLength(2)
    expect(
      screen.queryAllByRole('table', { name: 'Categoría DEFAULTINBOX' }),
    ).toHaveLength(1)
  })

  it('should keep every Rol row collapsed by default and toggle its Atributos detail on click', () => {
    render(<ReporteTabla reportes={fixtureReportes()} />)

    const boton = screen.getByRole('button', {
      name: 'Ver detalle de Rol Analista Uno',
    })
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Rol Suministro Uno')).not.toBeInTheDocument()

    fireEvent.click(boton)
    expect(boton).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Rol Suministro Uno')).toBeInTheDocument()

    fireEvent.click(boton)
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Rol Suministro Uno')).not.toBeInTheDocument()
  })

  it('should render a fallback message when there is no report data', () => {
    render(<ReporteTabla reportes={[]} />)

    expect(
      screen.getByText('No hay datos de reporte para mostrar.'),
    ).toBeInTheDocument()
  })
})
