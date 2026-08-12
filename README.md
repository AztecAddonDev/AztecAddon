# AztecAddon

A Minecraft addon for [Meteor Client](https://github.com/MeteorDevelopment/meteor-client), focused on useful, configurable and unique features for anarchy and vanilla Minecraft environments.

AztecAddon is independently developed from scratch and is not a fork of another addon. It uses the Meteor Client API and the Fabric ecosystem.

## Features

AztecAddon currently includes features across several categories:

- Quality of Life
- Utility
- Movement
- Render
- HUD
- Automation
- Miscellaneous
- Custom Mixins

The goal of AztecAddon is to provide configurable features that are useful for anarchy servers and vanilla Minecraft while avoiding unnecessary duplication of features already available in Meteor Client.

## Installation

1. Install a compatible version of Minecraft.
2. Install [Fabric Loader](https://fabricmc.net/).
3. Install the required version of [Meteor Client](https://meteorclient.com/).
4. Download the latest AztecAddon release.
5. Place the AztecAddon `.jar` file into your Minecraft `mods` folder.
6. Launch Minecraft with Fabric, Meteor Client and AztecAddon installed.

## Building

Clone the repository:

```bash
git clone https://github.com/AztecAddonDev/AztecAddon.git
cd AztecAddon-Source

Build the addon using Gradle:
./gradlew build

The compiled .jar will be available in:
build/libs/.
```

## Compatibility

AztecAddon is developed for specific Minecraft, Fabric and Meteor Client versions.

Always make sure that your Minecraft, Fabric Loader, Fabric API, Meteor Client and AztecAddon versions are compatible with each other.

## Discord

[Join the Discord](https://discord.gg/RYNY6vk5Rc)

## Credits

### Developer

- **AztecDeveloper** — Official developer and representative of AztecAddon.
- **Desaparecido** — Personal Minecraft and Discord identity of the developer.

> **Note:** Desaparecido and AztecDeveloper are two identities used by the same person. Desaparecido represents the personal gaming identity, while AztecDeveloper represents the development and official project side of AztecAddon.

### Inspiration & References

- **koodadevs-creator / KoodaAddon** — The `ChatBot` module served as inspiration for AztecBotAz. Parts of the original implementation were also used as a reference during development and testing.
    - [Original ChatBot.java](https://github.com/koodadevs-creator/KoodaAddon-/blob/main/src/main/java/pwn/noobs/trouserstreak/modules/ChatBot.java)

AztecAddon is not a fork of KoodaAddon. AztecBotAz was independently developed for AztecAddon, with the original project acknowledged as an inspiration and development reference.

Thank you to **koodadevs-creator** for the original idea and implementation that helped during development.

## License

AztecAddon is distributed under the license included in this repository's `LICENSE` file.

See `LICENSE` for the complete license terms.

## Disclaimer

AztecAddon is an independent third-party addon and is not affiliated with or endorsed by Meteor Development.
