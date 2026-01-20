# EncryptionChatApp

## Overview
This is a Kotlin + Jetpack Compose Android implementation that mirrors the Python reference logic (RSA OAEP encryption, RSA-SHA256 signing, and JSON storage layout).
目前没有问题。

## Local file layout (context.filesDir)
```
config/key/private.pem
config/key/public.pem
contacts/config.json
contacts/chats/<uid>.json
```

## Identity and UID derivation
- Public key PEM text is read from `config/key/public.pem` **as-is** and `rstrip('\n')` is applied.
- `pem_b64 = base64(pem_text_bytes)` (single line).
- `self_name = md5(pem_b64_bytes).hexdigest()`.
- For contacts, `uid = md5(base64(pub_key_pem_text_bytes).hexdigest())`.

## Crypto algorithms
- Key format: PKCS#8 private key (`BEGIN PRIVATE KEY`) and SubjectPublicKeyInfo public key (`BEGIN PUBLIC KEY`).
- Encryption: `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (OAEP SHA-256).
- Signing: `SHA256withRSA` (PKCS#1 v1.5).

## Build
- Ensure `ANDROID_HOME` (or `sdk.dir` in `local.properties`) points to a valid Android SDK install.
- Install required SDK components (example):
  - `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
- `./gradlew assembleDebug`

## Minimum environment
- Android Gradle Plugin 8.2+
- Kotlin 1.9+
- JDK 17

## 项目结构与实现细节（逐文件）

### 总体架构概览
- **应用层**：`MainActivity` 启动 Compose UI，`NavGraph` 管理导航与解锁/胁迫模式入口，主题由 `App.kt` 中的 `EncryptionChatTheme` 提供 Material 3 主题。核心 UI 为聊天、联系人、最近聊天、设置等多个 Compose Screen。
- **数据层**：`ChatRepository` 作为唯一数据入口，串联本地存储、加解密、网络 API（表单 POST / SSE），并负责消息读写、联系人与密钥管理。
- **存储与加密**：`FileStorage` 使用 app 私有目录持久化 JSON 与 PEM 文件；`CryptoManager` 负责 RSA 密钥管理、OAEP 加解密与签名。
- **同步机制**：`MessageSyncManager` 使用 SSE/手动拉取方式同步消息，结合 `UnreadCounter` 记录未读；同步入口由设置项与页面状态驱动。
- **安全**：`SecuritySettings` 提供 PIN/系统认证、胁迫模式与擦除策略；`GateScreen` 作为入口控制解锁/胁迫状态。

### Kotlin 文件职责与技术细节

#### 根/入口与导航
- `app/src/main/java/com/kgapp/encryptionchat/App.kt`
  - 定义 `EncryptionChatTheme`，根据 `ThemeMode` 决定浅色/深色 `colorScheme`。
  - 通过 `MaterialTheme` 统一配置 `primary/secondary/surface` 等颜色，并在暗色主题下增加 `surfaceVariant`/`onSurfaceVariant` 的对比度。
- `app/src/main/java/com/kgapp/encryptionchat/MainActivity.kt`
  - 初始化 `ThemePreferences`/`MessagePullPreferences`/`TimeDisplayPreferences`/`UnreadCounter`。
  - 构建 `FileStorage`、`CryptoManager`、`ChatApi`、`ChatRepository`、`MessageSyncManager` 并注入 UI。
  - 使用 `enableEdgeToEdge` 根据主题动态设置状态栏/导航栏样式。
- `app/src/main/java/com/kgapp/encryptionchat/NavGraph.kt`
  - 通过 `NavHost` 组织导航路由：解锁页、主 Tab、聊天页、设置页、伪装页等。
  - `TabScaffold` 管理底部导航栏与 Tab 切换，使用 `AnimatedContent` 做淡入淡出切换。
  - 根据 `SessionState` 与 `SessionMode` 控制访问权限，胁迫模式进入 `DecoyTabs`。
  - 根据 `MessagePullPreferences` 与当前 Tab 更新 `MessageSyncManager` 的拉取模式。

#### 数据层与同步
- `app/src/main/java/com/kgapp/encryptionchat/data/ChatRepository.kt`
  - 统一业务入口：联系人读写、聊天记录读写、发送/拉取消息、密钥管理。
  - **并发控制**：为每个 `uid` 使用 `Mutex` 并通过 `withChatLock` 包裹读写，避免并发写入导致历史丢失。
  - **聊天历史**：通过 `FileStorage` 按 `uid` 读写 JSON；`appendMessage`/`replaceMessageTimestamp` 保证本地时间戳可被服务器回写替换。
  - **消息发送**：拼接 `[pass=]` 握手密码后用 RSA OAEP 加密，调用 `ChatApi.postForm` 提交表单。
  - **消息拉取**：`readChat` 解析响应 `data`，逐条解密，验证握手密码，写入本地历史并返回增量数。
  - **SSE 单条消息**：`handleIncomingCipherMessage` 解密单条消息，验证密码并写入历史。
