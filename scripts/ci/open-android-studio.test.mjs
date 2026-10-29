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
  // Keep fixtures in the checkout so the real git revision lookup also runs.
  const parent = path.join(repositoryRoot, 'artifacts/android-native-comparison');
  mkdirSync(parent, { recursive: true });
  const root = mkdtempSync(path.join(parent, 'studio-test-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const android = path.join(root, 'apps/android/android');
  mkdirSync(android, { recursive: true });
  mkdirSync(path.join(root, 'scripts/ci'), { recursive: true });
  for (const script of ['open-android-studio.mjs', 'ci/android-version.mjs']) {
    copyFileSync(path.join(repositoryRoot, 'scripts', script), path.join(root, 'scripts', script));
  }
  writeFileSync(path.join(root, 'package.json'), JSON.stringify({ version: '0.1.1' }));
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
  const source = path.join(android, 'nativeApp/build/generated/jniLibs');
  function library(directory, abi, contents) {
    const file = path.join(directory, abi, 'libyaqmc_core.so');
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, contents);
    return file;
  }
  function run(...args) {
    return spawnSync(
      process.execPath,
      [path.join(root, 'scripts/open-android-studio.mjs'), process.execPath, ...args],
      { cwd: root, env: environment, encoding: 'utf8' },
    );
  }
  return { root, android, capture, environment, source, library, run };
}

test('Studio receives Core that survives Clean Project, and local settings are preserved', async (t) => {
  const f = fixture(t);
  f.library(f.source, 'arm64-v8a', 'core-before-clean');
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
  assert.match(inputs.commit, /^[0-9a-f]{40}$/u);
  assert.equal(inputs.version, '0.1.1');
  assert.equal(inputs.maven, 'fixture-maven');
  // Simulate the directory removed by :nativeApp:clean after the IDE starts.
  rmSync(path.join(f.android, 'nativeApp/build'), { recursive: true });
  assert.equal(
    readFileSync(path.join(inputs.core, 'arm64-v8a/libyaqmc_core.so'), 'utf8'),
    'core-before-clean',
  );
  assert.equal(readFileSync(settings, 'utf8'), '<existing-IDE-settings />');
  assert.match(
    readFileSync(path.join(f.android, 'local.properties'), 'utf8'),
    /^unrelated=value$/mu,
  );
});

test('prepare-only stages the selected ABI and refreshes Core without launching Studio', (t) => {
  const f = fixture(t);
  const source = path.join(f.root, 'custom-core');
  f.environment.YAQMC_ANDROID_NATIVE_LIB_DIR = source;
  f.environment.YAQMC_ANDROID_NATIVE_ABI = 'x86_64';
  f.library(source, 'x86_64', 'first-core');
  const staged = path.join(f.android, '.gradle/yaqmc-studio/jniLibs/x86_64/libyaqmc_core.so');
  assert.equal(f.run('--prepare-only').status, 0);
  assert.equal(readFileSync(staged, 'utf8'), 'first-core');
  f.library(source, 'x86_64', 'rebuilt-core');
  assert.equal(f.run('--prepare-only').status, 0);
  assert.equal(readFileSync(staged, 'utf8'), 'rebuilt-core');
  assert.equal(existsSync(f.capture), false);
});

test('missing matching Core stops before changing IDE configuration', (t) => {
  const f = fixture(t);
  f.library(f.source, 'x86_64', 'wrong-ABI');
  const result = f.run('--prepare-only');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Build a matching Rust Core/u);
  assert.equal(existsSync(path.join(f.android, 'local.properties')), false);
  assert.equal(existsSync(f.capture), false);
});
