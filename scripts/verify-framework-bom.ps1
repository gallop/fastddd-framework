$ErrorActionPreference = 'Stop'

[xml]$pom = Get-Content -Raw -Encoding utf8 "$PSScriptRoot\\..\\fastddd-bom\\pom.xml"
$ns = [System.Xml.XmlNamespaceManager]::new($pom.NameTable)
$ns.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')

$allowed = @(
    'fast-ddd-common',
    'fast-ddd-config',
    'fast-ddd-model',
    'fast-ddd-crud',
    'fast-ddd-web',
    'fast-ddd-application',
    'fast-ddd-cache',
    'fast-ddd-event',
    'fast-ddd-search',
    'fast-ddd-monitor',
    'fast-ddd-starter',
    'fast-ddd-analyzer-spring-boot-starter',
    'fast-ddd-security-starter',
    'fast-ddd-ai-spring-boot-starter'
)

$frameworkArtifacts = @(
    $pom.SelectNodes('/m:project/m:dependencyManagement/m:dependencies/m:dependency', $ns) |
        Where-Object { $_.groupId -eq 'com.gallop.fastddd' } |
        ForEach-Object { $_.artifactId }
)

$unexpected = @($frameworkArtifacts | Where-Object { $_ -notin $allowed })
$missing = @($allowed | Where-Object { $_ -notin $frameworkArtifacts })
if ($unexpected.Count -gt 0 -or $missing.Count -gt 0) {
    if ($unexpected.Count -gt 0) { Write-Error "Unexpected Framework BOM artifacts: $($unexpected -join ', ')" }
    if ($missing.Count -gt 0) { Write-Error "Missing Framework BOM artifacts: $($missing -join ', ')" }
    exit 1
}

Write-Output 'Framework BOM allowlist passed.'