- `app/src/main/java/com/kgapp/encryptionchat/data/storage/FileStorage.kt`
  - 负责 `filesDir` 下密钥与聊天文件布局：`config/key` 与 `contacts/chats`。
  - 使用 `kotlinx.serialization` 进行 JSON 编解码，提供 pretty/compact 三种 `Json` 配置。
  - 提供 `ensureChatFile` 与 `readChatHistory`，保证聊天文件存在并含默认“暂无记录”。
  - `wipeSensitiveData` 删除密钥与聊天内容，用于胁迫擦除。
- `app/src/main/java/com/kgapp/encryptionchat/data/crypto/CryptoManager.kt`
  - 使用 `KeyPairGenerator` 生成 RSA 2048 密钥；PEM 编码采用 `Base64` 64 列换行。
  - `encryptWithPublicPemBase64`：解析对方 PEM（先 base64 解码），使用 `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` 加密。
  - `decryptText`：读取本地私钥并用同样 OAEP 配置解密，失败返回“解密失败”。
  - `signNow`：对当前秒级时间戳进行 `SHA256withRSA` 签名，发送给服务器用于鉴权。
  - `computePemBase64`/`computeSelfName` 用于生成 UID 与本机标识。
- `app/src/main/java/com/kgapp/encryptionchat/data/api/ApiResult.kt`
  - 简单 `sealed class` 封装网络成功/失败结果。
- `app/src/main/java/com/kgapp/encryptionchat/data/api/ChatApi.kt`
  - 使用 OkHttp `FormBody` 提交表单到服务器 API。
  - 捕获明文连接 `UnknownServiceException` 与通用异常，返回中文错误信息。
- `app/src/main/java/com/kgapp/encryptionchat/data/api/SseChatApi.kt`
  - 构建 `OkHttpClient`，`readTimeout(0)` 持续流式读取；`openStream` 返回 `Call` 供上层管理。
- `app/src/main/java/com/kgapp/encryptionchat/data/sync/MessageSyncManager.kt`
  - 统一消息同步逻辑，支持 `MANUAL`/`CHAT_SSE`/`GLOBAL_SSE` 三种模式。
  - 通过 `Mutex` 保护 SSE 连接切换，避免多个 `Call` 并行。
  - `startSse` 读取 SSE 行，拼接 `data:` 行，调用 `handleSseData` 解密入库。
  - 通过 `MutableSharedFlow` 向 UI 广播更新，同时更新 `UnreadCounter`。

#### 数据模型
- `app/src/main/java/com/kgapp/encryptionchat/data/model/Models.kt`
  - `ContactConfig`：保存联系人备注、公钥（base64）、握手密码、聊天背景。
  - `ChatMessage`：保存消息发言人标识（0/1/2）与文本内容。

#### UI 组件
- `app/src/main/java/com/kgapp/encryptionchat/ui/components/MessageBubble.kt`
  - 根据 `speaker` 判断左右/居中布局，使用 `Surface` 与圆角呈现气泡。
  - 在暗色主题下为非系统消息增加 `outlineVariant` 边框以增强对比。

#### UI Screens（功能页面）
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/ChatScreen.kt`
  - 绑定 `ChatViewModel`，加载聊天记录并监听 `MessageSyncManager.updates`。
  - 支持手动刷新（非全局 SSE 时）、输入框发送、回车发送。
  - 背景来自 `ChatBackgrounds` 的 `Brush`，时间格式由 `TimeDisplayPreferences` 控制。
  - 进入页面时清空未读计数，离开时停止 SSE。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/RecentScreen.kt`
  - 展示最近聊天列表，支持手动拉取与长按删除聊天记录。
  - 与 `UnreadCounter` 结合展示角标。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/ContactsScreen.kt`
  - 展示联系人列表，支持添加联系人、编辑备注/背景、删除联系人。
  - 长按弹出菜单，编辑时可复制 UID；与 `ChatBackgrounds` 联动选择背景。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/AddContactScreen.kt`
  - 表单输入备注、公钥 PEM、握手密码；校验 PEM 格式并写入联系人配置。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/ThemeSettingsScreen.kt`
  - 主题设置页，基于 `ThemePreferences` 更新 `ThemeMode`。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/KeyManagementScreen.kt`
  - 密钥状态展示、生成密钥、导入/导出公私钥。
  - 导出私钥/清除密钥需要生物识别或系统凭据确认。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/GateScreen.kt`
  - 应用锁入口：支持系统认证与 PIN，含胁迫 PIN 与擦除逻辑。
  - 通过 `SessionState` 设置 NORMAL/DURESS 会话，并触发 `DecoyTabs` 或清除数据。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/SecuritySettingsScreen.kt`
  - 配置应用锁、认证方式、PIN 与胁迫动作；擦除模式需输入 “WIPE” 二次确认。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/SettingsScreen.kt`
  - 设置入口，整合安全、主题、消息拉取、时间显示模式等配置入口。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/DecoyScreens.kt`
  - 伪装模式 UI：提供伪聊天列表/内容、隐藏模式、空设置页。
  - `DecoyTabs` 与 `SessionState` 的胁迫动作结合使用。
