#!/usr/bin/env node
/**
 * Cross-platform script that bundles the 4 internal modules:
 *   - viet-template-api
 *   - viet-template-runtime
 *   - viet-template-language-vtl
 *   - viet-template-vtl-interpreter
 * into editors/vscode/server/viet-template-lsp.jar with:
 *   Main-Class: io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer
 */

const fs = require('fs');
const path = require('path');
const os = require('os');
const child_process = require('child_process');
const zlib = require('zlib');

const MODULES = [
  'viet-template-api',
  'viet-template-runtime',
  'viet-template-language-vtl',
  'viet-template-vtl-interpreter'
];

const MAIN_CLASS = 'io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer';

function findRepoRoot() {
  let curr = __dirname;
  while (curr && curr !== path.dirname(curr)) {
    if (fs.existsSync(path.join(curr, 'pom.xml')) && fs.existsSync(path.join(curr, 'viet-template-api'))) {
      return curr;
    }
    curr = path.dirname(curr);
  }
  return path.resolve(__dirname, '..', '..');
}

function findJarExecutable() {
  if (process.env.JAVA_HOME) {
    const jarPath = path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'jar.exe' : 'jar');
    if (fs.existsSync(jarPath)) return jarPath;
  }
  try {
    const cmd = process.platform === 'win32' ? 'where jar' : 'which jar';
    const out = child_process.execSync(cmd, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }).trim();
    const firstLine = out.split(/\r?\n/)[0].trim();
    if (firstLine && fs.existsSync(firstLine)) return firstLine;
  } catch (_) {}

  // Check next to java if possible
  try {
    const cmd = process.platform === 'win32' ? 'where java' : 'which java';
    const out = child_process.execSync(cmd, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }).trim();
    const javaBin = out.split(/\r?\n/)[0].trim();
    if (javaBin) {
      const jarCandidate = path.join(path.dirname(javaBin), process.platform === 'win32' ? 'jar.exe' : 'jar');
      if (fs.existsSync(jarCandidate)) return jarCandidate;
    }
  } catch (_) {}

  return 'jar';
}

function findModuleClassesOrJar(repoRoot, moduleName) {
  // Check Maven target/classes
  const mavenClasses = path.join(repoRoot, moduleName, 'target', 'classes');
  if (fs.existsSync(mavenClasses)) {
    return { type: 'dir', path: mavenClasses };
  }

  // Check Gradle build/classes/java/main
  const gradleClasses = path.join(repoRoot, moduleName, 'build', 'classes', 'java', 'main');
  if (fs.existsSync(gradleClasses)) {
    return { type: 'dir', path: gradleClasses };
  }

  // Check target jar
  const mavenTarget = path.join(repoRoot, moduleName, 'target');
  if (fs.existsSync(mavenTarget)) {
    const files = fs.readdirSync(mavenTarget);
    const jar = files.find(f => f.startsWith(moduleName) && f.endsWith('.jar') && !f.endsWith('-sources.jar') && !f.endsWith('-javadoc.jar') && !f.endsWith('-tests.jar'));
    if (jar) return { type: 'jar', path: path.join(mavenTarget, jar) };
  }

  // Check gradle build/libs
  const gradleLibs = path.join(repoRoot, moduleName, 'build', 'libs');
  if (fs.existsSync(gradleLibs)) {
    const files = fs.readdirSync(gradleLibs);
    const jar = files.find(f => f.startsWith(moduleName) && f.endsWith('.jar') && !f.endsWith('-sources.jar') && !f.endsWith('-javadoc.jar') && !f.endsWith('-tests.jar'));
    if (jar) return { type: 'jar', path: path.join(gradleLibs, jar) };
  }

  throw new Error(`Could not find compiled classes or JAR for module: ${moduleName}`);
}

function copyDirRecursive(src, dest) {
  if (!fs.existsSync(dest)) {
    fs.mkdirSync(dest, { recursive: true });
  }
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDirRecursive(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

// Pure JS ZIP writer fallback
const CRC_TABLE = new Int32Array(256);
for (let i = 0; i < 256; i++) {
  let c = i;
  for (let k = 0; k < 8; k++) {
    c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
  }
  CRC_TABLE[i] = c;
}

function calculateCrc32(buf) {
  let crc = -1;
  for (let i = 0; i < buf.length; i++) {
    crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ buf[i]) & 0xFF];
  }
  return (crc ^ (-1)) >>> 0;
}

