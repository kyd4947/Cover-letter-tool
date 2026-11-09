plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")   // 2026년 기준 — 나중에 mavenCentral에서 최신 확인 가능
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")    // pdf 텍스트 추출 - 2026-07 최신, 3.x Loader API
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("coverletter.Main")
}