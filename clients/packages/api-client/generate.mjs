import { readFileSync, writeFileSync } from "node:fs"
import openapiTS, { astToString } from "openapi-typescript"

// Each `x-extensible-enum`'s known values, beside a field typed `string`: `string & {}` would not survive
// openapi-fetch's `Readable` (docs/specs/2026-09-26-the-codes-declare-their-sets.md, decision D).
const contract = JSON.parse(readFileSync(new URL("../../../contract/openapi.json", import.meta.url), "utf8"))
const known = Object.entries(contract.components.schemas)
  .filter(([, schema]) => schema["x-extensible-enum"])
  .map(([name, schema]) => `    ${name}: ${schema["x-extensible-enum"].map((value) => JSON.stringify(value)).join(" | ")};`)
const extensibleEnums = `export interface extensibleEnums {\n${known.join("\n")}\n}\n`
writeFileSync(new URL("./src/schema.d.ts", import.meta.url), astToString(await openapiTS(contract)) + extensibleEnums)
