import { access, readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const primaryDocuments = [
  'README.md',
  'docs/zh-CN/project-plan.md',
  'docs/zh-CN/development.md',
  'docs/zh-CN/native-parity.md',
];
const supplementaryDocuments = [
  '.github/PULL_REQUEST_TEMPLATE.md',
  'apps/android/README.md',
  'apps/android/JNI_CONTRACT.md',
  'apps/android/android/nativeApp/README.md',
  'examples/plugins/README.md',
  'examples/plugins/provider-catalog-rust/README.md',
  'examples/plugins/provider-platform-rust/README.md',
];
const forbiddenContent = [
  {
    pattern: /\[PROJECT_NAME\]|\$PROJECT_NAME_API_TOKEN|GPT-Read-?me\.md/iu,
    label: 'public placeholder',
  },
  {
    pattern: /[A-Z]:\\(?:Users|Downloads|Documents|YAQMC)(?:\\|\b)/iu,
    label: 'workstation-specific absolute Windows path',
  },
  { pattern: /\/(?:home|Users)\/[^/\s)]+/u, label: 'workstation-specific absolute user path' },
  { pattern: /\bHANDOFF[_-]\d{4}/iu, label: 'internal handoff reference' },
];

async function exists(filePath) {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function collectMarkdown(root, directory = '', recursive = false) {
  const absolute = path.join(root, directory);
  if (!(await exists(absolute))) return [];
  const documents = [];
  for (const entry of await readdir(absolute, { withFileTypes: true })) {
    const relative = path.posix.join(directory, entry.name);
    if (entry.isFile() && entry.name.endsWith('.md')) documents.push(relative);
    else if (recursive && entry.isDirectory()) {
      documents.push(...(await collectMarkdown(root, relative, true)));
    }
  }
  return documents;
}

export async function checkPublicDocumentation(root = repositoryRoot) {
  const errors = [];
  for (const required of [...primaryDocuments, 'AGENTS.md', 'LICENSE']) {
    if (!(await exists(path.join(root, required))))
      errors.push(`Missing required file: ${required}`);
  }

  const documents = [
    ...(await collectMarkdown(root)),
    ...(await collectMarkdown(root, 'docs', true)),
  ];
  for (const relative of supplementaryDocuments) {
    if (await exists(path.join(root, relative))) documents.push(relative);
  }

  for (const relative of documents) {
    const absolute = path.join(root, relative);
    const markdown = await readFile(absolute, 'utf8');
    for (const { pattern, label } of forbiddenContent) {
      if (pattern.test(markdown)) errors.push(`${relative}: contains ${label}`);
    }
    for (const match of markdown.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) {
      const target = match[1]
        .trim()
        .replace(/^<|>$/g, '')
        .split(/\s+['"]/u, 1)[0];
      if (!target || target.startsWith('#') || /^[a-z][a-z+.-]*:/iu.test(target)) continue;
      const local = target.split('#', 1)[0].split('?', 1)[0];
      if (!local) continue;
      let decoded;
      try {
        decoded = decodeURIComponent(local);
      } catch {
        errors.push(`${relative}: malformed local link ${target}`);
        continue;
      }
      if (!(await exists(path.resolve(path.dirname(absolute), decoded)))) {
        errors.push(`${relative}: missing local link target ${target}`);
      }
    }

    if (relative === 'README.md' || relative === 'AGENTS.md') {
      for (const primary of primaryDocuments.slice(1)) {
        if (!markdown.includes(`](${primary})`)) {
          errors.push(`${relative}: missing primary document link ${primary}`);
        }
      }
    }
  }

  return { errors, checkedDocuments: documents.length };
}

if (Boolean(process.argv[1]) && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { errors, checkedDocuments } = await checkPublicDocumentation();
  if (errors.length > 0) {
    process.stderr.write(`Documentation validation failed (${errors.length}):\n`);
    for (const error of errors) process.stderr.write(`- ${error}\n`);
    process.exitCode = 1;
  } else {
    process.stdout.write(
      `Documentation validation passed: 4 primary documents; ${checkedDocuments} existing Markdown files checked.\n`,
    );
  }
}
