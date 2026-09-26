import { readFileSync, writeFileSync } from "node:fs"
import openapiTS, { astToString } from "openapi-typescript"

const contractFile = new URL("../../../contract/openapi.json", import.meta.url)
const schemaFile = new URL("./src/schema.d.ts", import.meta.url)

/** One member of `extensibleEnums`: the component's name and the union of its known values. */
function knownValuesLine(name, values) {
  const union = values.map((value) => JSON.stringify(value)).join(" | ")
  return `    ${name}: ${union};`
}

const contract = JSON.parse(readFileSync(contractFile, "utf8"))
const generatedTypes = astToString(await openapiTS(contract))

// Each `x-extensible-enum`'s known values, beside a field typed `string`: `string & {}` would not survive
// openapi-fetch's `Readable` (docs/specs/2026-09-26-the-codes-declare-their-sets.md, decision D).
const lines = []
for (const [name, schema] of Object.entries(contract.components.schemas)) {
  const values = schema["x-extensible-enum"]
  if (values) lines.push(knownValuesLine(name, values))
}
const extensibleEnums = ["export interface extensibleEnums {", ...lines, "}", ""].join("\n")

writeFileSync(schemaFile, generatedTypes + extensibleEnums)
