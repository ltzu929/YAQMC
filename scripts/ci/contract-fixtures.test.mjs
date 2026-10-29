import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  FIXTURE_FILES,
  assertFixturesMatch,
  committedFixturesDir,
  readFixture,
} from '../update-contract-fixtures.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

test('committed contract fixtures exist and stay under the 32 MiB hard cap', () => {
  const dir = committedFixturesDir(repositoryRoot);
  for (const name of FIXTURE_FILES) {
    readFileSync(path.join(dir, name), 'utf8');
  }
  const constants = readFixture(dir, 'constants.json');
  assert.equal(constants.protocolVersion, 1);
  assert.equal(constants.frameHardCapBytes, 32 * 1024 * 1024);
  assert.equal(constants.defaultMethodPayloadBytes, 1024 * 1024);
});

test('fixture drift comparison ignores platform line endings only', (context) => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'yaqmc-contract-lines-'));
  const left = path.join(root, 'left');
  const right = path.join(root, 'right');
  mkdirSync(left);
  mkdirSync(right);
  context.after(() => rmSync(root, { force: true, recursive: true }));

  for (const name of FIXTURE_FILES) {
    writeFileSync(path.join(left, name), '{\r\n  "value": 1\r\n}\r\n', 'utf8');
    writeFileSync(path.join(right, name), '{\n  "value": 1\n}\n', 'utf8');
  }

  assert.doesNotThrow(() => assertFixturesMatch(left, right));
});
