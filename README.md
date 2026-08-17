# 🌐 LinguaCraft

**LinguaCraft** 是专为 **Minecraft 26.1.2** 打造的 **Fabric 纯客户端实时翻译 Mod**。  
当你游玩国外服务器（如 Hypixel、Wynncraft 等）时，自动将外语**物品描述（Tooltip / Lore）**与 **Tab 列表菜单（Header / Footer）** 翻译为中文。

原生深度适配 **DeepSeek Responses API**（默认 `deepseek-v4-flash`）以及 **DeepL API**，并利用 DeepSeek **上下文硬盘缓存（Context Hard Disk Cache）** 技术，实现极低延迟、极低 Token 消耗的流畅游戏体验。

---

## ✨ 核心特性

- ⚡ **纯客户端 Mod**：无需服务端安装，不依赖额外 Kotlin 库，零冗余。
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
  "showOriginalAndTranslation": false,
  "prefix": "[译] "
}
```

> **可选模型**：
> - `deepseek-v4-flash`（极速、低成本，推荐游戏实时翻译）
> - `deepseek-v4-pro`（深度上下文与复杂长文本）

### 方案 2：DeepL API

```json
{
  "provider": "DEEPL",
  "apiKey": "你的DeepL_API_Key:fx",
  "targetLanguage": "ZH",
  "enabled": true,
  "translateTooltips": true,
  "translateTabMenu": true,
  "showOriginalAndTranslation": false,
  "prefix": "[译] "
}
```

### 方案 3：自定义 OpenAI 兼容接口 / 本地 Ollama

```json
{
  "provider": "OPENAI_CHAT",
  "apiKey": "ollama",
  "apiEndpoint": "http://localhost:11434/v1/chat/completions",
  "model": "qwen2.5:7b",
  "targetLanguage": "ZH",
  "enabled": true,
  "translateTooltips": true,
  "translateTabMenu": true,
  "showOriginalAndTranslation": false,
  "prefix": "[译] "
}
```

---

## ⌨️ 快捷键

- 默认按键：**`F8`**（可在游戏“选项 -> 控制 -> 按键绑定 -> LinguaCraft 翻译”中自由更改）
- 触发效果：Action Bar 显示 `§b[LinguaCraft] §r翻译功能 §a已开启 / §c已关闭`

---

## 🏗️ 开发与构建

### 1. 启动测试客户端
```bash
./gradlew runClient
```

### 2. 编译打包 Mod JAR
```bash
./gradlew build
```
编译产物位于：
- `build/libs/linguacraft-1.0.0.jar`

将生成的 `.jar` 文件直接放入客户端的 `.minecraft/mods/` 文件夹即可。

---

## 📄 开源许可

本项目基于 [MIT License](LICENSE) 开源。
