package com.gallop.fastddd.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Framework 依赖方向构建门禁（SPLIT-002 步骤 7）。
 *
 * <p>固化 ADR-005 依赖方向：Framework 层（fast-ddd-* 模块，包 com.gallop.fastddd.web / .common /
 * .cache / .event / ...）的源码不得 import 任何 Tooling/Studio 包。这是 SPLIT-002 修复后不变量
 * 的框架级守卫——违规最初发生在 fast-ddd-web（FastDocController/FastDocConfiguration 引用
 * com.gallop.fastddd.fastdoc.*），现已上移至 Tooling 层。
 *
 * <p><b>为何放在 fast-ddd-common 且用源码扫描：</b>framework 各模块的 &lt;parent&gt; 是根 pom
 * （非 fastddd-framework 聚合器），故聚合器级 maven-enforcer 无法被子模块继承。本测试从 common
 * 模块（surefire 工作目录 = fast-ddd-common basedir）遍历同级 fast-ddd-* 模块各自的主源码树，
 * 做框架级 Java import 检查——一次覆盖全部 framework 模块，作为构建门禁在 {@code verify}
 * 阶段失败即阻断合并。
 *
 * <p>POM 声明维度的禁令由「framework 可独立构建（acceptance 1）+ 依赖树无 fastddd-*」
 * （acceptance 3 命令级证据）覆盖；本测试覆盖 Java import 维度并防止源码级回归。
 *
 * <p>被禁包前缀（Tooling/Studio 根包，均带尾点避免误匹配）：
 * com.gallop.fastddd.fastdoc. / .codegen. / .doc. / .templates. / .maven.plugin. / .studio.
 *
 * @author gallop
 */
class FrameworkLayeringDirectionTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.gallop.fastddd.fastdoc.",
            "com.gallop.fastddd.codegen.",
            "com.gallop.fastddd.doc.",
            "com.gallop.fastddd.templates.",
            "com.gallop.fastddd.maven.plugin.",
            "com.gallop.fastddd.studio.");

    /** framework 聚合器目录（common 的上级）。 */
    private static final Path FRAMEWORK_DIR = Paths.get("..").toAbsolutePath().normalize();

    @Test
    void frameworkSource_shallNotImportToolingOrStudioPackages() throws IOException {
        assertThat(Files.isDirectory(FRAMEWORK_DIR))
                .as("framework 聚合器目录存在: %s", FRAMEWORK_DIR).isTrue();

        List<String> violations = new ArrayList<>();
        try (DirectoryStream<Path> moduleDirs = Files.newDirectoryStream(FRAMEWORK_DIR, "fast-ddd-*")) {
            for (Path moduleDir : moduleDirs) {
                Path mainJava = moduleDir.resolve("src/main/java");
                if (!Files.isDirectory(mainJava)) {
                    continue;
                }
                scanModule(mainJava, violations);
            }
        }

        assertThat(violations)
                .as("Framework（fast-ddd-*）源码不得 import Tooling/Studio 包（ADR-005 依赖方向，SPLIT-002）。"
                        + " 扫描根: " + FRAMEWORK_DIR)
                .isEmpty();
    }

    private void scanModule(Path mainJava, List<String> violations) throws IOException {
        Files.walkFileTree(mainJava, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    for (String line : Files.readAllLines(file)) {
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("import ")) {
                            continue;
                        }
                        for (String prefix : FORBIDDEN_IMPORT_PREFIXES) {
                            if (trimmed.contains(prefix)) {
                                violations.add(file + ": " + trimmed);
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new AssertionError("读取失败: " + file, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
