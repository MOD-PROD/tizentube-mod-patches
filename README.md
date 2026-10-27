# TizenTube MOD Patches

Bundle de 4 patches pour **TizenTube Cobalt 2.0.3** (`io.gh.reisxd.tizentube.cobalt`, APK unique), compatible avec Morphe (patcher 2.0.0).

## Structure

```
tizentube-mod-patches/
├── settings.gradle.kts            # plugin app.morphe.patches 2.0.0 + dépôts (GitHub Packages, JitPack)
├── build.gradle.kts
├── gradle.properties              # version du bundle
├── gradle/libs.versions.toml      # morphe-patcher, morphe-patches-library, smali
├── patches-bundle.json            # rempli par la CI (release.yml) pour Morphe Manager
├── patches-list.json              # généré par :patches:generatePatchesList
├── branding-src/                  # images maîtres + script de génération des densités
│   ├── icon_master.png
│   ├── banner_master.png
│   └── make_branding.py
├── extensions/extension/          # code Java injecté dans l'APK (→ extensions/extension.mpe)
│   └── src/main/java/app/gonzo/extension/
│       └── adblock/CobaltUserscriptInjector.java
└── patches/
    ├── build.gradle.kts           # about{} + dépendance morphe-patches-library
    └── src/main/
        ├── kotlin/app/gonzo/patches/
        │   ├── shared/Constants.kt                       # compatibilité TizenTube 2.0.3
        │   ├── branding/ModIconAndBannerPatch.kt         # 1
        │   ├── branding/ChangeAppNamePatch.kt            # 4
        │   ├── clone/CloneAppPatch.kt                    # 2
        │   └── ads/Fingerprints.kt + InjectAdblockerUserscriptPatch.kt  # 3
        └── resources/tizentube/
            ├── branding/icon/{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}.png
            ├── branding/banner/{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}.png
            └── userscript/adblock.user.js
```

## Les 4 patches

