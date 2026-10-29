import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

export const ANDROID_NDK_VERSION = '28.2.13676358';
export const ANDROID_RELEASE_ABIS = Object.freeze(['arm64-v8a']);
export const ANDROID_DEBUG_ABIS = Object.freeze(['arm64-v8a', 'x86_64']);
export const ANDROID_JAVA_VERSION = 21;

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const androidRoot = path.join(repositoryRoot, 'apps', 'android', 'android');
const nativeOutput = path.join(androidRoot, 'nativeApp', 'build', 'generated', 'jniLibs');

export function androidSdkCandidates(environment = process.env, platform = process.platform) {
  const candidates = [environment.ANDROID_SDK_ROOT, environment.ANDROID_HOME];
  if (platform === 'win32' && environment.LOCALAPPDATA) {
    candidates.push(path.join(environment.LOCALAPPDATA, 'Android', 'Sdk'));
  } else if (platform === 'darwin' && environment.HOME) {
    candidates.push(path.join(environment.HOME, 'Library', 'Android', 'sdk'));
  } else if (environment.HOME) {
    candidates.push(path.join(environment.HOME, 'Android', 'Sdk'));
  }
  return [...new Set(candidates.filter(Boolean).map((candidate) => path.resolve(candidate)))];
}

export function resolveAndroidToolchain(environment = process.env, platform = process.platform) {
  const sdk = androidSdkCandidates(environment, platform).find((candidate) =>
    existsSync(candidate),
  );
  if (!sdk) {
    throw new Error('Android SDK not found; set ANDROID_SDK_ROOT to the SDK 36 installation');
  }
  const explicitNdk = environment.ANDROID_NDK_HOME ?? environment.ANDROID_NDK_ROOT;
  const ndk = explicitNdk ? path.resolve(explicitNdk) : path.join(sdk, 'ndk', ANDROID_NDK_VERSION);
  if (!existsSync(ndk)) {
    throw new Error(`Android NDK ${ANDROID_NDK_VERSION} not found at ${ndk}`);
  }
  return { sdk, ndk };
}

function javaExecutableFromHome(home, platform) {
  return path.join(home, 'bin', platform === 'win32' ? 'java.exe' : 'java');
}

export function androidJavaCandidates(environment = process.env, platform = process.platform) {
  const homes = [environment.YAQMC_JAVA_HOME];
  if (platform === 'win32') {
    if (environment.LOCALAPPDATA) {
      homes.push(path.join(environment.LOCALAPPDATA, 'Programs', 'Android Studio', 'jbr'));
    }
    if (environment.USERPROFILE) {
      const jdks = path.join(environment.USERPROFILE, '.jdks');
      if (existsSync(jdks)) {
        homes.push(
          ...readdirSync(jdks, { withFileTypes: true })
            .filter((entry) => entry.isDirectory() && entry.name.startsWith('jdk-21'))
            .map((entry) => path.join(jdks, entry.name)),
        );
      }
    }
  } else if (platform === 'darwin') {
    homes.push('/Applications/Android Studio.app/Contents/jbr/Contents/Home');
  } else {
    homes.push('/opt/android-studio/jbr');
  }
  homes.push(environment.JAVA_HOME);
  return [
    ...new Set(
      homes
        .filter(Boolean)
        .map((home) => javaExecutableFromHome(path.resolve(home), platform))
        .filter((executable) => existsSync(executable)),
    ),
    'java',
  ];
}

function javaMajorVersion(executable, environment) {
  const result = spawnSync(executable, ['-version'], {
    env: environment,
    encoding: 'utf8',
  });
  if (result.status !== 0) return undefined;
  const output = `${result.stderr ?? ''}\n${result.stdout ?? ''}`;
  const match = output.match(/version\s+"(?:1\.)?(\d+)/u);
  return match ? Number.parseInt(match[1], 10) : undefined;
}

export function resolveAndroidJava(environment = process.env, platform = process.platform) {
  for (const executable of androidJavaCandidates(environment, platform)) {
    if (javaMajorVersion(executable, environment) === ANDROID_JAVA_VERSION) {
      return executable;
    }
  }
  throw new Error(
    `JDK ${ANDROID_JAVA_VERSION} is required by the Android toolchain; set YAQMC_JAVA_HOME to a JDK ${ANDROID_JAVA_VERSION} installation`,
  );
}

export function cargoNdkArguments(variant, outputDirectory = nativeOutput) {
  const abis = variant === 'release' ? ANDROID_RELEASE_ABIS : ANDROID_DEBUG_ABIS;
  const args = ['ndk', '-P', '26'];
  for (const abi of abis) args.push('-t', abi);
  args.push('-o', outputDirectory, 'build', '--locked', '-p', 'yaqmc-android');
  if (variant === 'release') args.push('--release');
  return args;
}

export function rustlsVerifierMavenFromMetadata(metadata) {
  const verifier = metadata?.packages?.find(
    (candidate) => candidate.name === 'rustls-platform-verifier-android',
  );
  if (!verifier?.manifest_path) {
    throw new Error('rustls-platform-verifier-android is missing from Cargo metadata');
  }
  const repository = path.join(path.dirname(verifier.manifest_path), 'maven');
  if (!existsSync(repository)) {
    throw new Error(`rustls Android Maven repository is missing: ${repository}`);
  }
  return repository;
}

export function resolveRustlsVerifierMaven(environment = process.env) {
  const result = spawnSync('cargo', ['metadata', '--format-version', '1', '--locked'], {
    cwd: repositoryRoot,
    env: environment,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error('cargo metadata failed while locating the Android TLS verifier');
  }
  return rustlsVerifierMavenFromMetadata(JSON.parse(result.stdout));
}
