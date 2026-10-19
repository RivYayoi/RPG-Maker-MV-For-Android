$ErrorActionPreference = 'Stop'
$src = 'C:\Program Files (x86)\KADOKAWA\RPGMV\NewData'
$dst = 'C:\Users\Yayoi\rmmv-editor-android\app\www\_template'

if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
New-Item -ItemType Directory -Force -Path $dst | Out-Null

# --- Files copied verbatim from the editor's own NewData scaffold ---------------
Copy-Item "$src\index.html"  "$dst\index.html"
Copy-Item "$src\package.json" "$dst\package.json"

foreach ($d in @('js', 'js\libs', 'data', 'fonts', 'icon', 'img\tilesets')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $dst $d) | Out-Null
}

Get-ChildItem "$src\js\*.js"        | Copy-Item -Destination "$dst\js"
Get-ChildItem "$src\js\libs\*.js"   | Copy-Item -Destination "$dst\js\libs"
Get-ChildItem "$src\data\*.json"    | Copy-Item -Destination "$dst\data"
Get-ChildItem "$src\fonts\*"        | Copy-Item -Destination "$dst\fonts"
Get-ChildItem "$src\icon\*"         | Copy-Item -Destination "$dst\icon"

# Tileset images: bundle only the default tileset's 4 sheets (Overworld).
# The full img/ tree is 218 MB and audio/ is 162 MB -- far beyond an MVP APK.
$tilesetImgs = @('World_A1.png','World_A2.png','World_B.png','World_C.png')
foreach ($t in $tilesetImgs) {
    $p = Join-Path "$src\img\tilesets" $t
    if (Test-Path $p) { Copy-Item $p (Join-Path "$dst\img\tilesets" $t) }
}

# --- report --------------------------------------------------------------------
$files = Get-ChildItem $dst -Recurse -File
$total = [math]::Round((($files | Measure-Object Length -Sum).Sum) / 1MB, 2)
"template files : $($files.Count)"
"template size  : $total MB"
$files | Group-Object { $_.DirectoryName.Replace($dst,'') } |
    Sort-Object Name | ForEach-Object { "{0,-22} {1,4} files" -f ($_.Name -replace '^\\','\'), $_.Count }
