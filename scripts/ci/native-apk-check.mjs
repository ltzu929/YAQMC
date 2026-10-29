import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';

const BUILD_TOOLS_VERSION = '36.0.0';
const PACKAGE_NAME = 'org.yaqmc.android.nativeui';
const CORE_ENTRY = 'lib/arm64-v8a/libyaqmc_core.so';
const ALLOWED_VARIANTS = new Set(['debug', 'benchmark']);

export function parseNativeApkBadging(output) {
  const match = /^package:\s+name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/mu.exec(
    output,
  );
  if (!match) throw new Error('APK does not report a valid Android package identity');
  return {
    packageName: match[1],
    versionCode: Number(match[2]),
    versionName: match[3],
  };
}

export function assertNativeApkContents({ badging, files, variant, version }) {
  if (!ALLOWED_VARIANTS.has(variant)) throw new Error('Unsupported native APK variant');
  const actual = parseNativeApkBadging(badging);
  const expectedName = version.versionName + '-native-' + variant;
  if (actual.packageName !== PACKAGE_NAME) {
    throw new Error('Native APK package mismatch: ' + actual.packageName);
  }
  if (actual.versionCode !== version.versionCode || actual.versionName !== expectedName) {
    throw new Error(
      'Native APK version mismatch: ' + actual.versionCode + ' / ' + actual.versionName,
    );
  }

  const entries = files.split(/\r?\n/u).map((entry) => entry.trim());
  if (!entries.includes(CORE_ENTRY)) {
    throw new Error('Native APK does not contain the ARM64 Rust Core: ' + CORE_ENTRY);
  }
  const abis = new Set(
    entries
      .filter((entry) => /^lib\/[^/]+\/[^/]+\.so$/u.test(entry))
      .map((entry) => entry.split('/')[1]),
  );
  if (abis.size !== 1 || !abis.has('arm64-v8a')) {
    throw new Error('Native APK must only package ARM64 libraries: ' + [...abis].join(', '));
  }
  const declaredAbi = /^native-code:\s*'([^']+)'/mu.exec(badging)?.[1];
  if (declaredAbi && declaredAbi !== 'arm64-v8a') {
    throw new Error('Native APK declares an unexpected ABI: ' + declaredAbi);
  }
  return actual;
}

export function parseNativeApkSigner(output) {
  const match = /Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f]{64})/iu.exec(output);
  if (!match) throw new Error('Native APK signature certificate could not be verified');
  return match[1].toLowerCase();
}

export function verifyNativeApk({
  apkPath,
  sdk,
  java,
  variant,
  version,
  runCommand = spawnSync,
  fileExists = existsSync,
}) {
  if (!ALLOWED_VARIANTS.has(variant)) throw new Error('Unsupported native APK variant');
  const toolsDirectory = path.join(sdk, 'build-tools', BUILD_TOOLS_VERSION);
  const aapt = path.join(toolsDirectory, process.platform === 'win32' ? 'aapt.exe' : 'aapt');
  const signerJar = path.join(toolsDirectory, 'lib', 'apksigner.jar');
  for (const file of [apkPath, aapt, signerJar]) {
    if (!fileExists(file)) throw new Error('Native APK verification input is missing: ' + file);
  }
  function run(command, args) {
    const result = runCommand(command, args, {
      encoding: 'utf8',
      windowsHide: true,
      maxBuffer: 16 * 1024 * 1024,
    });
    if (result.error) throw result.error;
    if (result.status !== 0) {
      throw new Error(
        'Native APK verification command failed: ' +
          path.basename(command) +
          ' (exit ' +
          result.status +
          '): ' +
          (result.stderr || result.stdout || '').trim(),
      );
    }
    return result.stdout;
  }

  const badging = run(aapt, ['dump', 'badging', apkPath]);
  const files = run(aapt, ['list', apkPath]);
  const identity = assertNativeApkContents({ badging, files, variant, version });
  const signature = run(java, [
    '-cp',
    signerJar,
    'com.android.apksigner.ApkSignerTool',
    'verify',
    '--verbose',
    '--print-certs',
    apkPath,
  ]);
  const certificateSha256 = parseNativeApkSigner(signature);
  return { ...identity, certificateSha256 };
}
