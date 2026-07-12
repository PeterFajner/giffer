# Releasing Giffer (iOS)

The App Store distribution path, including the two things that bit us on 1.1.

## 1. Bump the version

In `Giffer.xcodeproj/project.pbxproj` (both Debug and Release configs):

- `MARKETING_VERSION` — the user-facing version, e.g. `1.1`.
- `CURRENT_PROJECT_VERSION` — the build number. **Bump this for every upload**,
  even a re-upload of the same marketing version. App Store Connect rejects a
  build number it has already seen, so if an upload fails validation, the next
  attempt still needs a higher number.

Commit, then tag: `git tag -a vX.Y -m "…" && git push origin vX.Y`.

## 2. Build with a GM Xcode — via Xcode Cloud

**App Store submissions must use the public (GM) Xcode + SDK. Beta Xcode builds
are rejected at "Add for Review."**

On a beta macOS you often can't run the GM Xcode locally (the App Store build
reports "not supported"). Rather than find another Mac, build in **Xcode Cloud**,
which runs a GM toolchain in Apple's cloud regardless of your local OS:

- App Store Connect → your app → **Xcode Cloud → Manage Workflows**.
- **Environment → Xcode Version: Latest Release** (a GM, e.g. 26.x) — **not** a beta.
- **Actions:** the action must be **Archive** (iOS, scheme `Giffer`), with
  **Deployment Preparation = App Store Connect**. A plain **Build** action only
  compiles — it never archives or delivers, so nothing reaches TestFlight or the
  Distribution build picker even though the run shows in the Xcode Cloud tab.
- **Start Condition:** Branch Changes on `main` (or a Tag condition), or Start
  Build manually.

The workflow can be *configured* from a beta Xcode; the build environment Xcode
version is what matters, and it's selected in the workflow, not inherited from
your Mac. Signing is handled automatically by Xcode Cloud.

## 3. Export compliance

`INFOPLIST_KEY_ITSAppUsesNonExemptEncryption = NO` is set in the project — Giffer
does only local Photos-to-GIF work with no non-exempt cryptography. This skips
the compliance prompt so each build becomes selectable as soon as it finishes
processing. Leave it in place.

## 4. Submit

1. Push to `main` → the workflow runs, archives, and delivers.
2. Build appears in **TestFlight**, processes (~15–60 min), and — thanks to the
   encryption key — needs no compliance step.
3. App Store Connect → **Distribution** → create/select the version → add the
   build → fill in "What's New" → **Add for Review** → **Submit for Review**.

## Quick checklist

- [ ] Bump `MARKETING_VERSION` (new version) and `CURRENT_PROJECT_VERSION` (every upload)
- [ ] Commit + tag `vX.Y`, push
- [ ] Xcode Cloud workflow: GM Xcode environment + **Archive** action → App Store Connect
- [ ] Run the workflow (push to `main` or Start Build)
- [ ] Build processes in TestFlight → select under the version → Add for Review → Submit
