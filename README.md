# EncryptionChatApp

## 概述
这是一个端到端加密聊天应用，使用 **Kotlin + Jetpack Compose** 实现 Android 客户端。参考 Python 版本 logic，采用 RSA OAEP 加密 + RSA-SHA256 签名，结合自定义 JSON 存储与同步协议，实现安全、私密的点对点或客户端-服务器通信。

**当前版本：** 2.0 (versionCode 20)  
**最低 Android 版本：** API 26 (Android 8.0)  
**目标 Android 版本：** API 34 (Android 14)  
**语言：** Kotlin 1.9+, Java 17  
**架构：** MVVM + Repository 模式，Compose UI

---

## ✨ 核心特性

- 🔐 **端到端加密**：RSA OAEP (SHA-256) 加密消息内容，RSA-SHA256 签名验证身份。
- 🧩 **身份匿名**：基于公钥 PEM 自动生成 UID，无需用户名/手机号。
- 📁 **本地存储**：私有目录 JSON 存储，支持敏感数据擦除。
- 🔄 **消息同步**：支持 SSE (Server-Sent Events) 实时推送，可选手动拉取，带重连机制。
- 🪖 **胁迫模式**：模拟模式与数据快速擦除，应对胁迫检查。
- 🔑 **密钥管理**：支持生成/导入/导出密钥，生物识别/系统认证保护私钥。
- 🎨 **主题定制**：浅色/深色/自动主题切换，多种聊天背景可选。
- 📱 **现代 UI**：Material 3 + Jetpack Compose，流畅的交互动画。

---

## 📂 本地文件布局 (filesDir)

```
config/key/private.pem       # 本地 RSA 私钥 (PKCS#8)
config/key/public.pem        # 本地 RSA 公钥 (SubjectPublicKeyInfo)
contacts/config.json         # 联系人配置列表
contacts/chats/<uid>.json    # 与每个联系人的聊天记录
cache/...                    # 内部缓存（自动管理）
```

---

## 🔑 身份与 UID 推导

- **本机标识**：
  1. 读取 `config/key/public.pem` 文本（保留换行符）
  2. `pem_b64 = Base64.encode(pem_text_bytes)` (单行)
  3. `self_name = MD5(pem_b64_bytes).hexDigest()`
- **联系人 UID**：
  - `uid = MD5(Base64.encode(pub_key_pem_text_bytes).hexDigest())`

---

## 🔐 加密算法与密钥格式

