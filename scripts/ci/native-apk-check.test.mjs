import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';

import {
  assertNativeApkContents,
  parseNativeApkSigner,
  verifyNativeApk,
} from './native-apk-check.mjs';

const version = { versionName: '0.1.1', versionCode: 1_019_000 };
const packageLine =
  "package: name='org.yaqmc.android.nativeui' versionCode='1019000' versionName='0.1.1-native-benchmark'";
const badging = packageLine + "\nsdkVersion:'26'\nnative-code: 'arm64-v8a'\n";
const files = 'AndroidManifest.xml\nlib/arm64-v8a/libyaqmc_core.so\nlib/arm64-v8a/libc++_shared.so\n';
const certificate = 'ab'.repeat(32);
const signature = 'Verifies\nSigner #1 certificate SHA-256 digest: ' + certificate + '\n';

test('accepts only a correctly identified native APK with the packaged ARM64 Core', () => {
  assert.deepEqual(assertNativeApkContents({ badging, files, variant: 'benchmark', version }), {
    packageName: 'org.yaqmc.android.nativeui',
    versionCode: 1_019_000,
    versionName: '0.1.1-native-benchmark',
  });
});

test('rejects a wrong package, version, or build variant', () => {
  for (const invalid of [
    { badging: badging.replace('org.yaqmc.android.nativeui', 'org.yaqmc.android') },
    { badging: badging.replace('1019000', '1') },
    { badging: badging.replace('native-benchmark', 'native-debug') },
    { badging: 'garbled' },
  ]) {
    assert.throws(
      () => assertNativeApkContents({ badging, files, variant: 'benchmark', version, ...invalid }),
      /package|version|identity/i,
    );
  }
  assert.throws(
    () => assertNativeApkContents({ badging, files, variant: 'release', version }),
    /Unsupported/,
  );
});

test('rejects missing Core and unexpected native library ABIs', () => {
  assert.throws(
    () =>
      assertNativeApkContents({
        badging,
        files: 'AndroidManifest.xml\n',
        variant: 'benchmark',
        version,
      }),
    /Rust Core/,
  );
  assert.throws(
    () =>
      assertNativeApkContents({
        badging,
        files: files + 'lib/x86_64/libsomething.so\n',
        variant: 'benchmark',
        version,
      }),
    /only package ARM64/,
  );
  assert.throws(
    () =>
      assertNativeApkContents({
        badging: badging.replace("'arm64-v8a'", "'x86_64'"),
        files,
        variant: 'benchmark',
        version,
      }),
    /unexpected ABI/,
  );
});

test('refuses to accept an unverified signer certificate', () => {
  assert.equal(parseNativeApkSigner(signature), certificate);
  assert.throws(() => parseNativeApkSigner('DOES NOT VERIFY'), /signature certificate/);
});

test('inspects built APK before allowing it to be staged', () => {
  const commands = [];
  const result = verifyNativeApk({
    apkPath: path.join('tmp', 'nativeApp-benchmark.apk'),
    sdk: path.join('tmp', 'sdk'),
    java: 'java',
    variant: 'benchmark',
    version,
    fileExists: () => true,
    runCommand(command, args) {
      commands.push([command, args]);
      return {
        status: 0,
        stdout:
          args[0] === 'dump' ? badging : args[0] === 'list' ? files : signature,
        stderr: '',
      };
    },
  });
  assert.equal(result.certificateSha256, certificate);
  assert.equal(commands.length, 3);
  assert.deepEqual(commands[0][1].slice(0, 2), ['dump', 'badging']);
  assert.deepEqual(commands[1][1].slice(0, 1), ['list']);
  assert.ok(commands[2][1].includes('verify'));
});

test('fails closed for a missing verification tool or failed signature command', () => {
  assert.throws(
    () =>
      verifyNativeApk({
        apkPath: 'candidate.apk',
        sdk: 'sdk',
        java: 'java',
        variant: 'debug',
        version,
        fileExists: () => false,
      }),
    /input is missing/,
  );
  assert.throws(
    () =>
      verifyNativeApk({
        apkPath: 'candidate.apk',
        sdk: 'sdk',
        java: 'java',
        variant: 'benchmark',
        version,
        fileExists: () => true,
        runCommand(command, args) {
          if (args[0] === 'dump') return { status: 0, stdout: badging };
          if (args[0] === 'list') return { status: 0, stdout: files };
          return { status: 1, stdout: '', stderr: 'DOES NOT VERIFY' };
        },
      }),
    /verification command failed/,
  );
});
