"""Run portable decoder tests and build the dependency-free core JAR (JDK8+)."""
from pathlib import Path
import subprocess,sys,zipfile
root=Path(__file__).resolve().parents[1]
build=root/'build'/'core';build.mkdir(parents=True,exist_ok=True)
sources=list((root/'core'/'src').rglob('*.java'))
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(build),*[str(p) for p in sources]],check=True)
(root/'dist').mkdir(exist_ok=True)
with zipfile.ZipFile(root/'dist'/'teyes-can-core-0.5.1.jar','w',zipfile.ZIP_DEFLATED) as archive:
 for p in (build/'com').rglob('*.class'):archive.write(p,p.relative_to(build).as_posix())
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(build),'-d',str(build),*[str(p) for p in (root/'tests').glob('*.java')]],check=True)
for test in ['DecoderTest','StreamTest','VehicleDecoderTest']:subprocess.run(['java','-cp',str(build),test],check=True)
subprocess.run([sys.executable,'-m','unittest','-v','test_capture.py','test_drive.py'],cwd=root/'tools',check=True)
