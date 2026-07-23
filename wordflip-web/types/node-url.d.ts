declare module "node:url" {
  export class URL {
    constructor(input: string | URL, base?: string | URL);
  }

  export function fileURLToPath(url: string | URL): string;
}

interface ImportMeta {
  readonly url: string;
}
