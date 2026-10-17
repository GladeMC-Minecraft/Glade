# Glade

Custom Items, Furniture und Armor für Paper (Minecraft 26.1+).

## Module

| Modul   | Inhalt                                                        |
|---------|---------------------------------------------------------------|
| `api`   | Öffentliche API (Interfaces, Events), ohne Paper-Abhängigkeit |
| `core`  | Plattformunabhängige Logik (Items, Config, Pack-Generierung)  |
| `paper` | Paper/Folia-Plugin, bindet `api` und `core` ein               |

## Voraussetzungen

- Java 25
- Paper/Folia 26.1.x

## Bauen

```bash
./gradlew build
```

Das fertige Plugin liegt danach unter `paper/build/libs/Glade-<version>.jar`.
