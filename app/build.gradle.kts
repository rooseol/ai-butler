// 루트 빌드 스크립트: 각 모듈에서 사용할 플러그인 버전만 선언합니다 (apply false).
// AGP 9.0부터 Kotlin 지원이 AGP에 내장되어(built-in Kotlin support) 별도의
// org.jetbrains.kotlin.android / kotlin.plugin.compose 플러그인이 더 이상 필요하지 않습니다.
// (https://kotl.in/gradle/agp-built-in-kotlin)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
