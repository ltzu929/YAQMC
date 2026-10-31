import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  resolveAndroidJava,
  resolveAndroidToolchain,
  resolveRustlsVerifierMaven,
} from './android-toolchain.mjs';
import { rootVersion } from './ci/android-version.mjs';
import { verifyNativeApk } from './ci/native-apk-check.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const android = path.join(root, 'apps/android/android');
const args = new Set(process.argv.slice(2));
for (const arg of args)
  if (!['--debug', '--benchmark', '--reuse-core'].includes(arg))
    throw new Error(`Unknown argument: ${arg}`);
if (args.has('--debug') && args.has('--benchmark')) throw new Error('Choose one build variant');
const variant = args.has('--debug') ? 'debug' : args.has('--benchmark') ? 'benchmark' : 'release';
const toolchain = resolveAndroidToolchain();
const version = rootVersion(root);
const nativeDir =
  args.has('--reuse-core') && process.env.YAQMC_ANDROID_NATIVE_LIB_DIR
    ? path.resolve(process.env.YAQMC_ANDROID_NATIVE_LIB_DIR)
    : path.join(android, 'nativeApp', 'build/generated/jniLibs');
const environment = {
  ...process.env,
  ANDROID_HOME: toolchain.sdk,
  ANDROID_SDK_ROOT: toolchain.sdk,
  ANDROID_NDK_HOME: toolchain.ndk,
  ANDROID_NDK_ROOT: toolchain.ndk,
  YAQMC_RUSTLS_VERIFIER_MAVEN_DIR: resolveRustlsVerifierMaven(),
};
function run(command, commandArgs, cwd = root) {
  const result = spawnSync(command, commandArgs, { cwd, env: environment, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} exited with ${result.status}`);
}
if (args.has('--reuse-core')) {
  if (!existsSync(path.join(nativeDir, 'arm64-v8a/libyaqmc_core.so')))
    throw new Error('ARM64 Rust Core is missing');
  environment.YAQMC_ANDROID_NATIVE_LIB_DIR = nativeDir;
} else {
  delete environment.YAQMC_ANDROID_NATIVE_LIB_DIR;
}
const taskVariant = variant[0].toUpperCase() + variant.slice(1);
run(
  resolveAndroidJava(environment),
  [
    '-classpath',
    path.join(android, 'gradle/wrapper/gradle-wrapper.jar'),
    'org.gradle.wrapper.GradleWrapperMain',
    '--no-daemon',
    `:nativeApp:assemble${taskVariant}`,
    ':nativeApp:testDebugUnitTest',
  ],
  android,
);
const output = path.join(root, 'release-android');
mkdirSync(output, { recursive: true });
const filename =
  variant === 'release'
    ? `YAQMC-${version.versionName}-android-arm64-v8a.apk`
    : `YAQMC-${version.versionName}-android-${variant}.apk`;
const destination = path.join(output, filename);
const builtApk = path.join(
  android,
  `nativeApp/build/outputs/apk/${variant}/nativeApp-${variant}.apk`,
);
const identity = verifyNativeApk({
  apkPath: builtApk,
  sdk: toolchain.sdk,
  java: resolveAndroidJava(environment),
  version,
  variant,
});
copyFileSync(builtApk, destination);
process.stdout.write(
  `Native Android APK verified: ${identity.packageName} ${identity.versionName} (${identity.versionCode}), signer SHA-256 ${identity.certificateSha256}\n`,
);
process.stdout.write(`Native Android APK: ${destination}\n`);
