# WordFlip 内容管线

四个命令按顺序运行：

```powershell
$env:PYTHONPATH = "src"
python -m wordflip_content verify
python -m wordflip_content build `
  --book ielts=../../wordflip-server/db-archive/migration-v1/V3_1__seed_book_words_ielts.sql `
  --book cet4=../../wordflip-server/db-archive/migration-v1/V3_2__seed_book_words_cet4.sql `
  --book kaoyan=../../wordflip-server/db-archive/migration-v1/V3_3__seed_book_words_kaoyan.sql `
  --overrides overrides.json --output out
python -m wordflip_content publish --dsn "mysql://user:password@localhost:3306/wordflip" --build-dir out
```

`download` 只在 manifest 校验失败时访问官方 Release。全量 ZIP 与 SQLite 继续位于 gitignore 目录；`build` 只抽取三本词书涉及的词条，并生成异常清单和每书最多 100 条的确定性抽检样本。只有 `published` 学习卡会被 `publish` 写入线上数据库。
