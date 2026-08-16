package com.gallop.fastddd.agentaccess;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人工路径零依赖锚点（TM-24 骨架期形态）。
 *
 * <p>主制品（.contract/.spi）是 kit 缺席时也必须独立可用的类型层：本模块
 * main 源码不得引用 Spring 类型——kit 装配完全移除后，人工用例对主制品的
 * 消费不受影响。第三方依赖的零依赖约束由模块 POM（无 compile 依赖）+ Enforcer
 * 兜底，本测试以源码扫描钉死 Spring 检出，防止未来在 .contract/.spi 引入框架
 * 依赖破坏零依赖约束。注意：测试以 surefire 工作目录（模块 basedir）为基准，
 * 从仓库根直接运行时失败方向为「关」（不误绿）。</p>
 */
class MainArtifactPurityTest {

    @Test
    void mainSourcesStayFrameworkFree() throws IOException {
        Path mainJava = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(mainJava), "main 源码目录必须存在");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mainJava)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String source = Files.readString(p);
                    if (source.contains("springframework")) {
                        offenders.add(p.toString());
                    }
                } catch (IOException e) {
                    offenders.add(p + "（读取失败：" + e.getMessage() + "）");
                }
            });
        }
        assertTrue(offenders.isEmpty(),
                "主制品 main 源码出现框架引用（TM-24 零依赖违规）：" + offenders);
    }
}
