import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

import { checkPublicDocumentation } from '../check-public-docs.mjs';

const temporaryParent = path.resolve(
  import.meta.dirname,
  '../../artifacts/android-native-comparison',
);
const primaryLinks = [
  '[项目规划](apps/android/android/docs/project-plan.md)',
  '[开发指南](apps/android/android/docs/development.md)',
  '[迁移清单](apps/android/android/docs/native-parity.md)',
].join('\n');

async function fixture(t, extraFiles = {}) {
  await mkdir(temporaryParent, { recursive: true });
  const root = await mkdtemp(path.join(temporaryParent, 'docs-check-'));
  t.after(async () => {
    assert.equal(path.dirname(path.resolve(root)), temporaryParent);
    assert.ok(path.basename(root).startsWith('docs-check-'));
    await rm(root, { recursive: true, force: true });
  });
  const files = {
    'README.md': `# Android 客户端\n\n仓库版本 0.1.1，当前不维护双语文档。\n${primaryLinks}\n`,
    'AGENTS.md': primaryLinks,
    LICENSE: 'Test fixture license',
    'apps/android/android/docs/project-plan.md': '# 项目规划\n',
    'apps/android/android/docs/development.md': '# 开发指南\n',
    'apps/android/android/docs/native-parity.md': '# 迁移清单\n',
    ...extraFiles,
  };
  for (const [relative, content] of Object.entries(files)) {
    if (content === null) continue;
    await mkdir(path.dirname(path.join(root, relative)), { recursive: true });
    await writeFile(path.join(root, relative), content);
  }
  return root;
}

test('accepts Chinese-only Android docs without legacy release pages or English copies', async (t) => {
  const root = await fixture(t);
  assert.deepEqual(await checkPublicDocumentation(root), { errors: [], checkedDocuments: 5 });
});

test('requires the migration ledger and reports a missing primary document', async (t) => {
  const root = await fixture(t, { 'apps/android/android/docs/native-parity.md': null });
  const { errors } = await checkPublicDocumentation(root);
  assert.ok(errors.includes('Missing required file: apps/android/android/docs/native-parity.md'));
});

test('requires the README and agent entry point to expose the active guides', async (t) => {
  const root = await fixture(t, { 'README.md': '# Android\n', 'AGENTS.md': '# 开发入口\n' });
  const { errors } = await checkPublicDocumentation(root);
  for (const entry of ['README.md', 'AGENTS.md']) {
    assert.ok(
      errors.includes(
        `${entry}: missing primary document link apps/android/android/docs/development.md`,
      ),
    );
  }
});

test('checks retained and newly added references without requiring a translated counterpart', async (t) => {
  const root = await fixture(t, {
    'docs/retained.md': '[旧参考](missing.md)\n',
    'apps/android/android/docs/reference/notes.md': '[新参考](unknown.md)\n',
  });
  const { errors } = await checkPublicDocumentation(root);
  assert.deepEqual(errors.sort(), [
    'apps/android/android/docs/reference/notes.md: missing local link target unknown.md',
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
