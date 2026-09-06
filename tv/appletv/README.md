# Apple TV 金融看板

![Apple TV 原生金融看板，tvOS 模拟器 4K 截图](preview.png)

原生 SwiftUI / tvOS 版本，与 Roku 原型使用相同的九个演示标的和走势图生成规则。

面向 Apple TV HD（第四代）及后续 Apple TV 4K，要求 tvOS 15 或更新版本。HD 使用 1080p 输出；4K 机型由系统处理输出比例。原生布局使用逻辑点，不强制设备以 4K 渲染。

## 功能

- 全球指数、股票、数字资产行情切换。
- 日、周、月、年走势图及区间起点、最高和最低值。
- 自选添加、删除、筛选及 UserDefaults 保存。
- Siri Remote 原生焦点导航、点击选择、播放暂停键收藏。
- 中文资讯与日历演示面板、返回按钮及系统返回操作。

全部行情、资讯和情绪为演示数据，时钟为设备本地时间。当前版本为开发预览。

## 构建与安装

仓库已包含 Xcode 工程，安装 Xcode 与 tvOS SDK 后直接打开 `MarketWall.xcodeproj`。若修改工程配置，可使用 XcodeGen 重新生成：

```sh
brew install xcodegen
cd tv/appletv
xcodegen generate
open MarketWall.xcodeproj
```

在 Xcode 选择 MarketWall scheme、Apple TV 模拟器，点击 Run。真机运行需在 Signing & Capabilities 选择自己的开发团队和唯一 Bundle ID，再选择已配对的 Apple TV。

```sh
xcodebuild -project MarketWall.xcodeproj -scheme MarketWall -sdk appletvsimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

GitHub 发布包提供源码，不是可直接安装的签名 IPA。真机签名和设备导航需在自己的 Apple TV 上验证。

已在 Xcode 26.2 / tvOS 26.2 SDK 下编译模拟器版本，并在 Apple TV 4K 模拟器启动、截图检查。第四代真机和 tvOS 15 的实际导航仍待设备验证。

## 许可

MIT，见 LICENSE。
