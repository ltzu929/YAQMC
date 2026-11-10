import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

import { checkPublicDocumentation } from '../check-public-docs.mjs';

const temporaryParent = path.resolve(
  import.meta.dirname,
  '../../artifacts/android-native-comparison',
);
const primaryLinks = '[开发指南](docs/development.md)';

async function fixture(t, extraFiles = {}, useGit = true) {
  await mkdir(temporaryParent, { recursive: true });
  const root = await mkdtemp(path.join(temporaryParent, 'docs-check-'));
  t.after(async () => {
    assert.equal(path.dirname(path.resolve(root)), temporaryParent);
    assert.ok(path.basename(root).startsWith('docs-check-'));
    await rm(root, { recursive: true, force: true });
  });
  const files = {
    'README.md': `# Android 客户端\n\n仓库版本 0.1.1，当前不维护双语文档。\n${primaryLinks}\n`,
    '.gitignore': '/AGENTS.md\n/docs/project-plan.md\n/apps/android/docs/native-parity.md\n',
    LICENSE: 'Test fixture license',
    'docs/development.md': '# 开发指南\n',
    ...extraFiles,
  };
  for (const [relative, content] of Object.entries(files)) {
    if (content === null) continue;
    await mkdir(path.dirname(path.join(root, relative)), { recursive: true });
    await writeFile(path.join(root, relative), content);
  }
  if (useGit) {
    execFileSync('git', ['init', '--quiet'], { cwd: root });
    execFileSync('git', ['-c', 'core.autocrlf=false', 'add', '.'], { cwd: root });
  }
  return root;
}

test('accepts Chinese-only Android docs without legacy release pages or English copies', async (t) => {
  const root = await fixture(t);
  assert.deepEqual(await checkPublicDocumentation(root), { errors: [], checkedDocuments: 2 });
});

test('requires the build guide in a checkout without local planning documents', async (t) => {
  const root = await fixture(t, { 'docs/development.md': null });
  const { errors } = await checkPublicDocumentation(root);
  assert.ok(errors.includes('Missing required file: docs/development.md'));
});

test('requires the README to expose the build guide', async (t) => {
  const root = await fixture(t, { 'README.md': '# Android\n' });
  const { errors } = await checkPublicDocumentation(root);
  assert.ok(errors.includes('README.md: missing primary document link docs/development.md'));
});

test('checks a relocated guide before the deleted legacy path is staged', async (t) => {
  const legacyGuide = 'apps/android/android/docs/development.md';
  const root = await fixture(t, { [legacyGuide]: '# Previous guide\n' });
  await rm(path.join(root, legacyGuide));
  assert.deepEqual(await checkPublicDocumentation(root), { errors: [], checkedDocuments: 2 });
});

test('ignores local planning and agent files even when they exist', async (t) => {
  const root = await fixture(t, {
    'AGENTS.md': '[PROJECT_NAME]\n[本机入口](missing.md)\n',
    'docs/project-plan.md': 'C:\\Users\\example\\notes.md\n',
    'apps/android/docs/native-parity.md': '[截图](missing.png)\n',
  });
  assert.deepEqual(await checkPublicDocumentation(root), { errors: [], checkedDocuments: 2 });
});

test('rejects public links to files that only exist in the ignored local workspace', async (t) => {
  const root = await fixture(t, {
    'README.md': `${primaryLinks}\n[计划](docs/project-plan.md)\n`,
    'docs/project-plan.md': '# 本机计划\n',
  });
  const { errors } = await checkPublicDocumentation(root);
  assert.deepEqual(errors, ['README.md: local link target is not published docs/project-plan.md']);
});

test('accepts directory links when their source files are published', async (t) => {
  const root = await fixture(t, {
    'docs/reference.md': '[源码](../crates/example)\n',
    'crates/example/lib.rs': '// Fixture\n',
  });
  assert.deepEqual(await checkPublicDocumentation(root), { errors: [], checkedDocuments: 3 });
});

test('also checks exported source without Git metadata', async (t) => {
  const root = await fixture(t, {}, false);
  assert.deepEqual(await checkPublicDocumentation(root), { errors: [], checkedDocuments: 2 });
});

test('checks retained and newly added references without requiring a translated counterpart', async (t) => {
  const root = await fixture(t, {
    'docs/retained.md': '[旧参考](missing.md)\n',
    'apps/android/docs/reference/notes.md': '[新参考](unknown.md)\n',
  });
  const { errors } = await checkPublicDocumentation(root);
  assert.deepEqual(errors.sort(), [
    'apps/android/docs/reference/notes.md: missing local link target unknown.md',
    'docs/retained.md: missing local link target missing.md',
  ]);
});

test('accepts encoded local filenames and fragments while rejecting malformed link encoding', async (t) => {
  const root = await fixture(t, {
    'docs/reference.md': '[附注](notes%20one.md#details)\n[错误](bad%ZZ.md)\n',
    'docs/notes one.md': '# Details\n',
  });
  const { errors } = await checkPublicDocumentation(root);
  assert.deepEqual(errors, ['docs/reference.md: malformed local link bad%ZZ.md']);
});

test('keeps publication hygiene checks for optional reference pages', async (t) => {
  const root = await fixture(t, {
    'docs/reference.md': '[PROJECT_NAME]\nC:\\Users\\example\\notes.md\n',
  });
  const { errors } = await checkPublicDocumentation(root);
  assert.ok(errors.includes('docs/reference.md: contains public placeholder'));
  assert.ok(
    errors.includes('docs/reference.md: contains workstation-specific absolute Windows path'),
  );
});
