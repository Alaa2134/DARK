# Signing the iOS build with your Apple Developer certificate

With a paid Apple Developer account the CI can hand you a **signed IPA that installs directly
and stays valid for a year**, instead of an unsigned one that has to be re-signed every 7 days.

Nothing here needs to be shared with anyone. You add the certificate to your own repository's
secrets; GitHub encrypts them and they are never printed in the logs.

> **Never paste a `.p12`, its password, or a provisioning profile into a chat, an issue, or a
> commit.** A `.p12` contains your private key — anyone holding it can sign software as you.

---

## 1. Export the certificate from Keychain Access

On the Mac that has the certificate:

1. Open **Keychain Access → My Certificates**
2. Find **Apple Distribution: …** (or **Apple Development: …**)
3. Right-click → **Export…** → save as `certificate.p12`
4. Set a password when prompted — you will need it in step 3

## 2. Download the provisioning profile

1. Go to <https://developer.apple.com/account/resources/profiles/list>
2. Create or pick a profile for the bundle identifier you intend to use
   (default here: `com.rocketpocket.car`)
   - **Ad Hoc** — installs on the devices you registered
   - **App Store** — for TestFlight and the App Store
3. Download it — you get a `.mobileprovision` file

Register the iPhone/iPad UDIDs in the portal first, or an Ad Hoc build will not install on them.

## 3. Turn both files into text

GitHub secrets hold text, so base64-encode the two files. In Terminal:

```bash
base64 -i certificate.p12 | pbcopy          # now paste into IOS_CERT_P12_BASE64
base64 -i profile.mobileprovision | pbcopy  # now paste into IOS_PROVISIONING_PROFILE_BASE64
```

`pbcopy` puts the text straight on the clipboard, so nothing is written to a file you might
forget to delete.

## 4. Add the secrets

In the repository: **Settings → Secrets and variables → Actions → New repository secret**

| Secret | What goes in it |
|---|---|
| `IOS_CERT_P12_BASE64` | The base64 text from the `.p12` |
| `IOS_CERT_PASSWORD` | The password you set when exporting it |
| `IOS_PROVISIONING_PROFILE_BASE64` | The base64 text from the `.mobileprovision` |
| `IOS_TEAM_ID` | Your 10-character Team ID, from the top right of the developer portal |

Optionally, under the **Variables** tab:

| Variable | Default | When to change it |
|---|---|---|
| `IOS_BUNDLE_ID` | `com.rocketpocket.car` | Set it to whatever identifier your profile was issued for |
| `IOS_EXPORT_METHOD` | `development` | `ad-hoc` for an Ad Hoc profile, `app-store` for TestFlight |

**The bundle identifier must match the profile exactly**, or signing fails — a profile is issued
for one identifier and will refuse to sign anything else.

## 5. Push anything

The workflow notices `IOS_CERT_P12_BASE64` exists and switches to the signed path by itself.
The signed `RocketPocket.ipa` lands in the same place as before:

**<https://github.com/Alaa2134/DARK/releases/latest>**

Until the secrets are added, the job keeps producing the unsigned IPA, so the build never breaks
while the account is being set up.

---

## Installing the signed IPA

- **Apple Configurator** (Mac) — drag the IPA onto the connected device
- **Xcode → Window → Devices and Simulators** — drag it into "Installed Apps"
- **App Store Connect / TestFlight** — for an `app-store` export

An Ad Hoc build only installs on the device UDIDs registered in the profile at the time it was
created. Adding a new device later means regenerating the profile and updating the secret.

## If signing fails

| Message in the log | Cause |
|---|---|
| `No signing certificate "iOS Distribution" found` | The `.p12` is a Development certificate — set `CODE_SIGN_IDENTITY` to `Apple Development` in the workflow, or export the Distribution one |
| `Provisioning profile ... doesn't match the bundle identifier` | `IOS_BUNDLE_ID` and the profile disagree |
| `The provisioning profile is expired` | Regenerate it in the portal and update the secret |
| `errSecInternalComponent` | The `.p12` password is wrong |
