import { createContext, useCallback, useContext, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'

type ToastTipo = 'exito' | 'error'

interface ToastItem {
  id: number
  tipo: ToastTipo
  mensaje: string
}

interface ToastContextValue {
  /** Notificación de éxito -- usar tras crear/actualizar/eliminar sin error (pedido explícito del usuario: "no hay notificaciones"). */
  mostrarExito: (mensaje: string) => void
  /** Notificación de error -- complementa (no reemplaza) el `role="alert"` inline ya existente en cada formulario. */
  mostrarError: (mensaje: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const DURACION_MS = 4000

/**
 * Sistema de notificaciones "toast" a nivel de app (montado una vez en
 * `App.tsx`, fuera del router para sobrevivir a la navegación). Antes de
 * esto la única confirmación de que crear/actualizar/eliminar funcionó era
 * que la fila apareciera/cambiara/desapareciera de la tabla -- sin ninguna
 * confirmación explícita, ni feedback alguno en el caso de eliminar (que
 * no muestra ningún cambio visible más allá de que la fila desaparece).
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(0)

  const descartar = useCallback((id: number) => {
    setToasts((actuales) => actuales.filter((toast) => toast.id !== id))
  }, [])

  const agregar = useCallback(
    (tipo: ToastTipo, mensaje: string) => {
      const id = nextId.current++
      setToasts((actuales) => [...actuales, { id, tipo, mensaje }])
      window.setTimeout(() => descartar(id), DURACION_MS)
    },
    [descartar],
  )

  const value: ToastContextValue = {
    mostrarExito: (mensaje) => agregar('exito', mensaje),
    mostrarError: (mensaje) => agregar('error', mensaje),
  }

  return (
    <ToastContext.Provider value={value}>
      {children}
      {createPortal(
        <div className="toast-stack" role="region" aria-label="Notificaciones">
          {toasts.map((toast) => (
            <div
              key={toast.id}
              className={`toast toast--${toast.tipo}`}
              role={toast.tipo === 'error' ? 'alert' : 'status'}
            >
              <span>{toast.mensaje}</span>
              <button
                type="button"
                className="toast__close"
                onClick={() => descartar(toast.id)}
                aria-label="Descartar notificación"
              >
                ×
              </button>
            </div>
          ))}
        </div>,
        document.body,
      )}
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast debe usarse dentro de <ToastProvider>')
  }
  return context
}
