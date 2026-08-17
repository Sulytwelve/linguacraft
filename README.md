# 🌐 LinguaCraft

**LinguaCraft** 是专为 **Minecraft 26.1.2** 打造的 **Fabric 纯客户端实时翻译 Mod**。  
当你游玩国外服务器（如 Hypixel、Wynncraft 等）时，自动将外语**物品描述（Tooltip / Lore）**与 **Tab 列表菜单（Header / Footer）** 翻译为中文。

原生深度适配 **DeepSeek Responses API**（默认 `deepseek-v4-flash`）以及 **DeepL API**，并利用 DeepSeek **上下文硬盘缓存（Context Hard Disk Cache）** 技术，实现极低延迟、极低 Token 消耗的流畅游戏体验。

---

## ✨ 核心特性

- ⚡ **纯客户端 Mod**：无需服务端安装，不依赖额外 Kotlin 库，零冗余。
- 🔍 **按住 Tab 查看原文 (Hold Tab to Peek Original)** *(v1.0.1 新增)*：
  - 默认鼠标悬停在物品上时**直接显示中文翻译**。
  - **按住 `Tab`（或 `Left Alt`）键**立即动态切换回**英文字符串原文**，松开立刻还原译文，丝滑无卡顿。
- 🤖 **DeepSeek Responses API 原生支持**：
  - 默认采用最新的 `deepseek-v4-flash` 模型，毫秒级响应。
  - 固化标准化英文 Harness 系统提示词，**100% 触发 DeepSeek 上下文硬盘缓存**，减少高达 ~90% 输入 Token 成本。
- 📦 **双层缓存与批量请求合并**：
  - **L1 本地内存 LRU 缓存**（3000 条上限），相同文本仅翻译一次。
  - **Tooltip 批量合并**：多行 Lore 自动打包为单个 JSON 数组一次性请求，拒绝逐行刷 API。
- 🛡️ **智能过滤与格式还原**：
  - 自动保留 Minecraft `§` 颜色和排版样式代码。
  - 自动识别并跳过已有中文、纯数字、纯符号（如 `[+5]`、`12/20` 等），不浪费 API 额度。
- 🎮 **游戏内全指令支持**：支持直接在聊天框中配置 API Key、查看状态和切换提供商，无需切出游戏改文件。
- ⌨️ **快捷键一键开关**：默认按 `F8` 键随时开启/关闭翻译，Action Bar 实时提示。

---

## 🛠️ 环境要求

| 组件 | 版本要求 |
|------|----------|
| **Minecraft** | `26.1.2` |
| **Fabric Loader** | `>= 0.19.3` |
| **Fabric API** | `>= 0.155.2+26.1.2` |
| **Java Runtime (JDK)** | **Java 25**（如 Azul Zulu 25） |

---

## 🎮 游戏内指令

进入游戏后，按下 `T` 打开聊天框即可使用客户端指令：

| 指令 | 说明 |
|------|------|
| `/linguacraft setkey <API_KEY>` | 设置 API Key 并自动保存到配置文件（聊天栏脱敏回显） |
| `/linguacraft status` | 查看当前翻译开关、提供商、模型、Key 状态及缓存条数 |
| `/linguacraft provider <DEEPSEEK_RESPONSES\|DEEPL\|OPENAI_CHAT>` | 切换翻译后端提供商 |
| `/linguacraft clearcache` | 清空本地翻译内存缓存（强制重新获取最新翻译） |
| `/linguacraft reload` | 重新从磁盘加载 `linguacraft.json` 配置文件 |

---

## ⚙️ 配置文件说明

配置文件位于：`.minecraft/config/linguacraft.json`

### 方案 1：DeepSeek Responses API（推荐预设）

```json
{
  "provider": "DEEPSEEK_RESPONSES",
  "apiKey": "sk-你的DeepSeek_API_Key",
  "apiEndpoint": "https://api.deepseek.com/v1/responses",
  "model": "deepseek-v4-flash",
  "targetLanguage": "ZH",
  "enabled": true,
  "translateTooltips": true,
  "translateTabMenu": true,
  "holdTabToShowOriginal": true,
  "showHintLine": true,
  "showOriginalAndTranslation": false,
  "prefix": "[译] "
}
```

> **参数说明**：
> - `holdTabToShowOriginal`：按住 Tab（或 Alt）键时临时显示原文（默认 `true`）。
> - `showHintLine`：是否在 Tooltip 底部显示 `[按住 Tab 查看原文]` 提示行（默认 `true`）。

### 方案 2：DeepL API

```json
{
  "provider": "DEEPL",
  "apiKey": "你的DeepL_API_Key:fx",
  "targetLanguage": "ZH",
  "enabled": true,
  "translateTooltips": true,
  "translateTabMenu": true,
  "holdTabToShowOriginal": true,
  "showHintLine": true,
  "showOriginalAndTranslation": false,
  "prefix": "[译] "
}
```

---

## ⌨️ 快捷键

- **`F8`**：全局翻译功能开启 / 关闭切换。
- **按住 `Tab` 或 `Left Alt`**：物品悬停时临时查看原始未翻译文本。

---

## 🏗️ 开发与构建

```bash
# 启动测试客户端
./gradlew runClient

# 编译打包
./gradlew build
```
编译产物位于：`build/libs/linguacraft-1.0.1.jar`

---

## 📄 开源许可

本项目基于 [MIT License](LICENSE) 开源。
