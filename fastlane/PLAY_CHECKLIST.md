# Putting Puki on Google Play

Everything Play asks for, in order. The texts and images are in `metadata/android/` next to this
file: `en-US` and `iw-IL` (Play's code for Hebrew). Play's own forms change now and then, so
expect the labels to differ a little.

## 1. The developer account (only the account owner can do this)

- [ ] Sign up at https://play.google.com/console as a **personal** account: a one-time **$25** fee,
      plus identity verification with a government ID.
- [ ] Pick the developer name shown on the store, and a contact email. The email is shown
      publicly, so a separate address is a good idea.

## 2. Create the app

- [ ] Create app: name "Puki: Toddler Games", default language English (en-US) or Hebrew (iw-IL),
      **App**, **Free**. (Free can never be changed to paid, which suits the promise.)
- [ ] Add the second language under the main store listing.

## 3. App signing: keep the same key as the GitHub APKs (important)

Play re-signs what it delivers. If Play makes a new key, the Play version and the GitHub APK
won't install over each other, and switching between them means uninstalling, which loses the
child's progress and voice lines. To avoid that, give Play the existing release key:

- [ ] At the first upload, under **App integrity → App signing**, choose to use your own key
      ("Export and upload a key from Java keystore") and follow Play's PEPK steps with
      `~/keys/puki-release.jks` (alias `puki`). Run it yourself: it asks for the key's password.
- [ ] Play then asks for an **upload key**. Simplest: use the same key (it's the one the
      build already signs with).

The certificate's SHA-256, to check against what Play shows:
`a7:99:ca:b5:bc:f8:3c:20:c7:54:49:0f:8d:0e:b2:71:cf:84:4e:2b:7e:69:51:87:60:78:9f:2a:96:eb:3c:3d`

## 4. The bundle

```bash
./gradlew bundlePlayRelease
```

Upload `app/build/outputs/bundle/playRelease/app-play-release.aab`. This is the `play` flavor:
no self-updater and no REQUEST_INSTALL_PACKAGES (Play doesn't allow apps to update themselves).
It targets API 36, and its native libraries meet the 16 KB page rule.

## 5. Closed testing: 12 testers for 14 days

New personal accounts must run a closed test before going public:

- [ ] Testing → Closed testing → create a track, upload the .aab, and add at least **12 testers**
      (a Google Group or a list of Gmail addresses). Family and friends with Android phones work.
- [ ] Each tester opts in through the link Play gives and **keeps the app installed for 14 days
      in a row**. Ask them to open it now and then; Play asks whether testing was real.
- [ ] After 14 days: **Apply for production** and answer the short questions about the test.

## 6. Store listing

From `metadata/android/<language>/`:

- [ ] App name: `title.txt`. Short description: `short_description.txt`. Full description:
      `full_description.txt`.
- [ ] App icon (512×512): `images/icon.png`.
- [ ] Feature graphic (1024×500): `images/featureGraphic.png` (one per language).
- [ ] Screenshots: `en-US/images/phoneScreenshots/`. Upload the same files as phone, 7-inch and
      10-inch tablet screenshots, for both languages (they're tablet shots, 1340×800).
- [ ] Category: **Games → Educational**. Tags: toddlers, educational, family.
- [ ] Website: https://tomsadeh.github.io/puki/
- [ ] Privacy policy: **https://tomsadeh.github.io/puki/privacy.html**

## 7. App content forms (Policy → App content)

**Privacy policy:** https://tomsadeh.github.io/puki/privacy.html

**Ads:** No, the app contains no ads.

**App access:** All functionality is available without special access. Note for reviewers:
"Every game works on its own. Puki's talking companion is optional: it needs a free program
on the family's own PC (https://github.com/TomSadeh/puki), and without it the parrot uses
recorded lines. Parent settings open by holding the gear button for 2 seconds, then tapping the
number shown in words (e.g. 'seventeen' among six numbers)."

**Target audience and content:** ages **5 and under** (add 6–8 if wanted). This puts the app in
the **Families program**: the Families policy applies (no ads, no data-collecting SDKs, a parent
gate before settings). Puki has no ad or analytics SDKs; its libraries are ZXing (QR codes),
LiteRT-LM and ONNX Runtime (the on-phone brain), and AndroidX.
- Appeal to children: yes.
- Teacher Approved: optional, can be requested later.

**Content rating (IARC questionnaire):** category **Game**. Answer no to violence, fear, sexual
content, gambling, drugs, crude language and controlled substances. For user interaction:
users can't communicate with each other or share content, there's no location sharing and no
purchases. Expected rating: everyone / 3+.

**Data safety:** what the form asks, and Puki's answers. Play counts data as "collected" when it
leaves the device. The child's voice, drawings and name are sent to the family's own PC, not to
the developer, but that PC is off the device, so declare them. Recordings are never kept
(ephemeral); the PC's memory keeps the child's words as text and the name (not ephemeral).

- Does the app collect or share any of the required user data types? **Yes**
- Is all user data encrypted in transit? **Yes** (HTTPS with a pinned certificate)
- Do you provide a way for users to request that their data be deleted? Nothing is kept by the
  developer. Answer per the form's options: uninstalling deletes everything on the device, and
  the family deletes Puki's folder (or its `memory` folder) on their PC.
- Data types, each: collected **yes**, shared **no**, **optional** (only with the companion
  turned on), purpose **App functionality**:
  - **Audio → Voice or sound recordings**: processed ephemerally **yes** (talking to Puki, Say it,
    answering a game out loud, the echo game; no recording is kept)
  - **Photos and videos → Photos**, if Play counts drawings here: ephemeral **yes** (the drawing
    Puki looks at)
  - **Personal info → Name**: ephemeral **no** (the child's name, so Puki can say it; the PC's
    memory may keep it)
  - **App activity → Other user-generated content**: ephemeral **no** (the words the child says,
    as text, in Puki's memory on the family's PC: the log, diary and profile)
- Not collected: location, contacts, identifiers, analytics, crash logs, financial info,
  messages, health, calendar, web history, device IDs.

**News app:** No. **COVID-19:** No. **Government app:** No. **Financial features:** none.
**Health:** none.

**Permissions declarations:** RECORD_AUDIO (talking to Puki, the voice games, and answering a
game's question out loud) and CAMERA
(scanning the pairing QR code). No sensitive-permission forms are needed for the play flavor.

## 8. After it's public

- [ ] Add the Play badge to the website and README (ask Claude).
- [ ] Each release: bump `pukiVersion`, `python scripts/release.py --publish` for GitHub, then
      `./gradlew bundlePlayRelease` and upload the .aab to Play (production or a testing track).
