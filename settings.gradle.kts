include(":postgresql-uuid-test")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {

            // Common Utils

            val lombok = "1.18.22"
            val commonsLang3 = "3.12.0"
            val uuid = "4.1.0"

            library("lombok", "org.projectlombok:lombok:${lombok}")
            library("uuid", "com.fasterxml.uuid:java-uuid-generator:${uuid}")

            // Databases

            val postgresql = "42.5.4"

            library("postgresql", "org.postgresql:postgresql:${postgresql}")
        }
    }
}
