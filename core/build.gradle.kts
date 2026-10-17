// Plattformunabhängige Logik (Items, Furniture, Armor, Config-Parsing, Pack-Generierung).
// Keine Abhängigkeit auf Paper/Bukkit. Adventure wird zur Laufzeit von der Plattform gestellt.
dependencies {
    api(project(":api"))

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
}