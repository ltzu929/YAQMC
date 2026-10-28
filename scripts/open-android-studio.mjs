import { spawn, spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  resolveAndroidJava,
  resolveAndroidToolchain,
  resolveRustlsVerifierMaven,
} from './build-android.mjs';
import { rootVersion } from './ci/android-version.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const android = path.join(root, 'apps/android/android');
const args = process.argv.slice(2);
const prepareOnly = args.includes('--prepare-only');
const executableArgs = args.filter((arg) => arg !== '--prepare-only');
if (executableArgs.length !== 1 || !existsSync(executableArgs[0])) {
  throw new Error('Usage: npm run android:studio -- <Studio executable> [--prepare-only]');
}
const executable = path.resolve(executableArgs[0]);
const toolchain = resolveAndroidToolchain();
const java = resolveAndroidJava();
if (java === 'java') {
  throw new Error('Set YAQMC_JAVA_HOME to the JDK 21 directory before opening Studio');
}
const javaHome = path.dirname(path.dirname(java));
const version = rootVersion(root);
const revision = spawnSync('git', ['rev-parse', '--verify', 'HEAD'], {
  cwd: root,
  encoding: 'utf8',
});
const commit = revision.stdout?.trim();
if (revision.status !== 0 || !/^[0-9a-f]{40}$/u.test(commit ?? '')) {
  throw new Error('Unable to resolve the build commit');
}
const nativeSource = path.resolve(
  process.env.YAQMC_ANDROID_NATIVE_LIB_DIR ??
    path.join(android, 'nativeApp/build/generated/jniLibs'),
);
const abi = process.env.YAQMC_ANDROID_NATIVE_ABI ?? 'arm64-v8a';
const nativeLibrary = path.join(nativeSource, abi, 'libyaqmc_core.so');
if (!existsSync(nativeLibrary)) {
  throw new Error('Build a matching Rust Core first, or set YAQMC_ANDROID_NATIVE_LIB_DIR');
}
// Gradle clean removes module build directories, so keep IDE inputs outside them.
const nativeDir = path.join(android, '.gradle/yaqmc-studio/jniLibs');
const environment = {
  ...process.env,
  JAVA_HOME: javaHome,
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
const stagedLibrary = path.join(nativeDir, abi, 'libyaqmc_core.so');
mkdirSync(path.dirname(stagedLibrary), { recursive: true });
if (nativeLibrary !== stagedLibrary) copyFileSync(nativeLibrary, stagedLibrary);

// These machine paths belong only in ignored local files. Preserve other properties.
function setLocalProperty(file, key, value) {
  mkdirSync(path.dirname(file), { recursive: true });
  const lines = existsSync(file) ? readFileSync(file, 'utf8').split(/\r?\n/u) : [];
  const remaining = lines.filter((line) => !line.trimStart().startsWith(`${key}=`));
  while (remaining.at(-1) === '') remaining.pop();
  const escaped = value.replaceAll('\\', '/').replaceAll(':', '\\:');
  writeFileSync(file, [...remaining, `${key}=${escaped}`, ''].join('\n'));
}
setLocalProperty(path.join(android, 'local.properties'), 'sdk.dir', toolchain.sdk);
setLocalProperty(path.join(android, '.gradle/config.properties'), 'java.home', javaHome);
const gradleSettings = path.join(android, '.idea/gradle.xml');
if (!existsSync(gradleSettings)) {
  mkdirSync(path.dirname(gradleSettings), { recursive: true });
  writeFileSync(
    gradleSettings,
    `<project version="4">
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="externalProjectPath" value="$PROJECT_DIR$" />
        <option name="gradleJvm" value="#GRADLE_LOCAL_JAVA_HOME" />
      </GradleProjectSettings>
    </option>
  </component>
</project>
`,
  );
}
process.stdout.write(
  `Android project: ${android}\nGradle JDK: ${javaHome}\nSDK: ${toolchain.sdk}\n`,
);
if (!prepareOnly) {
  const child = spawn(executable, [android], {
    cwd: root,
    env: environment,
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  });
  child.on('error', (error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
  child.unref();
}
