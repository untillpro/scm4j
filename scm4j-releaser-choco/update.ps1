import-module au

$releases = 'https://github.com/scm4j/scm4j-releaser/releases'

function global:au_SearchReplace {
   @{
        ".\tools\chocolateyInstall.ps1" = @{
            "(?i)(^\s*url\s*=\s*)('.*')"        = "`$1'$($Latest.URL32)'"
            "(?i)(^\s*checksum\s*=\s*)('.*')"   = "`$1'$($Latest.Checksum32)'"
        }
    }
}

function global:au_GetLatest {
    $download_page = Invoke-WebRequest -Uri $releases
    $url = $download_page.Links | ? href -match '\.jar$' | % href | select -First 1
    $url = 'https://github.com' + $url
    $version = $url -split '/' | select -Last 1 -Skip 1

    @{
        URL32   = $url
        Version = $version
        # Checksum32 is calculated automaticaly
    }
}


update -ChecksumFor 32
