// Validates docs/auth-service.yml using Swagger Parser before testing or deployment.
// Resolves the contract from this script location and exits unsuccessfully if validation fails.
const SwaggerParser = require('@apidevtools/swagger-parser');
const { resolve } = require('node:path');
SwaggerParser.validate(resolve(__dirname, '../../../../docs/auth-service.yml'))
  .then(() => console.log('OpenAPI contract is valid'))
  .catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
