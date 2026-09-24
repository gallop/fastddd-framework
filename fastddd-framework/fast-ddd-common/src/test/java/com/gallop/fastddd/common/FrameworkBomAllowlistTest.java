package com.gallop.fastddd.common;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 跨平台 BOM 归属门禁，替代 CI 中对 Windows PowerShell 的依赖。 */
class FrameworkBomAllowlistTest {

    private static final Set<String> EXPECTED = Set.of(
            "fast-ddd-common",
            "fast-ddd-config",
            "fast-ddd-model",
            "fast-ddd-crud",
            "fast-ddd-web",
            "fast-ddd-application",
            "fast-ddd-cache",
            "fast-ddd-event",
            "fast-ddd-search",
            "fast-ddd-monitor",
            "fast-ddd-starter",
            "fast-ddd-analyzer-spring-boot-starter",
            "fast-ddd-security-starter",
            "fast-ddd-ai-spring-boot-starter",
            // 身份域（W2-1）
            "fast-ddd-identity-core",
            "fast-ddd-identity-reference-pg",
            "fast-ddd-identity-spring-boot-starter");

    @Test
    void bomContainsOnlyFrameworkCommunityArtifacts() throws Exception {
        Path bomPom = Paths.get("..", "..", "fastddd-bom", "pom.xml")
                .toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(bomPom)).as("Framework BOM POM: %s", bomPom).isTrue();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(bomPom.toFile());

        Set<String> actual = new LinkedHashSet<>();
        NodeList dependencies = document.getElementsByTagNameNS(
                "http://maven.apache.org/POM/4.0.0", "dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if ("io.github.gallop.fastddd".equals(childText(dependency, "groupId"))) {
                actual.add(childText(dependency, "artifactId"));
            }
        }

        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED);
    }

    private String childText(Element element, String localName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
}
