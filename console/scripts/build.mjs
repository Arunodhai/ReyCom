import { cp, mkdir, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const consoleDirectory = path.resolve(fileURLToPath(new URL("..", import.meta.url)));
const sourceDirectory = path.resolve(
  consoleDirectory,
  "../src/main/resources/static/console"
);
const outputDirectory = path.join(consoleDirectory, "dist");

await rm(outputDirectory, { recursive: true, force: true });
await mkdir(outputDirectory, { recursive: true });
await cp(sourceDirectory, outputDirectory, { recursive: true });

console.log(`ReyCom Console built at ${outputDirectory}`);
