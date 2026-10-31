import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  resolveAndroidJava,
  resolveAndroidToolchain,
  resolveRustlsVerifierMaven,
} from './android-toolchain.mjs';

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
const environment = {
  ...process.env,
  JAVA_HOME: javaHome,
  ANDROID_HOME: toolchain.sdk,
  ANDROID_SDK_ROOT: toolchain.sdk,
  ANDROID_NDK_HOME: toolchain.ndk,
  ANDROID_NDK_ROOT: toolchain.ndk,
  YAQMC_RUSTLS_VERIFIER_MAVEN_DIR: resolveRustlsVerifierMaven(),
};
// Build inputs are resolved by Gradle on every build, including after Clean Project.
for (const name of [
  'YAQMC_ANDROID_NATIVE_LIB_DIR',
  'YAQMC_VERSION_NAME',
  'YAQMC_VERSION_CODE',
  'YAQMC_BUILD_COMMIT',
]) {
  delete environment[name];
}

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
setLocalProperty(
  path.join(android, 'local.properties'),
  'yaqmc.rustlsVerifierMavenDir',
  environment.YAQMC_RUSTLS_VERIFIER_MAVEN_DIR,
);
setLocalProperty(path.join(android, '.gradle/config.properties'), 'java.home', javaHome);
for (const [key, name] of [
  ['yaqmc.cargoHome', 'CARGO_HOME'],
  ['yaqmc.rustupHome', 'RUSTUP_HOME'],
]) {
  if (process.env[name])
    setLocalProperty(path.join(android, 'local.properties'), key, process.env[name]);
}
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
