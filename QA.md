# Giffer — Manual QA Checklist

Run on a real device before each App Store submission. Mark each item Pass / Fail / N/A.

## 1. First launch & home screen
- [ ] App launches to picker screen with no errors
- [ ] No leftover state from previous installs (no remembered configs)
- [ ] `?` button visible on home screen
- [ ] Tapping `?` opens About sheet
- [ ] About sheet shows: app version + build number, GitHub link, author name, privacy policy link
- [ ] GitHub link opens repo in browser
- [ ] Privacy policy link opens privacy doc
- [ ] About sheet dismisses cleanly

## 2. In-app Live Photos picker
- [ ] "Select Live Photo" button shows system PHPicker
- [ ] Picker is filtered to Live Photos only (no stills, no videos visible)
- [ ] Cancel returns to home screen, no errors
- [ ] Selecting a Live Photo shows "Loading…" indicator
- [ ] Editor screen appears once frames are extracted
- [ ] Picker does NOT prompt for Photos permission (PHPicker is out-of-process)

## 3. Playback modes (top bar)
- [ ] Forward: preview plays start → end, loops
- [ ] Reverse: preview plays end → start, loops
- [ ] Bounce: preview plays forward then reverse, loops
- [ ] Switching mode updates preview within ~150ms (debounce)
- [ ] Switching mode triggers re-encode (file size in top bar updates)
- [ ] Exported GIF (saved to Photos) plays in selected mode

## 4. Trim tool
- [ ] Tap Trim: panel slides up, slider visible with frame thumbnails
- [ ] Dragging start handle: preview updates, doesn't go past end
- [ ] Dragging end handle: preview updates, doesn't go before start
- [ ] Trimmed range correctly reflected in exported GIF
- [ ] Tap Trim again with default range: panel collapses, tool disabled
- [ ] Tap Trim again with custom range: panel collapses but trim still applied (state preserved)
- [ ] Trim + Reverse: reversed clip is trimmed correctly
- [ ] Trim + Bounce: bounce occurs over trimmed range only

## 5. Speed (FPS) tool
- [ ] Tap Speed: slider appears, shows "12 fps" by default
- [ ] Indicator dot shows default position (12)
- [ ] Slider range 6–24
- [ ] Higher fps = larger file size in top bar
- [ ] Lower fps = smaller file size
- [ ] Tap Speed again at default 12: panel collapses, tool disables
- [ ] Tap Speed again at custom value: panel collapses but speed still applied

## 6. Size (resolution) tool
- [ ] Tap Size: slider appears
- [ ] Slider range 0.1–1.0
- [ ] Output dimensions in top bar update live as slider moves
- [ ] Smaller scale = smaller file
- [ ] Tap Size again at default 1.0: panel collapses, tool disables
- [ ] Tap Size again at custom value: panel collapses but size still applied

## 7. Crop tool
- [ ] Tap Crop: overlay appears with corner & edge handles
- [ ] Dragging handles resizes crop region
- [ ] Preview shows crop bounds while in crop mode
- [ ] "Reset Crop" button appears once crop is modified
- [ ] Reset Crop returns to full frame
- [ ] Tap Crop again exits crop mode
- [ ] Exiting with custom crop: preview shows cropped output
- [ ] Exported GIF respects crop region
- [ ] Crop + Trim + Speed + Size: all four combine correctly in export

## 8. Tool interaction
- [ ] Multiple tools can be enabled simultaneously
- [ ] Selecting one tool while another is open: switches panel
- [ ] Disabling a tool at default value removes it from enabled set
- [ ] Disabling a tool at custom value: resets that tool's config
- [ ] Re-enabling a previously-customized tool: restores its saved value (within the same editor session)
- [ ] Entering crop mode while a tool is selected: deselects the tool

## 9. Encoding & sharing
- [ ] File size shown in top bar after each encode
- [ ] Dimensions shown match output
- [ ] Encoding spinner visible on share button while encoding
- [ ] Share button is yellow when GIF is ready, gray when not
- [ ] Share button is disabled while encoding
- [ ] Tapping share button opens system share sheet
- [ ] "Save Image" saves animated GIF to Photos
- [ ] GIF in Photos plays animated, not as still
- [ ] Share via Messages: GIF sends and animates on recipient side
- [ ] Share via AirDrop to Mac: file received, plays in Preview/Finder

## 10. No-history verification
- [ ] Open Live Photo A, change settings, exit editor
- [ ] Pick same Live Photo A again: settings are back to defaults (no recall)
- [ ] Force-quit app, relaunch: no recent files, no saved state
- [ ] Reinstall app: no leftover data
- [ ] Inspect app's Documents folder (via Files app or Xcode Organizer): no `saved_configs.json` or similar

## 11. Error states
- [ ] Extraction failure (e.g. corrupted Live Photo): red error text appears
- [ ] Encoding failure: error visible, app doesn't crash
- [ ] Out of disk space mid-export: graceful failure
- [ ] Background app mid-encode then return: encode completes or cancels cleanly

## 12. Performance & edge cases
- [ ] Very short Live Photo (~1s): works without crash
- [ ] Long Live Photo (3s, the iOS max): works smoothly
- [ ] Rapid toggling of playback modes: debounces, no flicker storm
- [ ] Rapid slider drags: debounced re-encode, no piled-up tasks
- [ ] Memory stays reasonable across 10 successive Live Photo edits
- [ ] No frame leaks (verify with Instruments if possible)

## 13. Devices & orientations
- [ ] iPhone (newest supported)
- [ ] iPhone (oldest supported iOS version)
- [ ] iPad (if supported — confirm scope)
- [ ] Portrait orientation: layout correct
- [ ] Landscape orientation: layout doesn't break (or is locked to portrait — confirm)
- [ ] Editor is intentionally dark; picker respects system light/dark
- [ ] Dynamic type at largest setting: no truncation in About sheet, picker, tool labels

## 14. Accessibility
- [ ] VoiceOver reads labels for: ?, Select Live Photo, playback mode buttons, share button, each tool button
- [ ] All tappable targets ≥ 44×44pt
- [ ] Sliders are VoiceOver-adjustable

## 15. App Store Connect prerequisites (off-device)
- [ ] App icon set for all required sizes
- [ ] Launch screen present (or N/A if using SwiftUI default)
- [ ] `CFBundleShortVersionString` (marketing version) set
- [ ] `CFBundleVersion` (build number) set
- [ ] Bundle identifier matches App Store Connect record
- [ ] Privacy nutrition labels filled (Data Not Collected)
- [ ] Export compliance answered (uses only standard iOS encryption)
- [ ] Screenshots prepared for required device sizes
- [ ] Privacy policy URL accessible publicly
- [ ] Support URL accessible publicly
