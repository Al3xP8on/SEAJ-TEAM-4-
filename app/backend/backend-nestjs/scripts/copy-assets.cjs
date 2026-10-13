// Packages the canonical API contract and an available Angular production build.
// API-only development can use Angular's dev server; Docker always supplies the built UI.
const { copyFileSync, cpSync, mkdirSync, existsSync } = require('node:fs');
const { join } = require('node:path');
const root = join(__dirname, '..');
mkdirSync(join(root, 'dist'), { recursive: true });
copyFileSync(join(root, '..', '..', '..', 'docs', 'auth-service.yml'), join(root, 'dist', 'openapi.yaml'));
const ui = join(root, '..', '..', 'frontend', 'SEAJ-ui', 'dist', 'seaj-ui', 'browser');
if (existsSync(join(ui, 'index.html'))) {
  cpSync(ui, join(root, 'dist', 'public'), { recursive: true });
} else {
  console.info('Angular assets are not built; use the Angular dev server or build SEAJ-ui before packaging the API.');
}
