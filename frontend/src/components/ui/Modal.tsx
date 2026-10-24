import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'

export interface ModalProps {
  /** Usado como `aria-label` del `role="dialog"` -- identifica el modal en los tests igual que ya se hace con los `<form aria-label>` existentes. */
  title: string
  onClose: () => void
  children: ReactNode
}

/**
 * Diálogo modal genérico -- reemplaza los formularios "Registrar"/
 * "Actualizar" que antes vivían siempre visibles inline en la página
 * (pedido explícito del usuario 2026-08-20: "boton registar no abre una
 * pestaña para registro actualizar igual"). Se monta/desmonta
 * condicionalmente desde quien lo usa (no tiene prop `open`) -- mientras no
 * está montado no hay overlay ni listeners, más simple que manejar un
 * estado "cerrado pero en el DOM".
 *
 * Cierra con Escape o clic en el overlay, además del botón "×" -- las 3
 * vías llaman a la misma `onClose` que ya recibe quien lo usa, ningún
 * estado propio de "abierto/cerrado" vive acá.
 */
export function Modal({ title, onClose, children }: ModalProps) {
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return createPortal(
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal__header">
          <h3>{title}</h3>
          <button
            type="button"
            className="modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            ×
          </button>
        </div>
        <div className="modal__body">{children}</div>
      </div>
    </div>,
    document.body,
  )
}
