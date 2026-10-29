import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import {
  ANDROID_NDK_VERSION,
  ANDROID_RELEASE_ABIS,
  ANDROID_DEBUG_ABIS,
  cargoNdkArguments,
  androidSdkCandidates,
} from '../android-toolchain.mjs';

test('Android build plan fixes the release and debug ABI sets', () => {
  assert.deepEqual(ANDROID_RELEASE_ABIS, ['arm64-v8a']);
  assert.deepEqual(ANDROID_DEBUG_ABIS, ['arm64-v8a', 'x86_64']);
  assert.deepEqual(cargoNdkArguments('release', 'native-out'), [
    'ndk',
    '-P',
    '26',
    '-t',
    'arm64-v8a',
    '-o',
    'native-out',
    'build',
    '--locked',
    '-p',
    'yaqmc-android',
    '--release',
  ]);
});

test('debug invokes cargo-ndk at API 26 for both supported development ABIs', () => {
  assert.deepEqual(cargoNdkArguments('debug', 'native-out').slice(0, 8), [
    'ndk',
    '-P',
    '26',
    '-t',
    'arm64-v8a',
    '-t',
    'x86_64',
    '-o',
  ]);
});

test('SDK discovery prioritizes explicit configuration and has a Windows fallback', () => {
  const candidates = androidSdkCandidates(
    {
      ANDROID_SDK_ROOT: 'D:\\sdk-explicit',
      ANDROID_HOME: 'D:\\sdk-legacy',
      LOCALAPPDATA: 'C:\\Users\\tester\\AppData\\Local',
    },
    'win32',
  );
  assert.equal(candidates[0], path.resolve('D:\\sdk-explicit'));
  assert.equal(candidates[1], path.resolve('D:\\sdk-legacy'));
  assert.equal(candidates[2], path.resolve('C:\\Users\\tester\\AppData\\Local', 'Android', 'Sdk'));
  assert.equal(ANDROID_NDK_VERSION, '28.2.13676358');
});
