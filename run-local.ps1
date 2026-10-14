# Starts the app against local MySQL.
# The password is typed at runtime (hidden) and is never written to any file.
param([string]$User = "root")

$secure = Read-Host "MySQL password for '$User'" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

$env:DB_USERNAME = $User
$env:DB_PASSWORD = $plain
try {
    & "$PSScriptRoot\mvnw.cmd" spring-boot:run
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
