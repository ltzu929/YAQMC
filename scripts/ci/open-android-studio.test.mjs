import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { setTimeout } from 'node:timers/promises';

import { repositoryRoot } from './repo.mjs';

function fixture(t) {
  const parent = path.join(repositoryRoot, 'artifacts/android-native-comparison');
  mkdirSync(parent, { recursive: true });
  const root = mkdtempSync(path.join(parent, 'studio-test-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const android = path.join(root, 'apps/android/android');
  mkdirSync(android, { recursive: true });
  mkdirSync(path.join(root, 'scripts'), { recursive: true });
  copyFileSync(
    path.join(repositoryRoot, 'scripts/open-android-studio.mjs'),
    path.join(root, 'scripts/open-android-studio.mjs'),
  );
  // Toolchain discovery is covered separately; these tests exercise the launcher.
  writeFileSync(
    path.join(root, 'scripts/android-toolchain.mjs'),
    `export const resolveAndroidJava = () => process.execPath;
export const resolveAndroidToolchain = () => ({ sdk: 'fixture-sdk', ndk: 'fixture-ndk' });
export const resolveRustlsVerifierMaven = () => 'fixture-maven';
`,
  );
  // Node stands in for Studio and records only the relevant injected inputs.
  writeFileSync(path.join(android, 'package.json'), '{"main":"capture.cjs"}');
  writeFileSync(
    path.join(android, 'capture.cjs'),
    `const fs = require('node:fs');
fs.writeFileSync(process.env.STUDIO_CAPTURE + '.tmp', JSON.stringify({
  core: process.env.YAQMC_ANDROID_NATIVE_LIB_DIR,
  commit: process.env.YAQMC_BUILD_COMMIT,
  version: process.env.YAQMC_VERSION_NAME,
  maven: process.env.YAQMC_RUSTLS_VERIFIER_MAVEN_DIR,
}));
fs.renameSync(process.env.STUDIO_CAPTURE + '.tmp', process.env.STUDIO_CAPTURE);`,
  );
  const capture = path.join(root, 'capture.json');
  const environment = { ...process.env, STUDIO_CAPTURE: capture };
  delete environment.YAQMC_ANDROID_NATIVE_LIB_DIR;
  environment.YAQMC_ANDROID_NATIVE_ABI = 'arm64-v8a';
  function run(...args) {
    return spawnSync(
      process.execPath,
      [path.join(root, 'scripts/open-android-studio.mjs'), process.execPath, ...args],
      { cwd: root, env: environment, encoding: 'utf8' },
    );
  }
  return { root, android, capture, environment, run };
}

test('Studio opens without a prebuilt Core and preserves local settings', async (t) => {
  const f = fixture(t);
  f.environment.YAQMC_ANDROID_NATIVE_LIB_DIR = 'stale-core';
  f.environment.YAQMC_VERSION_NAME = 'stale-version';
  f.environment.YAQMC_BUILD_COMMIT = 'stale-commit';
  const settings = path.join(f.android, '.idea/gradle.xml');
  mkdirSync(path.dirname(settings), { recursive: true });
  writeFileSync(settings, '<existing-IDE-settings />');
  writeFileSync(path.join(f.android, 'local.properties'), 'unrelated=value\n');
  const result = f.run();
  assert.equal(result.status, 0, result.stderr);
  for (let attempt = 0; !existsSync(f.capture) && attempt < 250; attempt += 1) {
    await setTimeout(20);
  }
  const inputs = JSON.parse(readFileSync(f.capture, 'utf8'));
  assert.equal(inputs.commit, undefined);
  assert.equal(inputs.version, undefined);
  assert.equal(inputs.core, undefined);
  assert.equal(inputs.maven, 'fixture-maven');
  assert.equal(readFileSync(settings, 'utf8'), '<existing-IDE-settings />');
  assert.match(
    readFileSync(path.join(f.android, 'local.properties'), 'utf8'),
    /^unrelated=value$/mu,
  );
  assert.match(
    readFileSync(path.join(f.android, 'local.properties'), 'utf8'),
    /^yaqmc\.rustlsVerifierMavenDir=fixture-maven$/mu,
  );
});

test('prepare-only persists the Rust toolchain without launching Studio', (t) => {
  const f = fixture(t);
  f.environment.CARGO_HOME = 'D:/fixture/cargo';
  f.environment.RUSTUP_HOME = 'D:/fixture/rustup';
  assert.equal(f.run('--prepare-only').status, 0);
  const properties = readFileSync(path.join(f.android, 'local.properties'), 'utf8');
  assert.match(properties, /^yaqmc\.cargoHome=D\\:.*fixture\/cargo$/mu);
  assert.match(properties, /^yaqmc\.rustupHome=D\\:.*fixture\/rustup$/mu);
  assert.equal(existsSync(f.capture), false);
});

test('prepare-only selects Gradle JDK without injecting version or Core settings', (t) => {
  const f = fixture(t);
  const result = f.run('--prepare-only');
  assert.equal(result.status, 0, result.stderr);
  assert.match(
    readFileSync(path.join(f.android, '.idea/gradle.xml'), 'utf8'),
    /#GRADLE_LOCAL_JAVA_HOME/u,
  );
  assert.doesNotMatch(
    readFileSync(path.join(f.android, 'local.properties'), 'utf8'),
    /version|nativeLibDir/u,
  );
  assert.equal(existsSync(f.capture), false);
});
