import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { ANDROID_NDK_VERSION, androidSdkCandidates } from '../android-toolchain.mjs';

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