function buildJarPureJs(stagingDir, destJar) {
  const fileEntries = [];

  function collect(dir, relPath) {
    const items = fs.readdirSync(dir, { withFileTypes: true });
    // sort deterministically
    items.sort((a, b) => a.name.localeCompare(b.name));
    for (const item of items) {
      const full = path.join(dir, item.name);
      const rel = relPath ? `${relPath}/${item.name}` : item.name;
      if (item.isDirectory()) {
        fileEntries.push({ name: rel + '/', isDir: true, data: Buffer.alloc(0) });
        collect(full, rel);
      } else {
        fileEntries.push({ name: rel, isDir: false, data: fs.readFileSync(full) });
      }
    }
  }

  collect(stagingDir, '');

  const localHeaders = [];
  const centralHeaders = [];
  let currentOffset = 0;

  for (const entry of fileEntries) {
    const nameBuf = Buffer.from(entry.name, 'utf8');
    const isDir = entry.isDir;
    const rawData = entry.data;
    const crc = calculateCrc32(rawData);

    // Deflate if not empty and non-directory
    let compressedData = rawData;
    let method = 0; // STORE
    if (!isDir && rawData.length > 0) {
      const deflated = zlib.deflateRawSync(rawData);
      if (deflated.length < rawData.length) {
        compressedData = deflated;
        method = 8; // DEFLATE
      }
    }

    // Local file header (30 bytes + name)
    const localHeader = Buffer.alloc(30 + nameBuf.length);
    localHeader.writeUInt32LE(0x04034b50, 0); // Signature
    localHeader.writeUInt16LE(20, 4); // Min version 2.0
    localHeader.writeUInt16LE(0x0800, 6); // Flags: UTF-8
    localHeader.writeUInt16LE(method, 8); // Compression method
    localHeader.writeUInt16LE(0, 10); // Time
    localHeader.writeUInt16LE(0x21, 12); // Date
    localHeader.writeUInt32LE(crc, 14); // CRC-32
    localHeader.writeUInt32LE(compressedData.length, 18); // Compressed size
    localHeader.writeUInt32LE(rawData.length, 22); // Uncompressed size
    localHeader.writeUInt16LE(nameBuf.length, 26); // Name length
    localHeader.writeUInt16LE(0, 28); // Extra length
    nameBuf.copy(localHeader, 30);

    // Central directory header (46 bytes + name)
    const centralHeader = Buffer.alloc(46 + nameBuf.length);
    centralHeader.writeUInt32LE(0x02014b50, 0); // Signature
    centralHeader.writeUInt16LE(20, 4); // Version made by
    centralHeader.writeUInt16LE(20, 6); // Min version
    centralHeader.writeUInt16LE(0x0800, 8); // Flags: UTF-8
    centralHeader.writeUInt16LE(method, 10); // Compression method
    centralHeader.writeUInt16LE(0, 12); // Time
    centralHeader.writeUInt16LE(0x21, 14); // Date
    centralHeader.writeUInt32LE(crc, 16); // CRC-32
    centralHeader.writeUInt32LE(compressedData.length, 20); // Compressed size
    centralHeader.writeUInt32LE(rawData.length, 24); // Uncompressed size
    centralHeader.writeUInt16LE(nameBuf.length, 28); // Name length
    centralHeader.writeUInt16LE(0, 30); // Extra field length
    centralHeader.writeUInt16LE(0, 32); // Comment length
    centralHeader.writeUInt16LE(0, 34); // Disk start
    centralHeader.writeUInt16LE(0, 36); // Internal attrs
    centralHeader.writeUInt32LE(isDir ? 0x10 : 0, 38); // External attrs
    centralHeader.writeUInt32LE(currentOffset, 42); // Relative offset
    nameBuf.copy(centralHeader, 46);

    localHeaders.push(localHeader, compressedData);
    centralHeaders.push(centralHeader);

    currentOffset += localHeader.length + compressedData.length;
  }

  const centralOffset = currentOffset;
  let centralSize = 0;
  for (const ch of centralHeaders) centralSize += ch.length;

  // End of Central Directory (22 bytes)
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); // Signature
  eocd.writeUInt16LE(0, 4); // Disk number
  eocd.writeUInt16LE(0, 6); // Central directory disk
  eocd.writeUInt16LE(fileEntries.length, 8); // Entries on disk
  eocd.writeUInt16LE(fileEntries.length, 10); // Total entries
  eocd.writeUInt32LE(centralSize, 12); // Central dir size
  eocd.writeUInt32LE(centralOffset, 16); // Central dir offset
  eocd.writeUInt16LE(0, 20); // Comment length

  const allBuffers = [...localHeaders, ...centralHeaders, eocd];
  fs.writeFileSync(destJar, Buffer.concat(allBuffers));
}

