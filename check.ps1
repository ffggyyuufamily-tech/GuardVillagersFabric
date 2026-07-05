Add-Type -AssemblyName System.Drawing
foreach($file in Get-ChildItem 'C:\Users\lucas\.gemini\antigravity\brain\769b5611-615e-450a-ba60-ca19ac109374\*.png') {
    $img = [System.Drawing.Image]::FromFile($file.FullName)
    Write-Host "$($file.Name): $($img.Width)x$($img.Height)"
    $img.Dispose()
}
