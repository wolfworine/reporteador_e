import { QueryClient } from '@tanstack/react-query'

/**
 * Instancia única de `QueryClient` para toda la app (ver
 * `docs/architecture.md` — `app/` concentra providers/routing). Todo dato
 * de servidor pasa por TanStack Query (`useQuery`/`useMutation`) — prohibido
 * `useEffect` + `useState` manual para fetch de datos (ver
 * `docs/architecture.md`, regla dura).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Evita refetch agresivo por foco de ventana hasta que existan
      // endpoints reales que lo justifiquen; se puede afinar por query
      // individual en features posteriores.
      refetchOnWindowFocus: false,
      retry: 1,
      // Sin esto (default 0) cada vez que se vuelve a montar una pantalla ya
      // visitada (ej. navegar Área → Roles → Área) React Query la trataba
      // como "stale" al instante y volvía a mostrar "Cargando..." aunque los
      // datos ya estuvieran en cache y no hubieran cambiado -- bug real
      // reportado por el usuario 2026-08-20 ("por que veo que dice cargando
      // aplicacion"). 60s es suficiente para que la navegación entre
      // pantallas dentro de la misma sesión se sienta instantánea sin dejar
      // de reflejar cambios reales en un plazo corto.
      staleTime: 60_000,
      // Mantiene los datos en cache más allá del staleTime aunque el
      // componente se desmonte (ej. salir de Roles y volver) -- sin esto
      // TanStack Query los garbage-collectea a los 5 minutos por defecto de
      // todas formas, pero conviene dejarlo explícito junto a staleTime.
      gcTime: 5 * 60_000,
    },
  },
})
