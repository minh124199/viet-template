#!/usr/bin/env python3
"""
Spring Security Multi-Version Binary Compatibility Verification Script

Verifies that the compiled viet-template-spring-security artifact maintains
full runtime binary compatibility across:
  - Spring Security 6.3.x minimum baseline (tested: 6.3.4)
  - Spring Security 6.5.x final 6.x generation (tested: 6.5.11)
  - Spring Security 7.0.x baseline (tested: 7.0.7)
  - Spring Security 7.1.x baseline (tested: 7.1.1)

Single artifact guarantee: viet-template-spring-security
"""

import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
COMPAT_DIR = ROOT_DIR / "scripts" / "compat"
VERIFIER_JAVA = COMPAT_DIR / "SecurityCompatibilityVerifier.java"

TARGET_VERSIONS = [
    "6.3.4",   # Minimum binary-compatibility baseline (Spring Boot 3.3)
    "6.5.11",  # Final 6.x generation
    "7.0.7",   # Spring Security 7.0.x baseline
    "7.1.1",   # Spring Security 7.1.x baseline
]


def resolve_classpath(version: str) -> str:
    """Builds the runtime dependency classpath for the given Spring Security version."""
    pom_content = f"""<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.minh124199.test</groupId>
  <artifactId>security-compat-test-{version}</artifactId>
  <version>1.0.0</version>
  <dependencies>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-core</artifactId>
      <version>{version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-web</artifactId>
      <version>{version}</version>
    </dependency>
    <dependency>
      <groupId>jakarta.servlet</groupId>
      <artifactId>jakarta.servlet-api</artifactId>
      <version>6.0.0</version>
    </dependency>
  </dependencies>
</project>"""

    with tempfile.TemporaryDirectory() as td:
        pom_path = Path(td) / "pom.xml"
        pom_path.write_text(pom_content)
        cp_file = Path(td) / "cp.txt"
        cmd = [
            str(ROOT_DIR / "mvnw"),
            "dependency:build-classpath",
            f"-Dmdep.outputFile={cp_file}",
            "-f",
            str(pom_path),
            "-B",
            "-q",
        ]
        res = subprocess.run(cmd, cwd=str(ROOT_DIR), capture_output=True, text=True)
        if res.returncode != 0:
            print(f"[ERROR] Failed to resolve classpath for Spring Security {version}:", file=sys.stderr)
            print(res.stderr, file=sys.stderr)
            sys.exit(1)

        return cp_file.read_text().strip()


def locate_module_classpath(module_name: str) -> str:
    """Collects existing class directories and JARs for a module to ensure robust classpath resolution."""
    entries = []
    # Check classes directories
    m_classes = ROOT_DIR / module_name / "target" / "classes"
    if m_classes.is_dir():
        entries.append(str(m_classes))
    g_classes = ROOT_DIR / module_name / "build" / "classes" / "java" / "main"
    if g_classes.is_dir():
        entries.append(str(g_classes))

    # Check packaged JARs
    m_target = ROOT_DIR / module_name / "target"
    if m_target.is_dir():
        for jar in m_target.glob("*.jar"):
            if not any(jar.name.endswith(suffix) for suffix in ("-sources.jar", "-javadoc.jar", "-tests.jar")):
                entries.append(str(jar))

    g_libs = ROOT_DIR / module_name / "build" / "libs"
    if g_libs.is_dir():
        for jar in g_libs.glob("*.jar"):
            if not any(jar.name.endswith(suffix) for suffix in ("-sources.jar", "-javadoc.jar", "-tests.jar")):
                entries.append(str(jar))

    if not entries:
        raise FileNotFoundError(f"Cannot find compiled classes or JAR for module {module_name}")

    return ":".join(entries)


def main() -> None:
    print("=================================================================")
    print(" Viet Template Spring Security Multi-Version Compatibility Test  ")
    print("=================================================================")

    if not VERIFIER_JAVA.exists():
        print(f"[ERROR] Verifier source not found: {VERIFIER_JAVA}", file=sys.stderr)
        sys.exit(1)

    # Locate required internal module classes
    try:
        api_cp = locate_module_classpath("viet-template-api")
        spring_cp = locate_module_classpath("viet-template-spring")
        security_cp = locate_module_classpath("viet-template-spring-security")
    except FileNotFoundError as e:
        print(f"[ERROR] {e}. Please build the project before running verification.", file=sys.stderr)
        sys.exit(1)

    viet_modules_cp = f"{api_cp}:{spring_cp}:{security_cp}"

    # Resolve classpaths for all target versions
    version_classpaths = {}
    for version in TARGET_VERSIONS:
        version_classpaths[version] = resolve_classpath(version)

    overall_success = True

    with tempfile.TemporaryDirectory() as work_dir:
        classes_out = Path(work_dir) / "classes"
        classes_out.mkdir()

        # Compile verifier against baseline (6.3.4)
        baseline_compile_cp = f"{version_classpaths['6.3.4']}:{viet_modules_cp}"
        javac_cmd = [
            "javac",
            "--release",
            "17",
            "-cp",
            baseline_compile_cp,
            "-d",
            str(classes_out),
            str(VERIFIER_JAVA),
        ]
        javac_res = subprocess.run(javac_cmd, capture_output=True, text=True)
        if javac_res.returncode != 0:
            print("[FAIL] Compilation of verifier against baseline failed:", file=sys.stderr)
            print(javac_res.stderr, file=sys.stderr)
            sys.exit(1)

        # Run verification across each target version to verify runtime binary compatibility
        for version in TARGET_VERSIONS:
            print(f"--> Verifying runtime binary compatibility against Spring Security {version}...")
            run_cp = f"{classes_out}:{version_classpaths[version]}:{viet_modules_cp}"
            java_cmd = [
                "java",
                "-cp",
                run_cp,
                "io.github.minh124199.viettemplate.spring.security.compat.SecurityCompatibilityVerifier",
                version,
            ]
            java_res = subprocess.run(java_cmd, capture_output=True, text=True)
            if java_res.returncode != 0:
                print(f"[FAIL] Execution against Spring Security {version} failed (exit {java_res.returncode}):", file=sys.stderr)
                print(java_res.stdout)
                print(java_res.stderr, file=sys.stderr)
                overall_success = False
            else:
                print(f"    {java_res.stdout.strip()}")

    print("=================================================================")
    if overall_success:
        print(" [PASS] All 4 Spring Security generations verified compatible!  ")
        print(" Single artifact 'viet-template-spring-security' verified.      ")
        print("=================================================================")
        sys.exit(0)
    else:
        print(" [FAIL] Spring Security compatibility verification failed.     ", file=sys.stderr)
        print("=================================================================")
        sys.exit(1)


if __name__ == "__main__":
    main()
