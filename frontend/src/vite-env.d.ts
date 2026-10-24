/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * URL base de la API REST del backend (Quarkus). Configurable por entorno.
   * Sin valor de producción hardcodeado — ver `.env.example` y `src/api/client.ts`.
   */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
