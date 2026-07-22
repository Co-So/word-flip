[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080/api/v1",
    [Parameter(Mandatory = $true)]
    [string]$AccessToken
)

$ErrorActionPreference = "Stop"
$headers = @{ Authorization = "Bearer $AccessToken" }
$books = Invoke-RestMethod -Headers $headers -Uri "$BaseUrl/books"
if ($books.books.Count -lt 3) { throw "词书目录不足三本。" }

try {
    $plan = Invoke-RestMethod -Headers $headers -Uri "$BaseUrl/learning-plans/current"
    $todayHeaders = $headers + @{ "X-Timezone" = "Asia/Shanghai" }
    $today = Invoke-RestMethod -Headers $todayHeaders -Uri "$BaseUrl/today"
    Write-Host "当前计划：$($plan.bookName)；今日日期：$($today.date)"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-Host "当前用户尚无学习计划；Android 应进入首次选书页。"
    } else {
        throw
    }
}
Write-Host "WordFlip v2 冒烟检查通过。"