- `app/src/main/java/com/kgapp/encryptionchat/ui/screens/DebugScreen.kt`
  - 调试页，展示 `self_name`、公钥 PEM、联系人配置并支持输出到 Logcat。

#### ViewModel
- `app/src/main/java/com/kgapp/encryptionchat/ui/viewmodel/RepositoryViewModelFactory.kt`
  - 统一创建 `ContactsViewModel`/`ChatViewModel`/`SettingsViewModel`/`RecentViewModel`。
- `app/src/main/java/com/kgapp/encryptionchat/ui/viewmodel/ContactsViewModel.kt`
  - 封装联系人列表加载、添加、更新备注/背景、删除逻辑。
- `app/src/main/java/com/kgapp/encryptionchat/ui/viewmodel/ChatViewModel.kt`
  - 加载聊天记录并过滤默认占位消息，发送消息时先写本地再更新服务器时间戳。
  - `readNewMessages` 处理握手失败与错误提示插入系统消息。
- `app/src/main/java/com/kgapp/encryptionchat/ui/viewmodel/SettingsViewModel.kt`
  - 封装密钥状态、指纹、公钥预览、生成/导入/导出/清除密钥。
- `app/src/main/java/com/kgapp/encryptionchat/ui/viewmodel/RecentViewModel.kt`
  - 拉取最近聊天列表并使用 `TimeFormatter` 生成展示时间。

#### 工具与偏好设置
- `app/src/main/java/com/kgapp/encryptionchat/util/ThemePreferences.kt`
  - 使用 `SharedPreferences` 持久化主题模式，`StateFlow` 提供实时更新。
- `app/src/main/java/com/kgapp/encryptionchat/util/MessagePullPreferences.kt`
  - 保存/读取消息拉取模式（手动/聊天 SSE/全局 SSE）。
- `app/src/main/java/com/kgapp/encryptionchat/util/TimeDisplayPreferences.kt`
  - 控制聊天时间显示方式（相对/绝对/自动），由设置页切换。
- `app/src/main/java/com/kgapp/encryptionchat/util/ChatBackgrounds.kt`
  - 定义可选聊天背景梯度（默认、晴空、蜜桃、森林），用于聊天页背景渲染。
- `app/src/main/java/com/kgapp/encryptionchat/util/UnreadCounter.kt`
  - 使用 JSON 存储未读计数，`StateFlow` 推送至 UI，支持增量/清空。
- `app/src/main/java/com/kgapp/encryptionchat/util/TimeFormatter.kt`
  - 提供最近聊天与消息时间格式化，包含“刚刚/分钟前/昨天/周几”等规则。

#### 安全与会话
- `app/src/main/java/com/kgapp/encryptionchat/security/SecurityModels.kt`
  - 定义认证模式、胁迫动作、会话模式与安全配置数据结构。
- `app/src/main/java/com/kgapp/encryptionchat/security/SecuritySettings.kt`
  - 使用 `PBKDF2WithHmacSHA256` 对 PIN 做带盐哈希，盐+迭代次数+hash 以 `Base64:iter:hash` 保存。
  - 管理应用锁开关、胁迫模式、认证方式及擦除操作（调用 `FileStorage.wipeSensitiveData`）。
- `app/src/main/java/com/kgapp/encryptionchat/security/SessionState.kt`
  - 用 `StateFlow` 保存解锁状态/会话模式/胁迫动作，并提供锁定与解锁入口。

#### 测试
- `app/src/test/java/com/kgapp/encryptionchat/util/TimeFormatterTest.kt`
  - 单元测试验证 `TimeFormatter` 的当天/昨天格式化逻辑。
