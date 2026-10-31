import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';

import {
  assertNativeApkContents,
  parseNativeApkSigner,
  RELEASE_CERTIFICATE_SHA256,
  verifyNativeApk,
} from './native-apk-check.mjs';

const version = { versionName: '0.1.1', versionCode: 1_019_000 };
const packageLine = "package: name='org.yaqmc.android' versionCode='1019000' versionName='0.1.1'";
const badging = packageLine + "\nsdkVersion:'26'\nnative-code: 'arm64-v8a'\n";
const files = [
  'AndroidManifest.xml',
  'lib/arm64-v8a/libyaqmc_core.so',
  'lib/arm64-v8a/libc++_shared.so',
  '',
].join('\n');
const certificate = RELEASE_CERTIFICATE_SHA256;
const signature = 'Verifies\nSigner #1 certificate SHA-256 digest: ' + certificate + '\n';

test('accepts only a correctly identified native APK with the packaged ARM64 Core', () => {
  const identity = assertNativeApkContents({ badging, files, variant: 'release', version });
  assert.deepEqual(identity, {
    packageName: 'org.yaqmc.android',
    versionCode: 1_019_000,
    versionName: '0.1.1',
  });
});

test('rejects a wrong package, version, or build variant', () => {
  for (const invalid of [
    { badging: badging.replace('org.yaqmc.android', 'org.yaqmc.android.nativeui') },
    { badging: badging.replace('1019000', '1') },
    { badging: badging.replace("versionName='0.1.1'", "versionName='0.1.1-debug'") },
    { badging: 'garbled' },
  ]) {
    assert.throws(
      () => assertNativeApkContents({ badging, files, variant: 'release', version, ...invalid }),
      /package|version|identity/i,
    );
  }
  assert.throws(
    () => assertNativeApkContents({ badging, files, variant: 'unsigned', version }),
    /Unsupported/,
  );
});

test('rejects missing Core and unexpected native library ABIs', () => {
  assert.throws(
    () =>
      assertNativeApkContents({
        badging,
        files: 'AndroidManifest.xml\n',
        variant: 'release',
        version,
      }),
    /Rust Core/,
  );
  assert.throws(
    () =>
      assertNativeApkContents({
        badging,
        files: files + 'lib/x86_64/libsomething.so\n',
        variant: 'release',
        version,
      }),
    /only package ARM64/,
  );
  assert.throws(
    () =>
      assertNativeApkContents({
        badging: badging.replace("'arm64-v8a'", "'x86_64'"),
        files,
        variant: 'release',
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
    apkPath: path.join('tmp', 'nativeApp-release.apk'),
    sdk: path.join('tmp', 'sdk'),
    java: 'java',
    variant: 'release',
    version,
    fileExists: () => true,
    runCommand(command, args) {
      commands.push([command, args]);
      return {
        status: 0,
        stdout: args[0] === 'dump' ? badging : args[0] === 'list' ? files : signature,
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
        variant: 'release',
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

test('development APKs use explicit separate identities', () => {
  for (const variant of ['debug', 'benchmark']) {
    const developmentBadging = badging
      .replace("name='org.yaqmc.android'", `name='org.yaqmc.android.${variant}'`)
      .replace("versionName='0.1.1'", `versionName='0.1.1-native-${variant}'`);
    assertNativeApkContents({ badging: developmentBadging, files, variant, version });
    assert.throws(
      () =>
        assertNativeApkContents({
          badging: developmentBadging,
          files,
          variant: 'release',
          version,
        }),
      /package|version/i,
    );
  }
});

test('release export rejects a valid signature from a different key', () => {
  assert.throws(
    () =>
      verifyNativeApk({
        apkPath: 'candidate.apk',
        sdk: 'sdk',
        java: 'java',
        variant: 'release',
        version,
        fileExists: () => true,
        runCommand(command, args) {
          return {
            status: 0,
            stdout:
              args[0] === 'dump'
                ? badging
                : args[0] === 'list'
                  ? files
                  : 'Signer #1 certificate SHA-256 digest: ' + 'ab'.repeat(32),
          };
        },
      }),
    /does not match the existing installation/,
  );
});
