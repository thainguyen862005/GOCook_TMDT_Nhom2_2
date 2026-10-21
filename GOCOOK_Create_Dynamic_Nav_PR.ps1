# GO Cook - Dynamic Navigation: safe local commits + GitHub Draft PR
# Generated from GOCOOK_Dynamic_Navigation_Changed_Files.zip and PublicHelpController compile fix
# Run default for DRY RUN. -Apply -Push -CreatePr to complete workflow.
[CmdletBinding()]
param(
  [switch]$Apply,
  [switch]$Push,
  [switch]$CreatePr
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
if ($CreatePr) { $Push = $true }
function Invoke-Git([string[]]$GitArgs) {
  & git.exe @GitArgs
  if ($LASTEXITCODE -ne 0) { throw ("git failed: " + ($GitArgs -join " ")) }
}
function Invoke-CommandChecked([string]$Command, [string[]]$Arguments) {
  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}
function Get-NormalizedSha256([string]$Path) {
  $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
  $text = $text.TrimStart([char]0xFEFF).Replace("`r`n", "`n")
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try { $hashBytes = $sha.ComputeHash($bytes) } finally { $sha.Dispose() }
  return ([System.BitConverter]::ToString($hashBytes) -replace "-", "").ToLowerInvariant()
}
$root = (& git.exe rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) { throw "Run in a Git repository." }
Set-Location $root.Trim()
$branch = (& git.exe branch --show-current).Trim()
if ($branch -ne "tan") { throw "Expected branch tan, got $branch. Stop to avoid committing on the wrong branch." }
if (-not (Test-Path "frontend/package.json")) { throw "Run in GO Cook repository root." }
$alreadyStaged = @(& git.exe diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw "Could not inspect index." }
if (@($alreadyStaged | Where-Object { $_ -ne "" }).Count -gt 0) { throw "Files are already staged. Review git diff --cached and unstage them before running this script." }
$groups = @(
  @{ Message = "feat(public): add dynamic chef catalog and safe endpoints"; Files = @(
    "src/main/java/com/james/LMS/publiccontent/PublicChefService.java"
    "src/main/java/com/james/LMS/publiccontent/PublicCatalogController.java"
    "src/main/java/com/james/LMS/config/SecurityConfig.java"
    "src/main/java/com/james/LMS/util/PublicEndpointsValidatorUtil.java"
    "src/test/java/com/james/LMS/publiccontent/PublicCatalogControllerTest.java"
    "src/test/java/com/james/LMS/publiccontent/PublicChefServiceTest.java"
  ) },
  @{ Message = "feat(web): load home chefs and matching from public APIs"; Files = @(
    "frontend/src/types/publicContent.ts"
    "frontend/src/clients/publicContentClient.ts"
    "frontend/src/components/chef/ChefCard.tsx"
    "frontend/src/pages/chef/ChefListPage.tsx"
    "frontend/src/pages/chef/ChefDetailPage.tsx"
    "frontend/src/pages/home/HomePage.tsx"
    "frontend/src/pages/request/MatchingPage.tsx"
    "frontend/src/routers/routes/publicRoutes.tsx"
  ) },
  @{ Message = "feat(help): serve offers FAQs and authenticated support tickets"; Files = @(
    "src/main/java/com/james/LMS/publiccontent/PublicHelpController.java"
    "frontend/src/pages/offer/OffersPage.tsx"
    "frontend/src/pages/support/HelpPage.tsx"
    "frontend/src/pages/admin/FaqManagementPage.tsx"
    "frontend/src/layouts/AdminLayout.tsx"
    "frontend/src/constants/routes.ts"
    "frontend/src/routers/routes/adminRoutes.tsx"
  ) },
  @{ Message = "feat(request): accept custom cooking requests and admin review"; Files = @(
    "src/main/java/com/james/LMS/publiccontent/CookingRequestController.java"
    "frontend/src/pages/request/CookingRequestPage.tsx"
    "frontend/src/pages/admin/CookingRequestsPage.tsx"
  ) },
  @{ Message = "chore(db): add FAQ and cooking request migrations"; Files = @(
    "sql/a01/09_public_navigation_dynamic.sql"
    "sql/a01/10_verify_public_navigation.sql"
  ) }
)
$expectedSha = @{
  "frontend/src/clients/publicContentClient.ts" = "6a28a988cf9772362b046ee0b9cda05ca82cf650a3bb52a6887d3325490d0de2"
  "frontend/src/components/chef/ChefCard.tsx" = "8bd994775cced07b736e5a41fafcfc19a434107ed5cbab319ab3c39c5571e6ca"
  "frontend/src/constants/routes.ts" = "e7f9885f1e70ca2a2a01a53dc46182c9cd0dd31763b2155d790ca9d87661b71d"
  "frontend/src/layouts/AdminLayout.tsx" = "83e70dbfe158ad3b967a6f436c440f0c77965da7f870ad2f7d7de54835c9bc5c"
  "frontend/src/pages/admin/CookingRequestsPage.tsx" = "cf87da41c701f98a1087fce9f1cafff0a603d9c7e7790ddccbcc56a6f9861fc9"
  "frontend/src/pages/admin/FaqManagementPage.tsx" = "c0d93eabed2971ff2039eed7d864f12902a4a987b472c1b866cbd0a37d3fce2f"
  "frontend/src/pages/chef/ChefDetailPage.tsx" = "33435d61c0dde10f8db18a2c3a6fc671ab0a41f678e4aebe1b206004dd2db2ae"
  "frontend/src/pages/chef/ChefListPage.tsx" = "7d8fcbbb9a0651d010726419d0fbc73e07be0532cc95efde7bd9595412a9ca5f"
  "frontend/src/pages/home/HomePage.tsx" = "1ae45c0a14d1b245d4977a74342573192aa0b0310235dbf217968bfcdf428d38"
  "frontend/src/pages/offer/OffersPage.tsx" = "c7df934e86803a9a357ac2171211e359c431a8a6adee2b8587c22e0d6912fa19"
  "frontend/src/pages/request/CookingRequestPage.tsx" = "e04c6f4bc00344447a9625f2a1d12a5fb45592c9a9ab2a830a97ccdd38e757cc"
  "frontend/src/pages/request/MatchingPage.tsx" = "1d7c3ce51f17a9e0a2f2ac451f0ef2702dd797bcc5c2f2d328732457c942ff36"
  "frontend/src/pages/support/HelpPage.tsx" = "8e46471c0bc14c2f102ac8edde8214ebf6d173296e67079e0618c27c878c9c33"
  "frontend/src/routers/routes/adminRoutes.tsx" = "d3781ef44ab31620bce181c3c5e956339fea57b24fe2f174f586e82e1cf42c3c"
  "frontend/src/routers/routes/publicRoutes.tsx" = "e43b847a28e859afdf62fe026e7c76780d91557e42aa7d63fd4dc34937e96fb7"
  "frontend/src/types/publicContent.ts" = "9b440497bcc7e824efd244703515d3a699ecc1b93bc9fa6bdff3dae56bb83440"
  "sql/a01/09_public_navigation_dynamic.sql" = "1704373eddfc6196db90627a8e3988db2d85f55d885a1215d00cf06ec4168e40"
  "sql/a01/10_verify_public_navigation.sql" = "02a729f2e44ef132b8d412e4851b9ca5821169b2f4e0f10077b77230dd33e232"
  "src/main/java/com/james/LMS/config/SecurityConfig.java" = "697c84d31f33bc25fd051b598377b1380771dcdcddf8b0d75c2e5a26f7e5e317"
  "src/main/java/com/james/LMS/publiccontent/CookingRequestController.java" = "c43104a8ec90725c9c31d80c43c45f2b71e2ab6a7989e44dbe56c33cde7caa8b"
  "src/main/java/com/james/LMS/publiccontent/PublicCatalogController.java" = "25902ece4f37bcae9723fc47fda7c5f0e1dcc00c71a9d643bf24df998132f7b8"
  "src/main/java/com/james/LMS/publiccontent/PublicChefService.java" = "03f806e049990bf399f753b9988cf24ce373c9d7b08406634f24a56f56cbfc26"
  "src/main/java/com/james/LMS/publiccontent/PublicHelpController.java" = "6f0da21926e6e0854ae17b9066b4bc0aa8fa658434fd59e04b6a64d0b417cc33"
  "src/main/java/com/james/LMS/util/PublicEndpointsValidatorUtil.java" = "f3b068bf99ad1aa5aff73ff9dddfe37606a8d60014dbb47cdce6deddd538b157"
  "src/test/java/com/james/LMS/publiccontent/PublicCatalogControllerTest.java" = "d4601563e9b37cb6663f6c04340607c798411f6dd8c6c92c30658f4c8140ba3b"
  "src/test/java/com/james/LMS/publiccontent/PublicChefServiceTest.java" = "e04dc77d95ce421ef3a3099f88cb6fa717f3afa36df586fa3310fe64347f1ddd"
}
Write-Host "Repository: $root"
Write-Host "Branch: $branch"
Write-Host "Scope: Dynamic Navigation only; MenuListPage and Menu API are excluded."
Write-Host "Checking source file hashes against the supplied patch + compile fix..."
$changedFiles = @()
foreach ($group in $groups) {
  Write-Host "`n=== $($group.Message) ==="
  foreach ($path in $group.Files) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing file: $path. Apply the Dynamic Navigation patch first." }
    $actualSha = Get-NormalizedSha256 -Path $path
    if ($actualSha -ne $expectedSha[$path]) { throw "File differs from reviewed patch: $path. This may include teammate changes. Review manually; do not stage blindly." }
    $change = @(& git.exe status --short --untracked-files=all -- $path)
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect $path" }
    if (@($change | Where-Object { $_ -ne "" }).Count -gt 0) {
      Write-Host ($change -join "`n")
      $changedFiles += $path
    } else { Write-Host "Already committed / unchanged: $path" }
  }
}
if (-not $Apply) {
  Write-Host "`nDRY RUN ONLY. No commits, pushes, or PRs created."
  Write-Host "After tests and DB migration are verified: .\GOCOOK_Create_Dynamic_Nav_PR.ps1 -Apply -Push -CreatePr"
  exit 0
}
if (@($changedFiles).Count -eq 0) { Write-Host "No Dynamic Navigation file changes remain to commit." }
Write-Host "`nChecking Java compilation and unit tests..."
Invoke-CommandChecked ".\mvnw.cmd" @("-U", "clean", "test")
Write-Host "`nChecking frontend..."
Push-Location "frontend"
try {
  Invoke-CommandChecked "npm.cmd" @("run", "typecheck")
  Invoke-CommandChecked "npm.cmd" @("run", "build")
} finally { Pop-Location }
Write-Host "`nReview ALL Dynamic Navigation diffs before staging (especially SecurityConfig.java)."
Invoke-Git @("diff", "--check", "--", "src/main/java/com/james/LMS/config/SecurityConfig.java")
foreach ($group in $groups) {
  $files = @($group.Files | Where-Object { $changedFiles -contains $_ })
  if ($files.Count -eq 0) { Write-Host "Skip unchanged: $($group.Message)"; continue }
  Write-Host "`nCommit: $($group.Message)"
  Invoke-Git (@("add", "--") + $files)
  Invoke-Git @("diff", "--cached", "--check")
  Invoke-Git @("diff", "--cached", "--stat")
  Invoke-Git @("commit", "-m", $group.Message)
}
if ($Push) {
  Write-Host "`nPushing branch tan to origin..."
  Invoke-Git @("push", "-u", "origin", "tan")
}
if ($CreatePr) {
  if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Warning "GitHub CLI (gh) not installed. Commits/push are complete, but PR was NOT created."
    Write-Host "Open: https://github.com/thainguyen862005/GOCook_TMDT_Nhom2_2/compare/main...tan"
    exit 0
  }
  & gh auth status
  if ($LASTEXITCODE -ne 0) {
    Write-Warning "gh authentication is missing. Run: gh auth login . PR was NOT created."
    exit 0
  }
  $prUrl = @(& gh pr view tan --json url --jq ".url" 2>$null)
  if ($LASTEXITCODE -eq 0 -and $prUrl.Count -gt 0 -and $prUrl[0]) {
    Write-Host "Existing PR: $($prUrl[0])"
    exit 0
  }
  $body = @"
