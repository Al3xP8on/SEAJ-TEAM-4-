// Copies the shared authentication OpenAPI contract and login interface into the build output.
// Creates the output directory so packaged deployments contain the required non-code assets.
const { copyFileSync, cpSync, mkdirSync } = require('node:fs');
const { join } = require('node:path');
const root = join(__dirname, '..');
mkdirSync(join(root, 'dist'), { recursive: true });
copyFileSync(join(root, '..', '..', '..', 'docs', 'auth-service.yml'), join(root, 'dist', 'openapi.yaml'));
cpSync(
  join(root, '..', '..', 'frontend', 'Login'),
  join(root, 'dist', 'public'),
  { recursive: true },
);
