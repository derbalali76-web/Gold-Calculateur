# Google Play — Data safety form (suggested answers)

> Fill this in Play Console → App content → Data safety. Re-check against the SDK versions you ship and
> Google's current form; Google AdMob / UMP / Play Billing publish their own disclosure guidance.

**Does your app collect or share any of the required user data types?** Yes (through the Google Mobile Ads SDK).

| Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| Device or other IDs (advertising ID) | Yes | Yes (Google AdMob) | Advertising or marketing; Fraud prevention, security, compliance | No for the free version (ads); disappears after "Remove ads" |
| Approximate location (derived from IP by the ad SDK) | Yes | Yes (Google AdMob) | Advertising or marketing | No |
| App interactions / Diagnostics (crash logs, performance) | Yes | Yes (Google AdMob) | Advertising or marketing; Analytics | No |
| Purchase history | Processed by Google Play only | No | App functionality | — |
| Personal info, financial info, contacts, health, messages, photos, files, audio, calendar | **No** | **No** | — | — |
| Ingot weights / karats / history | **Not collected** (stored on-device only, never transmitted) | — | — | — |

**Is all of the user data collected by your app encrypted in transit?** Yes (ad SDK traffic uses HTTPS).
**Do you provide a way for users to request that their data be deleted?** No account data exists; local data is deleted by uninstalling. (Ad-related data: users can reset their advertising ID in Android settings.)
**Privacy policy URL:** host `docs/privacy/index.html` (e.g. GitHub Pages) and enter its URL.
**Ads declaration:** "Yes, my app contains ads".
**Target audience:** 18+ / adults (not designed for children — avoids the Families policy requirements).
**App access:** all functionality available without login.
