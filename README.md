<p align="center">
 <img width=200px height=200px src="./.github/readme-images/app-icon.png"/>
</p>

<h1 align="center"> Komikku </h1>

| Releases | Preview | CI builds | Discussions |
|----------|---------|-----------|-------------|
| [![GitHub downloads](https://img.shields.io/github/downloads/komikku-app/komikku/latest/total?label=Latest%20Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/komikku-app/komikku/releases/latest) [![Stable](https://img.shields.io/github/release/komikku-app/komikku.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://github.com/komikku-app/komikku/releases/latest) [![GitHub downloads](https://img.shields.io/github/downloads/komikku-app/komikku/total?label=Total%20Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/komikku-app/komikku/releases) [![Build](https://img.shields.io/github/actions/workflow/status/komikku-app/komikku/build_release.yml?labelColor=27303D)](https://github.com/komikku-app/komikku/actions/workflows/build_release.yml) | [![GitHub downloads](https://img.shields.io/github/downloads/komikku-app/komikku-preview/latest/total?label=Latest%20Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/komikku-app/komikku-preview/releases/latest) [![Beta](https://img.shields.io/github/v/release/komikku-app/komikku-preview.svg?maxAge=3600&label=Beta&labelColor=2c2c47&color=1c1c39)](https://github.com/komikku-app/komikku-preview/releases/latest) [![GitHub downloads](https://img.shields.io/github/downloads/komikku-app/komikku-preview/total?label=Total%20Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/komikku-app/komikku-preview/releases) [![Beta build](https://img.shields.io/github/actions/workflow/status/komikku-app/komikku-preview/build_app.yml?labelColor=27303D)](https://github.com/komikku-app/komikku-preview/actions/workflows/build_app.yml) | [![CI](https://img.shields.io/github/actions/workflow/status/komikku-app/komikku/build_check.yml?labelColor=27303D)](https://github.com/komikku-app/komikku/actions/workflows/build_check.yml) | [![Discord](https://img.shields.io/discord/1242381704459452488?label=discord&labelColor=7289da&color=2c2f33&style=flat)](https://discord.gg/85jB7V5AJR) |

### Help translate
* **[Weblate](https://hosted.weblate.org/projects/komikku-app/komikku/)**
* **[Crowdin](https://crowdin.com/project/komikku/invite?h=f922abd4193e77309b084a08c74b89872112170)**

A free and open source manga reader which is based off TachiyomiSY & Mihon/Tachiyomi. This fork is meant to provide new & useful features while regularly take features/updates from Mihon or other forks like SY, J2K and Neko...

![screenshots of app](./.github/readme-images/screens.png)

## Features

### Komikku's unique features:
- `Suggestions` automatically showing source-website's recommendations / suggestions / related to current entry for all sources.
- `Hidden categories` to hide yours things from *nosy* people.
- `Auto theme color` based on each entry's cover for entry View & Reader.
- `App custom theme` with `Color palettes` for endless color lover.
- `Bulk-favorite` multiple entries all at once.
- `Feed` now supports **all** sources, with more items (20 for now).
- Auto `2-way sync` progress with trackers.
- Chips for `Saved search` in source browse
- `Panorama cover` showing wide cover in full.
- `Merge multiple` library entries together at same time.
- `Range-selection` for Migration.
- Ability to `enable/disable repo`.
- `Update Error` screen & migrating them away.
- `to-be-updated` screen: which entries are going to be checked with smart-update?
- `Search for sources` & Quick NSFW sources filter in Extensions, Browse & Migration screen.
- `Feed` backup/restore/sync/re-order.
- Long-click to add/remove single entry to/from library, everywhere.
- Docking Read/Resume button.
- Banner shows Library syncing / Backup restoring / Library updating progress.
- Configurable interval to refresh entries from downloaded storage.
- More app themes & better UI, improvements...


<details>
  <summary>Features from Mihon / Tachiyomi</summary>

#### All up-to-date features from Mihon / Tachiyomi (original), include:

* Online reading from a variety of sources
* Local reading of downloaded content
* A configurable reader with multiple viewers, reading directions and other settings.
* Tracker support: [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Bangumi](https://bgm.tv/)
* Categories to organize your library
* Light and dark themes
* Schedule updating your library for new chapters
* Create backups locally to read offline or to your desired cloud service
* Continue reading button in library

</details>

<details>
  <summary>Features from Tachiyomi SY</summary>

#### All features from TachiyomiSY:
* Feed tab, where you can easily view the latest entries or saved search from multiple sources at same time.
* Automatic webtoon detection, allowing the reader to switch to webtoon mode automatically when viewing one
* Manga recommendations, uses MAL and Anilist, as well as Neko Similar Manga for Mangadex manga (Thanks to Az, She11Shocked, Carlos, and Goldbattle)
* Lewd filter, hide the lewd manga in your library when you want to
* Tracking filter, filter your tracked manga so you can see them or see non-tracked manga, made by She11Shocked
* Search tracking status in library, made by She11Shocked
* Custom categories for sources, liked the pinned sources, but you can make your own versions and put any sources in them
* Manga info edit
* Manga Cover view + share and save
* Dynamic Categories, view the library in multiple ways
* Smart background for reading modes like LTR or Vertical, changes the background based on the page color
* Force disable webtoon zoom
* Hentai features enable/disable, in advanced settings
* Quick clean titles
* Source migration, migrate all your manga from one source to another
* Saving searches
* Autoscroll
* Page preload customization
* Customize image cache size
* Batch import of custom sources and featured extensions
* Advanced source settings page, searching, enable/disable all
* Click tag for local search, long click tag for global search
* Merge multiple of the same manga from different sources
* Drag and drop library sorting
* Library search engine, includes exclude, quotes as absolute, and a bunch of other ways to search
* New E-Hentai/ExHentai features, such as language settings and watched list settings
* Enhanced views for internal and integrated sources
* Enhanced usability for internal and delegated sources

Custom sources:
* E-Hentai/ExHentai

Additional features for some extensions, features include custom description, opening in app, batch add to library, and a bunch of other things based on the source:
* 8Muses (EroMuse)
* HBrowse
* Mangadex
* NHentai
* Puruin
* Tsumino

</details>

## Download
* [Stable](https://github.com/komikku-app/komikku/releases/latest)
* [Preview](https://github.com/komikku-app/komikku-preview/releases/latest) to try latest features.

## Issues, Feature Requests and Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

<details><summary>Issues</summary>

1. **Before reporting a new issue, take a look at the [FAQ](https://mihon.app/docs/faq/general), the [changelog](https://github.com/komikku-app/komikku/releases) and the already opened [issues](https://github.com/komikku-app/komikku/issues).**
2. If you are unsure, ask here: [![Discord](https://img.shields.io/discord/1242381704459452488)](https://discord.gg/85jB7V5AJR)

</details>

<details><summary>Bugs</summary>

* Include version (More → About → Version)
 * If not latest, try updating, it may have already been solved
 * Preview version is equal to the number of commits as seen on the main page
* Include steps to reproduce (if not obvious from description)
* Include screenshot (if needed)
* If it could be device-dependent, try reproducing on another device (if possible)
* Don't group unrelated requests into one issue

Use the [issue forms](https://github.com/komikku-app/komikku/issues/new/choose) to submit a bug.

</details>

<details><summary>Feature Requests</summary>

* Write a detailed issue, explaining what it should do or how.
* Include screenshot (if needed).
</details>

<details><summary>Contributing</summary>

See [CONTRIBUTING.md](./CONTRIBUTING.md).
</details>

<details><summary>Code of Conduct</summary>

See [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md).
</details>

### Credits

Thank you to all the people who have contributed!

<a href="https://github.com/komikku-app/komikku/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=komikku-app/komikku" alt="Komikku app contributors" title="Komikku app contributors" width="800"/>
</a>

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

## FAQ

* Komikku [website](https://komikku-app.github.io/) / [Discord](https://discord.gg/85jB7V5AJR)

* Mihon [website](https://mihon.app/) / [Discord](https://discord.gg/mihon)

## License

    Copyright 2015 Javier Tomás

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## Disclaimer

The developer of this application does not have any affiliation with the content providers available.
