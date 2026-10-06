package io.github.minh124199.viettemplate.tooling.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateMigrationReportMojoTest {

  @Test
  @DisplayName("Generates clean migration report for exact-compatible templates")
  void testExactCompatibleTemplateReport(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("welcome.vtl"),
        "#set($title = 'Welcome')\n<h1>$title</h1>\n<p>Hello $name</p>\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("welcome.vtl.contract"), "name=String\n", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/migration-report.txt");

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("Apache Velocity");
    assertThat(content).contains("Viet Template");
    assertThat(content).contains("READY");
    assertThat(content).contains("Total Templates: 1");
    assertThat(content).contains("Compatible Templates: 1");
  }

  @Test
  @DisplayName("Generates report with blocker findings for division by zero")
  void testDivisionByZeroBlockerFinding(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("divzero.vtl"), "#set($x = 10 / 0)\n", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/migration-report-div.txt");

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());
    mojo.setFailOnBlocker(false);

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("BLOCKED");
    assertThat(content).contains("MIG-ARITH-DIV-ZERO");
  }

  @Test
  @DisplayName("Generates report with findings for security class access")
  void testSecurityClassAccessFinding(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("security.vtl"), "$user.class\n$user.getClass()\n", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/migration-report-sec.txt");

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());
    mojo.setFailOnWarning(false);

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("MIG-SEC-CLASS-ACCESS");
  }

  @Test
  @DisplayName("Generates JSON formatted report and writes to output file")
  void testJsonFormatAndOutputFile(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("hello.vtl"), "Hello, $name!", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("hello.vtl.contract"), "name=String\n", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/migration-report.json");

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setFormat("json");
    mojo.setOutputFile(outFile.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("\"formatVersion\": 1");
    assertThat(content).contains("\"sourceEngine\": \"Apache Velocity\"");
    assertThat(content).contains("\"targetEngine\": \"Viet Template\"");
    assertThat(content).contains("\"totalTemplates\": 1");
    assertThat(content).contains("\"success\": true");
  }

  @Test
  @DisplayName("Filters analysis to single template via template parameter")
  void testSingleTemplateFilter(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("tmpl1.vtl"), "Template 1: $name", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("tmpl2.vtl"), "Template 2: $name", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/migration-report-filter.txt");

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());
    mojo.setTemplate("tmpl1.vtl");

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("tmpl1.vtl");
    assertThat(content).doesNotContain("tmpl2.vtl");
    assertThat(content).contains("Total Templates: 1");
  }

  @Test
  @DisplayName("Throws MojoFailureException when failOnBlocker is true and blockers exist")
  void testFailOnBlocker(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("divzero.vtl"), "#set($x = 10 / 0)\n", StandardCharsets.UTF_8);

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setFailOnBlocker(true);

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("blocker(s) with failOnBlocker enabled");
  }

  @Test
  @DisplayName("Throws MojoFailureException when failOnWarning is true and warnings exist")
  void testFailOnWarning(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("dyn.vtl"), "#set($x = 10 / $divisor)\n", StandardCharsets.UTF_8);

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setFailOnWarning(true);

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("warning(s) with failOnWarning enabled");
  }

  @Test
  @DisplayName("Handles non-existent source directory cleanly")
  void testNonExistentDirectory(@TempDir Path tempDir) {
    Path srcDir = tempDir.resolve("does-not-exist");

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Handles empty source directory cleanly")
  void testEmptyDirectory(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Skips execution cleanly when skip is true")
  void testSkipExecution(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("divzero.vtl"), "#set($x = 10 / 0)\n", StandardCharsets.UTF_8);

    VietTemplateMigrationReportMojo mojo = new VietTemplateMigrationReportMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setFailOnBlocker(true);
    mojo.setSkip(true);

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }
}
