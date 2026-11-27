$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$classOutput = Join-Path $projectRoot 'build/state-check'
New-Item -ItemType Directory -Force $classOutput | Out-Null
$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/OriginProfile.java') (Join-Path $projectRoot 'tests/OriginProfileCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Origin profile compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.OriginProfileCheck
if ($LASTEXITCODE -ne 0) { throw 'Origin profile checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/OriginProfile.java') (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/LandscapeContract.java') (Join-Path $projectRoot 'tests/LandscapeContractCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Landscape contract compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.LandscapeContractCheck
if ($LASTEXITCODE -ne 0) { throw 'Landscape contract checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/DensityContract.java') (Join-Path $projectRoot 'tests/DensityContractCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Density contract compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.DensityContractCheck
if ($LASTEXITCODE -ne 0) { throw 'Density contract checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/HeadsUpContract.java') (Join-Path $projectRoot 'tests/HeadsUpContractCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Heads-up contract compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.HeadsUpContractCheck
if ($LASTEXITCODE -ne 0) { throw 'Heads-up contract checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/EntryStore.java') (Join-Path $projectRoot 'tests/EntryStoreCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'State check compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.EntryStoreCheck
if ($LASTEXITCODE -ne 0) { throw 'State checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/DeliveryQueue.java') (Join-Path $projectRoot 'tests/DeliveryQueueCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Delivery check compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.DeliveryQueueCheck
if ($LASTEXITCODE -ne 0) { throw 'Delivery checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/CardPullGesture.java') (Join-Path $projectRoot 'tests/CardPullGestureCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Gesture check compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.CardPullGestureCheck
if ($LASTEXITCODE -ne 0) { throw 'Gesture checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/CardMorphGeometry.java') (Join-Path $projectRoot 'tests/CardMorphGeometryCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Morph geometry compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.CardMorphGeometryCheck
if ($LASTEXITCODE -ne 0) { throw 'Morph geometry checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/RetiredIslandGate.java') (Join-Path $projectRoot 'tests/RetiredIslandGateCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Retirement check compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.RetiredIslandGateCheck
if ($LASTEXITCODE -ne 0) { throw 'Retirement checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/CardMorphGeometry.java') (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/CardShellAppearance.java') (Join-Path $projectRoot 'tests/CardShellAppearanceCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Shell appearance compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.CardShellAppearanceCheck
if ($LASTEXITCODE -ne 0) { throw 'Shell appearance checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/AppSelection.java') (Join-Path $projectRoot 'tests/AppSelectionCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'App selection compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.AppSelectionCheck
if ($LASTEXITCODE -ne 0) { throw 'App selection checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/IconCrop.java') (Join-Path $projectRoot 'tests/IconCropCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Icon crop compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.IconCropCheck
if ($LASTEXITCODE -ne 0) { throw 'Icon crop checks failed' }
& $javac -encoding UTF-8 -d $classOutput (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/IslandSwipePolicy.java') (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/EntryStore.java') (Join-Path $projectRoot 'tests/IslandSwipePolicyCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Island swipe compilation failed' }
& $java -cp $classOutput com.joelau.notifyisland.IslandSwipePolicyCheck
if ($LASTEXITCODE -ne 0) { throw 'Island swipe checks failed' }
