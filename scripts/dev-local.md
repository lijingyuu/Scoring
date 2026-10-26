# 本地联调一键启动（微信开发者工具 + 小程序）

## 用法

在仓库根目录的终端执行，或直接双击 `scripts\dev-local.cmd`：

```
scripts\dev-local.cmd            # 后端：dev profile + H2 内存库（零配置零密钥）
scripts\dev-local.cmd mysql      # 后端：真实 MySQL（local-env.ps1）+ mock 微信登录
scripts\dev-local.cmd mysql wx   # 后端：真实 MySQL + 真实微信登录（见下方白名单）
```

脚本会自动弹出**两个终端窗口**（后端和前端确实是两个常驻进程，但不需要你手动开窗口）：

| 窗口 | 内容 | 停止方式 |
|---|---|---|
| `scoring-backend` | 后端 :8080（约 20~40 秒就绪） | Ctrl+C 或关窗 |
| `scoring-mp-weixin` | 小程序 watch 构建 → `frontend\dist\dev\mp-weixin` | Ctrl+C 或关窗 |

## 微信开发者工具设置（首次一次性）

1. 导入项目：目录选 `frontend\dist\dev\mp-weixin`，AppID 用项目的（`wx8113b05d52ef52b3`）或测试号；
2. **必须**：右上角「详情」→「本地设置」→ 勾选【不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书】，否则发往 `http://局域网IP:8080` 的请求全部被拦；
3. 等首次 watch 构建完成（窗口出现 DONE）后，工具里点「编译」。改前端代码会自动热重编译，工具里再点「编译」刷新。

## 两种后端模式的区别

| | H2 模式（默认） | mysql 模式 | mysql wx 模式 |
|---|---|---|---|
| 配置需求 | 无需任何密钥/MySQL | `local-env.ps1` 填好真实密码 | 同左 |
| 数据 | 内存库，重启后端即清零 | 持久化到 MySQL | 持久化到 MySQL |
| 微信登录 | dev mock openid，首次登录需在小程序内补全资料 | **同样 mock openid**（脚本自动清空微信凭据，不调微信接口） | 真实 AppID/Secret，走微信侧校验 |

### 关于「invalid ip, not in whitelist」报错

`mysql wx` 模式下后端会真实调用微信 `jscode2session`，微信校验发起请求的**公网出口 IP** 是否在该 AppID 的白名单内（mp.weixin.qq.com → 开发 → 开发管理 → 开发设置 → IP 白名单）。校园网/家庭宽带的公网 IP 经常变化，每次变化都要去后台更新白名单。**本地模拟测试用默认 mysql 模式（mock 登录）即可完全绕开这个问题**。

## 注意事项

- **换网络（WiFi/IP 变化）后必须重跑脚本**：小程序端的 API 地址（`http://局域网IP:8080`）是构建时固化进包里的，`dev:mp-weixin:local` 会自动探测当前 IP 写入 `frontend/.env.local` 并重新构建；
- 后端接口健康检查：浏览器开 `http://127.0.0.1:8080/api/v1/tournaments` 返回 JSON 即就绪；
- 脚本主体刻意保持纯 ASCII：cmd.exe 按 ANSI 代码页解析批处理文件，UTF-8 中文会撕碎行解析（本次修复的问题）。