<!-- PATCHES_START EXPANDED -->
> **[v1.2.0](https://github.com/MOD-PROD/tizentube-mod-patches/releases/tag/v1.2.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;5 patches total
<details open>
<summary>📦 TizenTube&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 2.0.3 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change app name](#change-app-name) | Changes the app name shown on the launcher and in the app list. | • App name |
| [Clone app](#clone-app) | Changes the package name so the patched app can be installed next to the original TizenTube. Default package name: mod.youtube.tv. | • Package name<br>• Update permissions<br>• Update providers |
| [Inject Adblocker Userscript](#inject-adblocker-userscript) | Injects a JavaScript userscript into the YouTube TV web app running in Cobalt. The script removes ad placements from InnerTube responses before the app reads them. | • Custom userscript file |
| [MOD Icon and Banner](#mod-icon-and-banner) | Replaces the app icon and the Android TV launcher banner with YouTube artwork. On Android TV the launcher shows the banner, so both are replaced. | • Replace icon<br>• Replace banner |

</details>

<details open>
<summary>📦 YouTube&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change app name](#change-app-name) | Changes the app name shown on the launcher and in the app list. | • App name |

</details>

<!-- PATCHES_END -->

| # | Patch | Type | Défaut | Options |
|---|---|---|---|---|
| 1 | MOD Icon and Banner | resource | non | `replaceIcon`, `replaceBanner` |
| 2 | Clone app | resource | non | `packageName` (mod.youtube.tv), `updatePermissions`, `updateProviders` |
| 3 | Inject Adblocker Userscript | bytecode + resource + extension | oui | `userscriptFile` (chemin d'un .js perso) |
| 4 | Change app name | resource | non | `appName` (YouTube, ou n'importe quel texte ; `\n` = retour à la ligne, ex. `YouTube\nPremium`). Compatible TizenTube **et YouTube** (`com.google.android.youtube`, toutes versions) |

Les patches 1 à 3 visent uniquement TizenTube 2.0.3 ; Change app name ne touche que le manifest et marche aussi sur YouTube. L'affichage sur 2 lignes dépend du launcher (certains launchers TV n'en montrent qu'une).

Clone app n'est utile que pour garder le TizenTube original installé à côté. L'adblock est une couche en plus de celui de TizenTube.

## Patch 1 — où placer les images

Le patch ne code pas les noms en dur : il lit `android:icon`, `android:roundIcon` et `android:banner` dans le manifest (dans TizenTube 2.0.x : `@mipmap/ic_app` et `@drawable/app_banner`), retrouve chaque fichier `res/<type>-<densité>/<nom>` et y copie l'image de la bonne densité.

Images attendues dans le bundle :

| Fichier | Taille | Remplace |
|---|---|---|
| `resources/tizentube/branding/icon/mdpi.png` | 80×80 | `res/mipmap-mdpi-v4/ic_app` |
| `…/icon/hdpi.png` | 120×120 | `res/mipmap-hdpi-v4/ic_app` |
| `…/icon/xhdpi.png` | 160×160 | `res/mipmap-xhdpi-v4/ic_app` |
| `…/icon/xxhdpi.png` | 240×240 | `res/mipmap-xxhdpi-v4/ic_app` |
| `…/icon/xxxhdpi.png` | 320×320 | `res/mipmap-xxxhdpi-v4/ic_app` |
| `…/banner/mdpi.png` | 160×90 | `res/drawable/app_banner` |
| `…/banner/hdpi.png` | 240×135 | `res/drawable-hdpi-v4/app_banner` |
| `…/banner/xhdpi.png` | 320×180 | `res/drawable-xhdpi-v4/app_banner` (taille de référence Android TV) |
| `…/banner/xxhdpi.png` | 480×270 | `res/drawable-xxhdpi-v4/app_banner` |
| `…/banner/xxxhdpi.png` | 640×360 | `res/drawable-xxxhdpi-v4/app_banner` |

Pour changer le visuel : remplace `branding-src/icon_master.png` (carré) et `branding-src/banner_master.png` (16:9), puis :

```
pip install pillow
python3 branding-src/make_branding.py --no-badge # images utilisées telles quelles (réglage actuel)
python3 branding-src/make_branding.py            # ajoute une pastille MOD + un tag TizenTube
```

## Patch 3 — userscript

- Script livré : `patches/src/main/resources/tizentube/userscript/adblock.user.js` (ES5, sans requête externe).
- Le patch l'écrit dans l'APK sous `assets/tizentube-mod/adblock.user.js` ; l'extension le lit au démarrage (fallback minimal embarqué si l'asset manque).
- Il hooke `JSON.parse`, `fetch` et `XMLHttpRequest.response` pour retirer `adPlacements`, `playerAds`, `adSlots`… des réponses InnerTube, filtre les tuiles sponsorisées, et saute une pub qui passerait quand même.
- Logcat : `adb logcat -s TizenTubeMod` → « Userscript injected ».

## Build

Les artefacts Morphe sont sur GitHub Packages : un token GitHub classique avec le scope `read:packages` suffit.

```
# ~/.gradle/gradle.properties
gpr.user=TON_PSEUDO_GITHUB
gpr.key=ghp_xxx

./gradlew :patches:buildAndroid :patches:generatePatchesList
# → patches/build/libs/patches-1.0.0.mpp
```

Puis dans Morphe Manager : Sources → + → fichier local `.mpp`, ou, une fois une release publiée par la CI :

```
https://morphe.software/add-source?github=MOD-PROD/tizentube-mod-patches
```

## Crédits

- Clone app adapté de [MorpheApp/morphe-patches](https://github.com/MorpheApp/morphe-patches) (GPLv3 + conditions section 7, voir NOTICE).
- Technique d'injection Cobalt et idée du patch branding : [ysamjo/morphe-patches](https://github.com/ysamjo/morphe-patches) (GPLv3).