| 项目 | 说明 |
|------|------|
| 密钥格式 | PKCS#8 私钥 (`BEGIN PRIVATE KEY`)，SubjectPublicKeyInfo 公钥 (`BEGIN PUBLIC KEY`) |
| 加密 | `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` |
| 签名 | `SHA256withRSA` (PKCS#1 v1.5) |
| 密钥长度 | RSA 2048-bit |

---

## 🏗️ 项目结构与模块说明

### 应用层 (App Layer)

- `MainActivity` - 应用入口，初始化依赖，注入 UI，配置 EdgeToEdge
- `App.kt` - Material 3 主题定义 (`EncryptionChatTheme`) 与颜色配置
- `NavGraph.kt` - 导航图，解锁流程、Tab 结构、胁迫模式页面切换

### UI 层 (UI Layer)

#### Screens（页面）
- `ContactsScreen` - 联系人列表，添加/编辑/删除联系人
- `ChatScreen` - 聊天界面，消息发送与接收，实时更新
- `RecentScreen` - 最近聊天列表，显示未读角标
- `SettingsScreen` - 设置总入口
- `ThemeSettingsScreen` - 主题切换
- `KeyManagementScreen` - 密钥生成、导入、导出、清除
- `SecuritySettingsScreen` - 应用锁、PIN、胁迫配置
- `GateScreen` - 锁屏入口，系统认证/PIN 解锁
- `DebugScreen` - 调试信息（self_name、公钥、配置导出）
- `ApiSettingsScreen` - API 端点与消息拉取模式配置
- `DecoyScreens` / `DecoyTabs` - 胁迫模式下显示的伪装界面

#### Components（组件）
- `MessageBubble` - 消息气泡，区分发送/接收/系统消息样式

#### ViewModels
- `ContactsViewModel` - 联系人数据操作
- `ChatViewModel` - 聊天逻辑、消息发送/拉取
- `SettingsViewModel` - 设置与密钥管理
- `RecentViewModel` - 最近聊天与未读计数
- `RepositoryViewModelFactory` - 统一 ViewModel 工厂

### 数据层 (Data Layer)

- `ChatRepository` - 核心业务入口，封装联系人、聊天、密钥、同步
- `FileStorage` - 私密文件读写，JSON 序列化（pretty/compact 可选）
- `CryptoManager` - RSA 密钥生成、加解密、签名、PEM 编码解码
- `ChatApi` - HTTP POST 表单提交（OkHttp）
- `SseChatApi` - SSE 长连接支持

#### 模型
- `Models.kt` - `ContactConfig`、`ChatMessage` 等数据类

### 同步模块 (Sync)

- `MessageSyncManager` - 统一同步入口，支持：
  - `MANUAL`：手动下拉刷新
  - `Chat SSE`：当前聊天页面打开时建立 SSE
  - `Global SSE`：全局 SSE（后台）
- 自动重连、错误处理、未读计数更新

### 安全与会话

- `SecuritySettings` - 应用锁、认证方式（系统/自定义 PIN）、胁迫动作（无操作/伪装/擦除）
- `SessionState` - 会话状态机（NORMAL / DURESS），驱动 UI 切换

### 工具类 (Utils)

- `ThemePreferences` - 主题模式持久化
- `MessagePullPreferences` - 拉取模式偏好
- `TimeDisplayPreferences` - 时间显示格式（相对/绝对/自动）
- `ChatBackgrounds` - 预定义背景渐变
- `UnreadCounter` - 未读计数 JSON 存储与 Flow 广播
- `TimeFormatter` - 时间格式化（刚刚、分钟前、昨天等）

### 测试 (Tests)

- `TimeFormatterTest.kt` - 时间格式化单元测试

---

## 🔧 构建与运行

### 环境要求

- Android SDK 34 (platforms;android-34)
- Build-Tools 34.0.0
- JDK 17
- Android Gradle Plugin 8.2+
- Kotlin 1.9+

### 本地构建

```bash
# 1. 配置 Android SDK 路径
# 确保 local.properties 中包含：
#   sdk.dir=/path/to/Android/sdk
# 或设置 ANDROID_HOME 环境变量

# 2. 安装 SDK 组件（如未安装）
# sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 3. 构建 Debug APK
./gradlew assembleDebug

# 4. 安装到设备（需 USB 调试开启）
./gradlew installDebug
```

### IDE 推荐

- Android Studio Iguana (2023.2) 或更高版本，支持 Compose 预览与 Gradle 8.2+。

---

## 📶 服务器端 API（简述）

应用依赖一个简单的服务器实现（不在本仓库），提供：

- `POST /api/message` - 发送消息（表单：`from_uid`、`to_uid`、`cipher`、`sign`、`ts`）
- `GET /api/sync?uid=...` - 拉取未读消息
- `GET /sse?uid=...` - SSE 流式推送新消息
- 可选身份验证（API Key 或证书）

**握手流程：**
1. 双方交换公钥 PEM（通过联系人添加）
2. 发送方生成一次性握手密码 `[pass=xxxx]`，并附加在消息前
3. 接收方用私钥解密握手密码，验证有效后解密正文
4. 每一步失败都会显示系统错误消息

---

## 🛡️ 安全设计要点

- 所有敏感数据存储在应用私有目录，无法被其他应用读取。
- 私钥导出需要生物识别或系统认证确认。
- 胁迫 PIN 触发后立即擦除所有密钥与聊天记录（`FileStorage.wipeSensitiveData`）。
- 胁迫模式切换后 UI 完全变为伪装内容（`DecoyScreens`）。
- 签名验证防止消息伪造，时间戳防重放（如服务器实现校验）。

---

## 📝 开发与调试

- 使用 `DebugScreen` 查看 `self_name`、公钥指纹、导出的配置 JSON。
- 测试证书：使用自签名 RSA 密钥或 OpenSSL 生成测试密钥对。
- 日志：LOG 使用 ` Timber `（如已集成）或 `Log.d/e`。

---

## 📄 许可证

本项目为个人学习与实验项目，未使用开源许可证。如需二次分发或商用，请遵循相应依赖的许可证（Android SDK、Jetpack Compose、OkHttp 等）。

---

## 🙏 致谢

- 参考 Python 端到端加密聊天实现
- 使用 Kotlin Coroutines + Flow 实现响应式数据流
- Jetpack Compose + Material 3 提供现代 UI

---

**祝你聊天愉快，安全第一！🔐**