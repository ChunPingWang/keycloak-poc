package com.shopmall.membership.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 把第 3、4 章的分層規則固化成 CI 會擋下的測試。 */
@AnalyzeClasses(packages = "com.shopmall.membership")
class ArchitectureTest {

    @ArchTest
    static final ArchRule 領域層不依賴任何框架 =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                            "org.keycloak..");

    @ArchTest
    static final ArchRule 應用層不依賴基礎設施層 =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule Keycloak型別只能出現在identity套件 =
            noClasses().that().resideOutsideOfPackage("..infrastructure.identity..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.keycloak..");
}