## Summary
- Connect main public navigation pages to PostgreSQL-backed APIs instead of hard-coded demo data.
- Add verified chef discovery, chef detail, featured homepage entries, dynamic offers, FAQs and authenticated support requests.
- Store custom cooking requests for Admin review without inventing Booking or Payment transactions.
- Add migration 09 and verification SQL 10; keep existing `/menus` implementation unchanged.
- Fix Spring JDBC NamedParameterJdbcTemplate queryForList compile error in PublicHelpController.

## Validation
- [x] Maven clean test (local execution)
- [x] Frontend typecheck and build (local execution)
- [ ] Navicat migration 09 and verification 10 confirmed in shared environment
- [ ] End-to-end chef/help/request flows tested with real data
- [ ] Shared SecurityConfig.java reviewed by team

## Scope and integration
- Only public navigation and Admin FAQ/cooking request UI/API.
- No Booking/Payment state changes; voucher application remains the owning domain responsibility.
- Draft PR: do not merge before final integration checks.
"@
  $tmpBody = Join-Path ([System.IO.Path]::GetTempPath()) "gocook_dynamic_nav_pr_body.md"
  [System.IO.File]::WriteAllText($tmpBody, $body, [System.Text.Encoding]::UTF8)
  & gh pr create --base main --head tan --draft --title "feat(web): make main navigation data-driven" --body-file $tmpBody
  if ($LASTEXITCODE -ne 0) { throw "gh pr create failed; commits and push may have succeeded, but PR has not been confirmed." }
}
Write-Host "`nDone. Check git status and GitHub PR checks before merging."
