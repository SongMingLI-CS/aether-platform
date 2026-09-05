# macOS 代码签名与公证（Aether Platform 桌面端）

> 现状：`npm run desktop:dist` 产出的 `.app` / `.dmg` / `.zip` 为**未签名**构建（本地开发用）。
> 未签名应用分发到他人机器时，Gatekeeper 会提示「来自身份不明的开发者」，需右键 → 打开或到「系统设置 → 隐私与安全性」放行。正式分发请按本文签名并公证。

## 1. 前置条件

- 一个 **Apple Developer ID Application** 证书（在 https://developer.apple.com 的 Certificates 页面创建，导出为 `.p12`）。
- 一个 Apple ID（用于 notarytool 公证）。

## 2. 签名（本地构建）

electron-builder 通过环境变量读取证书：

```bash
export CSC_LINK=~/certificates/DeveloperIDApplication.p12   # 或 base64 字符串 / https URL
export CSC_KEY_PASSWORD='<证书导出密码>'
# 关闭自动发现，明确走 CSC_LINK
export CSC_IDENTITY_AUTO_DISCOVERY=false

cd frontend
npm run desktop:dist
```

`package.json` 的 `build.mac` 建议补上（公证所需）：

```jsonc
"mac": {
  "icon": "build/icon.png",
  "category": "public.app-category.productivity",
  "hardenedRuntime": true,
  "entitlements": "build/entitlements.mac.plist",
  "entitlementsInherit": "build/entitlements.mac.plist",
  "target": ["dmg", "zip"]
}
```

`build/entitlements.mac.plist` 最小内容（Hardened Runtime 必需的基本权限）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>com.apple.security.cs.allow-jit</key><true/>
  <key>com.apple.security.cs.allow-unsigned-executable-memory</key><true/>
</dict></plist>
```

## 3. 公证（notarization）

签名后由 Apple 公证，通过后 Gatekeeper 不再弹「不明开发者」：

```bash
export APPLE_ID='you@example.com'
export APPLE_APP_SPECIFIC_PASSWORD='xxxx-xxxx-xxxx-xxxx'  # App 专用密码
export APPLE_TEAM_ID='YOURTEAMID'
```

electron-builder 26 已内置 `notarize` 支持，`build.mac` 追加：

```jsonc
"mac": {
  // ... 上面的配置 ...
  "notarize": {
    "teamId": "YOURTEAMID"
  }
}
```

或构建后手动公证：

```bash
xcrun notarytool submit "release/Aether Platform-0.1.0-arm64.dmg" \
  --apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" \
  --password "$APPLE_APP_SPECIFIC_PASSWORD" --wait
xcrun stapler staple "release/Aether Platform-0.1.0-arm64.dmg"
```

## 4. Windows 签名（可选）

在 Windows/CI 上，用 `WIN_CSC_LINK` / `WIN_CSC_KEY_PASSWORD` 提供代码签名证书（`.pfx`），electron-builder 会自动对 `nsis` 安装包签名。

## 5. 参考

- https://www.electron.build/code-signing
- https://www.electron.build/mac#hardened-runtime
- https://www.electron.build/notarize