function bundle() {
  const repoRoot = findRepoRoot();
  const serverDir = path.resolve(__dirname, '..', 'server');
  const destJar = path.join(serverDir, 'viet-template-lsp.jar');

  console.log(`[bundle-server] Repository root: ${repoRoot}`);
  console.log(`[bundle-server] Output JAR: ${destJar}`);

  if (!fs.existsSync(serverDir)) {
    fs.mkdirSync(serverDir, { recursive: true });
  }

  const stagingDir = fs.mkdtempSync(path.join(os.tmpdir(), 'vtl-lsp-bundle-'));
  try {
    for (const mod of MODULES) {
      const source = findModuleClassesOrJar(repoRoot, mod);
      console.log(`[bundle-server] Staging module ${mod} from ${source.path}`);
      if (source.type === 'dir') {
        copyDirRecursive(source.path, stagingDir);
      } else {
        // Extract jar
        const jarBin = findJarExecutable();
        try {
          child_process.execFileSync(jarBin, ['xf', source.path], { cwd: stagingDir, stdio: 'ignore' });
        } catch (_) {
          // If jar xf fails, try unzip
          child_process.execFileSync('unzip', ['-q', '-o', source.path, '-d', stagingDir], { stdio: 'ignore' });
        }
      }
    }

    // Write MANIFEST.MF
    const metaInf = path.join(stagingDir, 'META-INF');
    if (!fs.existsSync(metaInf)) {
      fs.mkdirSync(metaInf, { recursive: true });
    }
    const manifestContent = `Manifest-Version: 1.0\r\nMain-Class: ${MAIN_CLASS}\r\nCreated-By: viet-template-bundle-server\r\n\r\n`;
    const manifestPath = path.join(metaInf, 'MANIFEST.MF');
    fs.writeFileSync(manifestPath, manifestContent);

    // Try packaging with jar tool first
    let packaged = false;
    const jarBin = findJarExecutable();
    try {
      if (jarBin) {
        console.log(`[bundle-server] Creating jar using ${jarBin}`);
        const args = ['--create', '--file', destJar, '--manifest', manifestPath, '-C', stagingDir, '.'];
        const res = child_process.spawnSync(jarBin, args, { stdio: 'inherit' });
        if (res.status === 0 && fs.existsSync(destJar) && fs.statSync(destJar).size > 0) {
          packaged = true;
        } else {
          // Try legacy jar cfm syntax
          const resLegacy = child_process.spawnSync(jarBin, ['cfm', destJar, manifestPath, '-C', stagingDir, '.'], { stdio: 'inherit' });
          if (resLegacy.status === 0 && fs.existsSync(destJar) && fs.statSync(destJar).size > 0) {
            packaged = true;
          }
        }
      }
    } catch (err) {
      console.warn(`[bundle-server] Failed to invoke jar tool: ${err.message}`);
    }

    if (!packaged) {
      console.log(`[bundle-server] Fallback: Creating jar using pure Node.js ZIP generator`);
      buildJarPureJs(stagingDir, destJar);
    }

    const stat = fs.statSync(destJar);
    console.log(`[bundle-server] Successfully bundled server jar (${stat.size} bytes): ${destJar}`);
  } finally {
    try {
      fs.rmSync(stagingDir, { recursive: true, force: true });
    } catch (_) {}
  }
}

if (require.main === module) {
  bundle();
}

module.exports = { bundle };
