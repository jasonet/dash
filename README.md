# Dash TV

Open source TV dashboard prototypes and device-specific applications.

![全球金融 TV Demo 封面](tv/rokutv/prototype-preview.jpg)

仓库根目录新增了一个可直接打开的独立版金融 TV Demo：[roku-finance-top3-standalone-2.html](roku-finance-top3-standalone-2.html)。

## Projects

- [tv/appletv](tv/appletv/README.md)：Apple TV HD（第四代）及后续 4K 机型的原生 tvOS 金融看板，要求 tvOS 15+。

![Apple TV 原生版预览](tv/appletv/preview.png)

- [tv/rokutv](tv/rokutv/README.md)：Roku TV Market Wall prototype，设计逻辑画布为 1920x1080，并检查至 3840x2160。
- [tv/firetv](tv/firetv/README.md)：Amazon Fire TV 原生 Android 看板，重点面向 Fire TV Stick 4K 的遥控器和 16:9 TV 模式。
- [tv/androidtablet](tv/androidtablet/README.md)：Android 平板原生看板，支持横屏、竖屏、触控和方向键，并使用内置数据缓存快速首屏。

## Layout

TV projects live under `tv/<platform>` so future versions can be added without mixing platform-specific assets or controls.

## License

Each TV project carries its own license and documentation. See the project directory before reuse.
