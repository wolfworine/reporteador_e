import { apiClient } from './client'
import type { MenuNodo } from '../types/menu'

/**
 * Trae el árbol de menú (submenús con sus ítems hijos ya anidados, ver
 * `types/menu.ts`) para un `tipoUsuario` dado -- filtrado y armado del
 * lado del servidor (`web/MenuResource.java`, `GET
 * /api/menu/{tipoUsuario}`).
 */
export async function fetchMenu(tipoUsuario: string): Promise<MenuNodo[]> {
  return apiClient<MenuNodo[]>(`/api/menu/${encodeURIComponent(tipoUsuario)}`)
}
