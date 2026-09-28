// Builds a signed release APK of Remind Me There. Used by the GitHub Action and on your laptop.
// Needs these environment variables:
//   KEYSTORE_PATH      path to the .jks signing key
//   KEYSTORE_PASSWORD  its password
//   BUILD_NUMBER       a number that goes up every build (GitHub supplies it)
//   ANDROID_HOME       the Android SDK
import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const APK_NAME = "remind-me-there.apk";
const ICON = "icon-512.png";
const ICON_BG = "#F4F4F8";
// Website files that go inside the app (it works offline and uses the phone's features)
const WEB_FILES = ["index.html", "sw.js", "manifest.webmanifest", "icon-192.png", "icon-512.png", "apple-touch-icon.png", "favicon-32.png"];
// Version 1.1 (code 2) was installed by hand before the automatic builds, so start above it
const VERSION_CODE_START = 100;

const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, "$1"));
const repo = path.resolve(here, "..");
const win = process.platform === "win32";
const run = (cmd, cwd = here) => execSync(cmd, { cwd, stdio: "inherit" });
const need = (name) => {
  if (!process.env[name]) throw new Error(`Missing environment variable ${name}`);
  return process.env[name];
};

const keystore = path.resolve(need("KEYSTORE_PATH"));
need("KEYSTORE_PASSWORD");
const build = parseInt(process.env.BUILD_NUMBER || "1", 10);

// 1. Copy the web app into www, then into the Android project
fs.rmSync(path.join(here, "www"), { recursive: true, force: true });
fs.mkdirSync(path.join(here, "www"));
for (const f of WEB_FILES) fs.copyFileSync(path.join(repo, f), path.join(here, "www", f));
run("npx cap sync android");

// 2. App icon
fs.mkdirSync(path.join(here, "assets"), { recursive: true });
fs.copyFileSync(path.join(repo, ICON), path.join(here, "assets", "icon-only.png"));
fs.copyFileSync(path.join(repo, ICON), path.join(here, "assets", "icon-foreground.png"));
const { default: sharp } = await import("sharp");
await sharp({ create: { width: 1024, height: 1024, channels: 3, background: ICON_BG } })
  .png().toFile(path.join(here, "assets", "icon-background.png"));
run(`npx capacitor-assets generate --android --iconBackgroundColor "${ICON_BG}" --splashBackgroundColor "${ICON_BG}"`);

// 3. Version number: must go up every release so updates install over the old app
const gradleFile = path.join(here, "android", "app", "build.gradle");
let gradle = fs.readFileSync(gradleFile, "utf8");
gradle = gradle.replace(/versionCode \d+/, `versionCode ${VERSION_CODE_START + build}`)
               .replace(/versionName "[^"]*"/, `versionName "1.${build + 1}"`);
fs.writeFileSync(gradleFile, gradle);

// 4. Build
const androidDir = path.join(here, "android");
run(`"${path.join(androidDir, win ? "gradlew.bat" : "gradlew")}" assembleRelease`, androidDir);

// 5. Sign (the password is passed through an environment variable, never on the command line)
const tools = path.join(need("ANDROID_HOME"), "build-tools");
const latest = fs.readdirSync(tools).sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).pop();
const tool = (name) => `"${path.join(tools, latest, win ? `${name}${name === "zipalign" ? ".exe" : ".bat"}` : name)}"`;
const out = path.join(androidDir, "app", "build", "outputs", "apk", "release");
const aligned = path.join(out, "app-release-aligned.apk");
const final = path.join(here, APK_NAME);
fs.rmSync(aligned, { force: true });
run(`${tool("zipalign")} -p -f 4 "${path.join(out, "app-release-unsigned.apk")}" "${aligned}"`);
run(`${tool("apksigner")} sign --ks "${keystore}" --ks-key-alias release ` +
    `--ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD --out "${final}" "${aligned}"`);
run(`${tool("apksigner")} verify "${final}"`);
console.log(`\nDone: android-app/${APK_NAME} (version 1.${build + 1})`);
