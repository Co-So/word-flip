# WordFlip v2 上线脚本

这些脚本默认不改真实数据库。先构建并审核内容包，再为新库执行备份、基线和发布。

```powershell
$env:WORDFLIP_DB_PASSWORD = "<数据库密码>"
.\scripts\rebuild-wordflip-v2.ps1 -SourceDatabase wordflip -TargetDatabase wordflip_v2
# 核对输出后：
.\scripts\rebuild-wordflip-v2.ps1 -SourceDatabase wordflip -TargetDatabase wordflip_v2 -Execute
```

脚本遇到已存在的目标库会终止，不会删除或清空数据库。服务启动并登录后执行：

```powershell
.\scripts\smoke-wordflip-v2.ps1 -AccessToken "<JWT access token>"
```
