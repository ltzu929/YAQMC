import { spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  resolveAndroidJava,
  resolveAndroidToolchain,
  resolveRustlsVerifierMaven,
} from './android-toolchain.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const androidRoot = path.join(repositoryRoot, 'apps', 'android', 'android');
const tasks = process.argv.slice(2);

if (tasks.length === 0 || tasks.some((task) => !/^:[a-zA-Z0-9:_-]+$/u.test(task))) {
  throw new Error('one or more explicit Gradle task paths are required');
}

const toolchain = resolveAndroidToolchain();

const environment = {
  ...process.env,
  ANDROID_HOME: toolchain.sdk,
  ANDROID_SDK_ROOT: toolchain.sdk,
  ANDROID_NDK_HOME: toolchain.ndk,
  ANDROID_NDK_ROOT: toolchain.ndk,
  YAQMC_RUSTLS_VERIFIER_MAVEN_DIR: resolveRustlsVerifierMaven(),
};
const wrapperJar = path.join(androidRoot, 'gradle', 'wrapper', 'gradle-wrapper.jar');
const result = spawnSync(
  resolveAndroidJava(environment),
  ['-classpath', wrapperJar, 'org.gradle.wrapper.GradleWrapperMain', '--no-daemon', ...tasks],
  {
    cwd: androidRoot,
    env: environment,
    stdio: 'inherit',
  },
);
if (result.error) throw result.error;
if (result.status !== 0) process.exitCode = result.status ?? 1;
