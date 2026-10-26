import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  cargoNdkArguments,
  resolveAndroidJava,
  resolveAndroidToolchain,
  resolveRustlsVerifierMaven,
} from './build-android.mjs';
import { rootVersion } from './ci/android-version.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const android = path.join(root, 'apps/android/android');
const args = new Set(process.argv.slice(2));
for (const arg of args)
  if (!['--debug', '--reuse-core'].includes(arg)) throw new Error(`Unknown argument: ${arg}`);
const variant = args.has('--debug') ? 'debug' : 'benchmark';
const toolchain = resolveAndroidToolchain();
const version = rootVersion(root);
const nativeDir =
  args.has('--reuse-core') && process.env.YAQMC_ANDROID_NATIVE_LIB_DIR
    ? path.resolve(process.env.YAQMC_ANDROID_NATIVE_LIB_DIR)
    : path.join(android, args.has('--reuse-core') ? 'app' : 'nativeApp', 'build/generated/jniLibs');
const commit = spawnSync('git', ['rev-parse', 'HEAD'], {
  cwd: root,
  encoding: 'utf8',
}).stdout.trim();
if (!/^[0-9a-f]{40}$/u.test(commit)) throw new Error('Cannot determine source commit');
const environment = {
  ...process.env,
  ANDROID_HOME: toolchain.sdk,
  ANDROID_SDK_ROOT: toolchain.sdk,
  ANDROID_NDK_HOME: toolchain.ndk,
  ANDROID_NDK_ROOT: toolchain.ndk,
  YAQMC_ANDROID_NATIVE_LIB_DIR: nativeDir,
  YAQMC_RUSTLS_VERIFIER_MAVEN_DIR: resolveRustlsVerifierMaven(),
  YAQMC_VERSION_NAME: version.versionName,
  YAQMC_VERSION_CODE: String(version.versionCode),
  YAQMC_BUILD_COMMIT: commit,
};
function run(command, commandArgs, cwd = root) {
  const result = spawnSync(command, commandArgs, { cwd, env: environment, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} exited with ${result.status}`);
}
if (!args.has('--reuse-core')) {
  mkdirSync(nativeDir, { recursive: true });
  run('cargo', cargoNdkArguments('release', nativeDir));
}
if (!existsSync(path.join(nativeDir, 'arm64-v8a/libyaqmc_core.so')))
  throw new Error('ARM64 Rust Core is missing');
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
const destination = path.join(output, `YAQMC-${version.versionName}-android-native-${variant}.apk`);
copyFileSync(
  path.join(android, `nativeApp/build/outputs/apk/${variant}/nativeApp-${variant}.apk`),
  destination,
);
process.stdout.write(`Native Android APK: ${destination}\n`);
