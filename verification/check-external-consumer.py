#!/usr/bin/env python3
"""Compile independent consumers using generated Maven artifacts, never project substitution."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
subprocess.run([str(ROOT / "gradlew"), "--quiet", "allMetadataJar", "jvmJar",
    "generateMetadataFileForJvmPublication", "generatePomFileForJvmPublication",
    "generateMetadataFileForKotlinMultiplatformPublication", "generatePomFileForKotlinMultiplatformPublication"], cwd=ROOT, check=True)
with tempfile.TemporaryDirectory(prefix="foundationdb-consumer-") as temporary:
    temporary = Path(temporary)
    repository = temporary / "repository"
    version = None
    for publication in ["kotlinMultiplatform", "jvm"]:
        generated = ROOT / "build/publications" / publication
        pom = ET.parse(generated / "pom-default.xml").getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        group, module, version = (pom.findtext("m:" + key, namespaces=ns) for key in ["groupId", "artifactId", "version"])
        destination = repository / group.replace(".", "/") / module / version
        destination.mkdir(parents=True)
        shutil.copyfile(generated / "pom-default.xml", destination / f"{module}-{version}.pom")
        shutil.copyfile(generated / "module.json", destination / f"{module}-{version}.module")
        metadata = json.loads((generated / "module.json").read_text())
        for variant in metadata["variants"]:
            for artifact in variant.get("files", []):
                source = ROOT / "build/libs" / artifact["name"]
                assert hashlib.sha256(source.read_bytes()).hexdigest() == artifact["sha256"], source
                shutil.copyfile(source, destination / artifact["url"])
    for coordinate, pom_only in [("foundationdb-multiplatform", False), ("foundationdb-multiplatform-jvm", False), ("foundationdb-multiplatform-jvm", True)]:
        label = coordinate + (" (POM only)" if pom_only else "")
        consumer = temporary / (coordinate + ("-pom" if pom_only else ""))
        shutil.copytree(ROOT / "verification/consumer", consumer)
        subprocess.run([str(ROOT / "gradlew"), "--quiet", "-p", str(consumer),
            f"-PbindingRepository={repository}", f"-PbindingCoordinate={coordinate}",
            f"-PbindingVersion={version}", f"-PpomOnly={str(pom_only).lower()}", "compileKotlinJvm"], cwd=ROOT, check=True)
        print(f"External Maven consumer passed: {label}", flush=True)
