# AI Butler - 개인용 앱이라 release도 기본은 shrink 비활성화(build.gradle.kts 참고).
# minifyEnabled를 켤 경우를 대비한 최소 규칙만 남겨둡니다.
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }
