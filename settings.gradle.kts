pluginManagement {
    repositories {
        // Aliyun mirrors (fast in China)
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        // Fallback only if Aliyun misses something
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/jitpack")
        google()
        mavenCentral()
    }
}
rootProject.name = "AirPodsControl"
include(
    ":app",
    ":feature-popup",
    ":feature-home",
    ":feature-settings",
    ":core-bluetooth",
    ":core-aacp",
    ":core-model3d",
    ":core-service",
    ":core-ui",
    ":core-data"
)
